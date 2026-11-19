package com.jsosowo.winemu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Foreground service that installs the Linux/QEMU environment and runs the virtual machine,
 * so Android does not kill them while the user is looking at another screen.
 */
public class VmService extends Service {
    public static final String ACTION_SETUP = "setup";
    public static final String ACTION_START = "start";
    public static final String ACTION_POWERDOWN = "powerdown";
    public static final String ACTION_KILL = "kill";
    public static final String ACTION_DOWNLOAD_ISO = "downloadIso";
    public static final String ACTION_CANCEL_ISO = "cancelIso";
    public static final String EXTRA_URL = "url";

    private static final String CHANNEL = "vm";
    private static final int NOTIF_ID = 1;

    // ---- shared state, read by the UI ----
    public static volatile boolean installing = false;
    public static volatile boolean running = false;
    public static volatile String status = "";
    public static volatile boolean downloadingIso = false;
    private static volatile boolean cancelIso = false;
    private static final StringBuilder LOG = new StringBuilder();

    private static Process vmProcess;
    private PowerManager.WakeLock wakeLock;

    public static void log(String line) {
        synchronized (LOG) {
            LOG.append(line).append('\n');
            if (LOG.length() > 60000) LOG.delete(0, LOG.length() - 40000);
        }
    }

    public static String logText() {
        synchronized (LOG) { return LOG.toString(); }
    }

    public static void send(Context ctx, String action) {
        Intent i = new Intent(ctx, VmService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, notification("대기 중"));
        String action = intent == null ? null : intent.getAction();
        if (ACTION_SETUP.equals(action)) {
            if (!installing && !running) new Thread(this::runSetup, "setup").start();
        } else if (ACTION_START.equals(action)) {
            if (!installing && !running) new Thread(this::runVm, "vm").start();
        } else if (ACTION_POWERDOWN.equals(action)) {
            new Thread(() -> qmp("system_powerdown"), "qmp").start();
        } else if (ACTION_KILL.equals(action)) {
            new Thread(this::killVm, "kill").start();
        } else if (ACTION_DOWNLOAD_ISO.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            if (!downloadingIso && !running && url != null) {
                downloadingIso = true;
                new Thread(() -> runIsoDownload(url), "iso").start();
            }
        } else if (ACTION_CANCEL_ISO.equals(action)) {
            cancelIso = true;
        }
        if (!busy()) {
            stopSelfLater();
        }
        return START_NOT_STICKY;
    }

    private static boolean busy() { return installing || running || downloadingIso; }

    private void stopSelfLater() {
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            if (!busy()) {
                stopForeground(true);
                stopSelf();
            }
        }).start();
    }

    // ------------------------------------------------------------------ setup

    private void runSetup() {
        installing = true;
        acquireWakeLock();
        Env env = new Env(this);
        try {
            if (!new File(env.rootfs, "bin/busybox").exists()) {
                File tgz = new File(getCacheDir(), "rootfs.tar.gz");
                setStatus("Alpine Linux 다운로드 중…");
                download(env.rootfsUrl(), tgz);
                setStatus("Alpine Linux 압축 해제 중…");
                deleteRecursive(env.rootfs);
                TarExtractor.extract(tgz, env.rootfs);
                tgz.delete();
                log("[winemu] rootfs extracted to " + env.rootfs);
            }
            writeFile(new File(env.rootfs, "etc/resolv.conf"), "nameserver 8.8.8.8\nnameserver 1.1.1.1\n");
            writeFile(new File(env.rootfs, "etc/apk/repositories"),
                    Env.MIRROR + "/v" + Env.ALPINE_VERSION + "/main\n"
                            + Env.MIRROR + "/v" + Env.ALPINE_VERSION + "/community\n");

            setStatus("QEMU 설치 중… (수 분 소요, 약 200MB)");
            int rc = runLogged(env.shell(
                    "apk update && apk add --no-progress " + Env.PACKAGES
                            + " && qemu-system-x86_64 --version && touch /.winemu-ready"));
            if (rc != 0 || !env.isInstalled()) throw new Exception("apk 설치 실패 (코드 " + rc + ")");
            setStatus("설치 완료! 이제 'Windows 11 ISO 받기'를 누르세요.");
        } catch (Throwable t) {
            log("[winemu] ERROR: " + t);
            setStatus("설치 실패: " + t.getMessage());
        } finally {
            installing = false;
            releaseWakeLock();
            stopSelfLater();
        }
    }

    private void download(String url, File out) throws Exception {
        log("[winemu] GET " + url);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode() + " " + url);
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] b = new byte[65536];
            long done = 0, last = 0;
            int n;
            while ((n = in.read(b)) > 0) {
                os.write(b, 0, n);
                done += n;
                if (done - last > 512 * 1024) {
                    last = done;
                    status = "Alpine Linux 다운로드 중… " + (done >> 20) + "MB"
                            + (total > 0 ? " / " + (total >> 20) + "MB" : "");
                }
            }
        }
    }

    // ------------------------------------------------------------------ Windows ISO

    /** Downloads the ISO the user picked on Microsoft's page straight into the VM folder (resumable). */
    private void runIsoDownload(String url) {
        cancelIso = false;
        acquireWakeLock();
        Env env = new Env(this);
        env.vmDir.mkdirs();
        String name = IsoInfo.fileNameFromUrl(url);
        File part = new File(env.vmDir, VmConfig.ISO_NAME + ".part");
        try {
            setStatus("Windows 11 ISO 다운로드 시작: " + name);
            for (int attempt = 1; ; attempt++) {
                try {
                    if (downloadIsoOnce(url, part)) break;
                    throw new Exception("다운로드가 완료되지 않았습니다");
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    if (cancelIso || attempt >= 5) throw e;
                    log("[winemu] 다운로드 끊김, 이어받기 재시도 " + attempt + ": " + e.getMessage());
                    Thread.sleep(3000L * attempt);
                }
            }
            File iso = new File(env.vmDir, VmConfig.ISO_NAME);
            iso.delete();
            if (!part.renameTo(iso)) throw new Exception("파일 이름 변경 실패");
            IsoInfo.remember(this, name);
            setStatus("ISO 준비 완료: " + name + " — 이제 VM을 시작하세요.");
        } catch (Throwable t) {
            if (cancelIso) {
                part.delete();
                setStatus("ISO 다운로드를 취소했습니다.");
            } else {
                log("[winemu] ISO ERROR: " + t);
                setStatus("ISO 다운로드 실패: " + t.getMessage() + " (다시 누르면 이어받습니다)");
            }
        } finally {
            downloadingIso = false;
            releaseWakeLock();
            stopSelfLater();
        }
    }

    private boolean downloadIsoOnce(String url, File part) throws Exception {
        long have = part.exists() ? part.length() : 0;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        int code = c.getResponseCode();
        if (code == 416) return true; // already complete
        if (code != 200 && code != 206) {
            throw new Exception("HTTP " + code + (code == 403 ? " (링크가 만료됨 — Microsoft 페이지에서 다시 받아주세요)" : ""));
        }
        boolean append = code == 206;
        if (!append) have = 0;
        long total = c.getContentLengthLong();
        if (total > 0) total += have;
        long free = new android.os.StatFs(part.getParent()).getAvailableBytes();
        if (total > 0 && total - have > free - (256L << 20)) {
            throw new Exception("저장공간 부족 (필요 " + ((total - have) >> 20) + "MB, 여유 " + (free >> 20) + "MB)");
        }
        long start = System.currentTimeMillis(), startBytes = have, lastUi = 0;
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(part, append)) {
            byte[] b = new byte[1 << 18];
            int n;
            while ((n = in.read(b)) > 0) {
                if (cancelIso) throw new Exception("취소됨");
                os.write(b, 0, n);
                have += n;
                long now = System.currentTimeMillis();
                if (now - lastUi > 1000) {
                    lastUi = now;
                    double mbps = (have - startBytes) / 1048576.0 / Math.max(1, (now - start) / 1000.0);
                    String st = "ISO 다운로드 중… " + (have >> 20) + "MB"
                            + (total > 0 ? " / " + (total >> 20) + "MB (" + (have * 100 / total) + "%)" : "")
                            + String.format(java.util.Locale.US, " · %.1f MB/s", mbps);
                    status = st;
                    updateNotification(st);
                }
            }
        }
        return total <= 0 || have >= total;
    }

    // ------------------------------------------------------------------ VM

    private void runVm() {
        Env env = new Env(this);
        if (downloadingIso) {
            setStatus("ISO 다운로드가 끝난 뒤 시작하세요.");
            stopSelfLater();
            return;
        }
        if (!env.isInstalled()) {
            setStatus("먼저 '환경 설치'를 실행하세요.");
            stopSelfLater();
            return;
        }
        running = true;
        acquireWakeLock();
        VmConfig cfg = new VmConfig(this);
        boolean kvm = cfg.arm64 && env.hasKvm() && "aarch64".equals(Env.alpineArch());
        setStatus("VM 실행 중 (" + (cfg.arm64 ? "Windows 11 ARM64" : "Windows 11 x64")
                + (kvm ? ", KVM 가속" : ", TCG 에뮬레이션") + ")");
        updateNotification("Windows VM 실행 중");
        try {
            String isoName = getSharedPreferences("vm", MODE_PRIVATE).getString("isoName", "");
            boolean autoInstall = cfg.prepareUnattend(env.vmDir, isoName);
            if (cfg.autoLocal) log("[winemu] 로컬 계정 자동 설정: " + cfg.userName);
            if (autoInstall) {
                log("[winemu] 자동 설치: 새 디스크 → 파티션·에디션(" + (cfg.editionHome ? "Home" : "Pro") + ") 자동 선택");
                new Thread(VmService::pressKeyToBootCd, "boot-cd").start();
            }
            ProcessBuilder pb = env.shell(cfg.buildScript(env.vmDir, kvm));
            int rc = runLogged(pb, true);
            log("[winemu] QEMU exited with code " + rc);
            setStatus("VM 종료됨 (코드 " + rc + ")");
        } catch (Throwable t) {
            log("[winemu] ERROR: " + t);
            setStatus("VM 실행 실패: " + t.getMessage());
        } finally {
            running = false;
            vmProcess = null;
            releaseWakeLock();
            stopSelfLater();
        }
    }

    private void killVm() {
        qmp("quit");
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        Process p = vmProcess;
        if (p != null) p.destroy();
    }

    /** Sends a single QMP command to the running QEMU. */
    static boolean qmp(String command) {
        return qmpExec("{\"execute\":\"" + command + "\"}", command);
    }

    /**
     * Presses a key combination inside the guest via QMP send-key, holding it for holdMs.
     * More reliable than VNC for combos like Shift+F10 because the keys are really held down.
     */
    static boolean sendKeys(String[] qcodes, int holdMs) {
        StringBuilder keys = new StringBuilder();
        for (String k : qcodes) {
            if (!k.matches("[a-z0-9_]+")) return false;
            if (keys.length() > 0) keys.append(',');
            keys.append("{\"type\":\"qcode\",\"data\":\"").append(k).append("\"}");
        }
        return qmpExec("{\"execute\":\"send-key\",\"arguments\":{\"keys\":[" + keys
                + "],\"hold-time\":" + holdMs + "}}", "send-key " + String.join("+", qcodes));
    }

    private static boolean qmpExec(String json, String label) {
        return qmpCall(json, label, false) != null;
    }

    /** Runs one QMP command and returns its reply line (null on failure). */
    private static synchronized String qmpCall(String json, String label, boolean quiet) {
        String command = label;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", VmConfig.QMP_PORT), 2000);
            s.setSoTimeout(3000);
            OutputStream o = s.getOutputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            r.readLine(); // greeting
            o.write("{\"execute\":\"qmp_capabilities\"}\n".getBytes(StandardCharsets.UTF_8));
            r.readLine();
            o.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
            String line;
            do {
                line = r.readLine(); // skip asynchronous events
            } while (line != null && !line.contains("\"return\"") && !line.contains("\"error\""));
            if (!quiet) log("[winemu] QMP " + command + " -> " + line);
            return line;
        } catch (Exception e) {
            if (!quiet) log("[winemu] QMP " + command + " failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets past "Press any key to boot from CD or DVD" on a fresh install: presses Enter every
     * second until the guest has clearly started reading the installer (cd0 read > 40 MB).
     */
    private static void pressKeyToBootCd() {
        long deadline = System.currentTimeMillis() + 10 * 60_000L;
        boolean announced = false;
        while (running && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            String stats = qmpCall("{\"execute\":\"query-blockstats\"}", "blockstats", true);
            if (stats == null) continue; // QEMU not up yet
            long cdRead = cdReadBytes(stats);
            if (cdRead > (40L << 20)) {
                log("[winemu] 설치 프로그램 부팅 확인 — 자동 키 입력 종료");
                return;
            }
            if (!announced) { log("[winemu] CD 부팅 키 자동 입력 중…"); announced = true; }
            qmpCall("{\"execute\":\"send-key\",\"arguments\":{\"keys\":[{\"type\":\"qcode\",\"data\":\"ret\"}],\"hold-time\":50}}",
                    "send-key ret", true);
        }
    }

    /** rd_bytes of the cd0 drive from a query-blockstats reply. */
    static long cdReadBytes(String reply) {
        try {
            org.json.JSONArray arr = new org.json.JSONObject(reply).getJSONArray("return");
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject d = arr.getJSONObject(i);
                if ("cd0".equals(d.optString("device"))) {
                    return d.getJSONObject("stats").getLong("rd_bytes");
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private int runLogged(ProcessBuilder pb) throws Exception { return runLogged(pb, false); }

    private int runLogged(ProcessBuilder pb, boolean isVm) throws Exception {
        Process p = pb.start();
        if (isVm) vmProcess = p;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) log(line);
        }
        return p.waitFor();
    }

    // ------------------------------------------------------------------ helpers

    private void setStatus(String s) {
        status = s;
        log("[winemu] " + s);
        updateNotification(s);
    }

    private Notification notification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "가상 머신", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, notification(text));
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "winemu:vm");
        }
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld() && !busy()) wakeLock.release();
    }

    static void writeFile(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        f.delete(); // may be a dangling symlink in the rootfs
        try (OutputStream o = new FileOutputStream(f)) { o.write(content.getBytes(StandardCharsets.UTF_8)); }
    }

    static void deleteRecursive(File f) {
        if (f.isDirectory() && !isSymlink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    private static boolean isSymlink(File f) {
        try {
            return android.system.OsConstants.S_ISLNK(android.system.Os.lstat(f.getPath()).st_mode);
        } catch (Exception e) {
            return true;
        }
    }
}
