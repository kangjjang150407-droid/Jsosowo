package com.jsosowo.winemu;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Paths and the proot command line used to run the Alpine Linux userland that hosts QEMU. */
public final class Env {
    public static final String ALPINE_VERSION = "3.24";
    public static final String ALPINE_RELEASE = "3.24.4";
    public static final String MIRROR = "https://dl-cdn.alpinelinux.org/alpine";

    /** Packages installed inside Alpine. */
    public static final String PACKAGES =
            "qemu-system-x86_64 qemu-system-aarch64 qemu-img swtpm";

    public final File files;
    public final File rootfs;
    public final File vmDir;
    public final File tmp;
    public final File libDir;

    public Env(Context ctx) {
        files = ctx.getFilesDir();
        rootfs = new File(files, "alpine");
        vmDir = new File(files, "vm");
        tmp = new File(ctx.getCacheDir(), "proot-tmp");
        libDir = new File(ctx.getApplicationInfo().nativeLibraryDir);
    }

    /** Alpine architecture name matching this device. */
    public static String alpineArch() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.equals("arm64-v8a")) return "aarch64";
            if (abi.equals("x86_64")) return "x86_64";
        }
        return "aarch64";
    }

    public String rootfsUrl() {
        String a = alpineArch();
        return MIRROR + "/v" + ALPINE_VERSION + "/releases/" + a
                + "/alpine-minirootfs-" + ALPINE_RELEASE + "-" + a + ".tar.gz";
    }

    public boolean isInstalled() {
        return new File(rootfs, ".winemu-ready").exists();
    }

    public boolean hasKvm() {
        File kvm = new File("/dev/kvm");
        return kvm.exists() && kvm.canRead() && kvm.canWrite();
    }

    /** Build a ProcessBuilder that runs {@code script} with /bin/sh inside the Alpine rootfs. */
    public ProcessBuilder shell(String script) {
        vmDir.mkdirs();
        tmp.mkdirs();
        new File(rootfs, "tmp").mkdirs();

        List<String> cmd = new ArrayList<>();
        cmd.add(new File(libDir, "libproot.so").getAbsolutePath());
        cmd.add("--kill-on-exit");
        cmd.add("--link2symlink");
        cmd.add("-0");
        cmd.add("-r");
        cmd.add(rootfs.getAbsolutePath());
        cmd.add("-b");
        cmd.add("/dev");
        cmd.add("-b");
        cmd.add("/proc");
        cmd.add("-b");
        cmd.add("/sys");
        cmd.add("-b");
        cmd.add(new File(rootfs, "tmp").getAbsolutePath() + ":/dev/shm");
        cmd.add("-b");
        cmd.add(vmDir.getAbsolutePath() + ":/vm");
        cmd.add("-w");
        cmd.add("/root");
        cmd.add("/usr/bin/env");
        cmd.add("-i");
        cmd.add("HOME=/root");
        cmd.add("USER=root");
        cmd.add("TERM=xterm-256color");
        cmd.add("LANG=C.UTF-8");
        cmd.add("TMPDIR=/tmp");
        cmd.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        cmd.add("/bin/sh");
        cmd.add("-c");
        cmd.add(script);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
        env.put("PROOT_LOADER", new File(libDir, "libproot-loader.so").getAbsolutePath());
        env.put("PROOT_LOADER_32", new File(libDir, "libproot-loader32.so").getAbsolutePath());
        env.put("PROOT_TMP_DIR", tmp.getAbsolutePath());
        env.put("HOME", files.getAbsolutePath());
        return pb;
    }
}
