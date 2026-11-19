package com.jsosowo.winemu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_ISO = 10;

    private Env env;
    private VmConfig cfg;
    private TextView status, log, isoInfo;
    private EditText ram, cores, disk, cpu, extra;
    private CheckBox tpm, secureBoot;
    private RadioGroup archGroup;
    private Button btnSetup, btnStart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile String copyStatus = null;
    private String lastLog = "";

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        env = new Env(this);
        cfg = new VmConfig(this);

        status = findViewById(R.id.status);
        log = findViewById(R.id.log);
        log.setMovementMethod(new ScrollingMovementMethod());
        isoInfo = findViewById(R.id.isoInfo);
        ram = findViewById(R.id.ram);
        cores = findViewById(R.id.cores);
        disk = findViewById(R.id.disk);
        cpu = findViewById(R.id.cpu);
        extra = findViewById(R.id.extra);
        tpm = findViewById(R.id.tpm);
        secureBoot = findViewById(R.id.secureBoot);
        archGroup = findViewById(R.id.archGroup);
        btnSetup = findViewById(R.id.btnSetup);
        btnStart = findViewById(R.id.btnStart);

        archGroup.check(cfg.arm64 ? R.id.archArm : R.id.archX64);
        ram.setText(String.valueOf(cfg.ramMb));
        cores.setText(String.valueOf(cfg.cores));
        disk.setText(String.valueOf(cfg.diskGb));
        cpu.setText(cfg.cpuModel);
        extra.setText(cfg.extraArgs);
        tpm.setChecked(cfg.tpm);
        secureBoot.setChecked(cfg.secureBoot);

        btnSetup.setOnClickListener(v -> VmService.send(this, VmService.ACTION_SETUP));
        btnStart.setOnClickListener(v -> startVm());
        findViewById(R.id.btnScreen).setOnClickListener(v ->
                startActivity(new Intent(this, DisplayActivity.class)));
        findViewById(R.id.btnPower).setOnClickListener(v -> VmService.send(this, VmService.ACTION_POWERDOWN));
        findViewById(R.id.btnKill).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("VM을 강제로 끕니다. 저장하지 않은 데이터는 사라질 수 있어요.")
                .setPositiveButton("강제 종료", (d, w) -> VmService.send(this, VmService.ACTION_KILL))
                .setNegativeButton("취소", null).show());
        findViewById(R.id.btnIso).setOnClickListener(v -> pickIso());
        findViewById(R.id.btnIsoRemove).setOnClickListener(v -> {
            if (VmService.running) { toast("VM 실행 중에는 바꿀 수 없어요."); return; }
            new File(env.vmDir, VmConfig.ISO_NAME).delete();
            refresh();
        });
        findViewById(R.id.btnResetDisk).setOnClickListener(v -> resetDisk());

        ((TextView) findViewById(R.id.help)).setText(Html.fromHtml(getHelpHtml()));
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        readConfig();
    }

    private void readConfig() {
        cfg.arm64 = archGroup.getCheckedRadioButtonId() == R.id.archArm;
        cfg.ramMb = parse(ram, 4096, 512, 65536);
        cfg.cores = parse(cores, 2, 1, 64);
        cfg.diskGb = parse(disk, 64, 8, 2048);
        cfg.cpuModel = cpu.getText().toString().trim().isEmpty() ? "max" : cpu.getText().toString().trim();
        cfg.extraArgs = extra.getText().toString();
        cfg.tpm = tpm.isChecked();
        cfg.secureBoot = secureBoot.isChecked();
        cfg.save();
    }

    private static int parse(EditText e, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim())));
        } catch (Exception ex) {
            return def;
        }
    }

    private void startVm() {
        readConfig();
        if (!env.isInstalled()) { toast("먼저 1단계 환경 설치를 해주세요."); return; }
        if (copyStatus != null) { toast("ISO 복사가 끝날 때까지 기다려주세요."); return; }
        boolean hasIso = new File(env.vmDir, VmConfig.ISO_NAME).exists();
        boolean hasDisk = new File(env.vmDir, cfg.diskName()).exists();
        if (!hasIso && !hasDisk) {
            toast("설치용 Windows 11 ISO를 먼저 선택하세요.");
            return;
        }
        VmService.send(this, VmService.ACTION_START);
        handler.postDelayed(() -> {
            if (VmService.running) startActivity(new Intent(this, DisplayActivity.class));
        }, 6000);
    }

    private void resetDisk() {
        readConfig();
        if (VmService.running) { toast("VM을 먼저 종료하세요."); return; }
        new AlertDialog.Builder(this)
                .setMessage(cfg.diskName() + " 가상 디스크와 UEFI/TPM 상태를 삭제합니다. 설치된 Windows가 모두 사라져요.")
                .setPositiveButton("삭제", (d, w) -> {
                    new File(env.vmDir, cfg.diskName()).delete();
                    new File(env.vmDir, cfg.varsName()).delete();
                    VmService.deleteRecursive(new File(env.vmDir, "tpm"));
                    toast("삭제했습니다.");
                })
                .setNegativeButton("취소", null).show();
    }

    // -------------------------------------------------------------- ISO import

    private void pickIso() {
        if (VmService.running) { toast("VM 실행 중에는 바꿀 수 없어요."); return; }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_ISO);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ISO && res == RESULT_OK && data != null && data.getData() != null) {
            importIso(data.getData());
        }
    }

    private void importIso(Uri uri) {
        long size = -1;
        String name = "ISO";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                if (ni >= 0) name = c.getString(ni);
            }
        } catch (Exception ignored) {}
        env.vmDir.mkdirs();
        long free = new StatFs(env.vmDir.getPath()).getAvailableBytes();
        if (size > 0 && size > free - (512L << 20)) {
            toast("저장공간이 부족합니다. (필요 " + (size >> 20) + "MB, 여유 " + (free >> 20) + "MB)");
            return;
        }
        final long total = size;
        final String fname = name;
        copyStatus = "ISO 복사 준비 중…";
        new Thread(() -> {
            File tmp = new File(env.vmDir, VmConfig.ISO_NAME + ".part");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[1 << 20];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    copyStatus = "ISO 복사 중… " + (done >> 20) + "MB"
                            + (total > 0 ? " / " + (total >> 20) + "MB (" + (done * 100 / total) + "%)" : "");
                }
                out.close();
                File iso = new File(env.vmDir, VmConfig.ISO_NAME);
                iso.delete();
                if (!tmp.renameTo(iso)) throw new Exception("rename failed");
                getSharedPreferences("vm", MODE_PRIVATE).edit().putString("isoName", fname).apply();
                VmService.log("[winemu] ISO imported: " + fname);
                runOnUiThread(() -> toast("ISO 준비 완료: " + fname));
            } catch (Exception e) {
                tmp.delete();
                VmService.log("[winemu] ISO import failed: " + e);
                runOnUiThread(() -> toast("ISO 복사 실패: " + e.getMessage()));
            } finally {
                copyStatus = null;
            }
        }, "iso-copy").start();
    }

    // -------------------------------------------------------------- UI refresh

    private void refresh() {
        String s;
        if (copyStatus != null) s = copyStatus;
        else if (!VmService.status.isEmpty()) s = VmService.status;
        else if (!env.isInstalled()) s = "환경이 아직 설치되지 않았습니다. 1단계를 눌러주세요. (Wi-Fi 권장)";
        else s = "준비됨";
        if (env.hasKvm()) s += "\n✔ 이 기기는 KVM 하드웨어 가속을 지원합니다 (ARM64 VM).";
        status.setText(s);

        btnSetup.setEnabled(!VmService.installing && !VmService.running);
        btnSetup.setText(env.isInstalled() ? "환경 재설치 / 업데이트" : "리눅스 + QEMU 환경 설치 (최초 1회)");
        btnStart.setEnabled(!VmService.installing && !VmService.running);

        File iso = new File(env.vmDir, VmConfig.ISO_NAME);
        if (iso.exists()) {
            String n = getSharedPreferences("vm", MODE_PRIVATE).getString("isoName", "install.iso");
            isoInfo.setText("💿 ISO: " + n + " (" + (iso.length() >> 20) + "MB)");
        } else {
            isoInfo.setText("💿 ISO 없음 — 설치 후에는 빼두면 디스크로 부팅합니다.");
        }

        String l = VmService.logText();
        if (!l.equals(lastLog)) {
            lastLog = l;
            int start = Math.max(0, l.length() - 8000);
            log.setText(l.substring(start));
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private String getHelpHtml() {
        return "<b>사용 순서</b><br>"
                + "1) <b>환경 설치</b>: Alpine Linux와 QEMU(약 200MB)를 내려받습니다.<br>"
                + "2) Microsoft 공식 사이트에서 받은 <b>Windows 11 ISO</b>를 선택합니다. "
                + "x64용 ISO는 'x64', ARM64용 ISO는 'ARM64'를 고르세요. (ISO는 직접 준비해야 합니다)<br>"
                + "3) <b>VM 시작</b> → 화면이 뜨면 'Press any key to boot from CD' 문구에서 화면을 탭하세요.<br><br>"
                + "<b>화면 조작</b>: 탭 = 클릭, 두 손가락 탭 = 우클릭, 길게 누르고 끌기 = 드래그, "
                + "두 손가락 상하 = 스크롤. 상단 툴바에서 키보드, Ctrl+Alt+Del, Win 키를 쓸 수 있어요.<br><br>"
                + "<b>Windows 11 설치 팁</b><br>"
                + "• 인터넷/MS 계정 단계를 건너뛰려면 Shift+F10 → <tt>OOBE\\BYPASSNRO</tt> 입력.<br>"
                + "• TPM을 끈 경우: 설치 화면에서 Shift+F10 → regedit → HKLM\\SYSTEM\\Setup\\LabConfig 에 "
                + "BypassTPMCheck, BypassSecureBootCheck, BypassRAMCheck (DWORD=1) 추가.<br>"
                + "• 최소 4096MB 메모리, 64GB 디스크가 필요합니다. 기기 여유 공간을 확인하세요.<br><br>"
                + "<b>성능 안내</b><br>"
                + "휴대폰은 x86 PC가 아니므로 CPU 명령어를 소프트웨어로 번역(TCG)합니다. "
                + "Windows 11 x64는 설치에 수 시간이 걸리고 매우 느릴 수 있습니다. "
                + "ARM64 ISO가 훨씬 낫고, KVM 지원 기기라면 거의 실사용 가능한 속도가 나옵니다.<br><br>"
                + "<b>VM이 갑자기 꺼질 때 (Android 12 이상)</b><br>"
                + "'팬텀 프로세스' 제한 때문일 수 있습니다. 개발자 옵션의 "
                + "'하위 프로세스 제한 사용 중지'를 켜거나 PC에서 adb로:<br>"
                + "<tt>adb shell device_config put activity_manager max_phantom_processes 2147483647</tt><br>"
                + "또한 배터리 최적화에서 이 앱을 '제한 없음'으로 설정하세요.";
    }
}
