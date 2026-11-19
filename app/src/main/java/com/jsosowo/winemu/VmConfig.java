package com.jsosowo.winemu;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** User-editable VM settings and the QEMU command line built from them. */
public final class VmConfig {
    public static final int VNC_WS_PORT = 5700;   // websocket port for display :0
    public static final int QMP_PORT = 4444;

    public boolean arm64;        // false = Windows 11 x64 (x86_64), true = Windows 11 ARM64
    public int ramMb;
    public int cores;
    public int diskGb;
    public boolean tpm;
    public boolean secureBoot;
    public String cpuModel;
    public String extraArgs;

    private final SharedPreferences sp;

    /** Defaults without persistence (used by tests). */
    VmConfig() {
        sp = null;
        ramMb = 4096; cores = 2; diskGb = 64; tpm = true; secureBoot = true;
        cpuModel = "max"; extraArgs = "";
    }

    public VmConfig(Context ctx) {
        sp = ctx.getSharedPreferences("vm", Context.MODE_PRIVATE);
        arm64 = sp.getBoolean("arm64", false);
        ramMb = sp.getInt("ram", 4096);
        cores = sp.getInt("cores", Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        diskGb = sp.getInt("disk", 64);
        tpm = sp.getBoolean("tpm", true);
        secureBoot = sp.getBoolean("secureBoot", true);
        cpuModel = sp.getString("cpu", "max");
        extraArgs = sp.getString("extra", "");
    }

    public void save() {
        sp.edit()
                .putBoolean("arm64", arm64)
                .putInt("ram", ramMb)
                .putInt("cores", cores)
                .putInt("disk", diskGb)
                .putBoolean("tpm", tpm)
                .putBoolean("secureBoot", secureBoot)
                .putString("cpu", cpuModel)
                .putString("extra", extraArgs)
                .apply();
    }

    public String diskName() { return arm64 ? "win11-arm64.qcow2" : "win11-x64.qcow2"; }
    public String varsName() { return arm64 ? "efivars-arm64.fd" : "efivars-x64.fd"; }
    public static final String ISO_NAME = "install.iso";

    /** Shell script executed inside Alpine that prepares files and launches QEMU. */
    public String buildScript(File vmDir, boolean kvm) {
        boolean hasIso = new File(vmDir, ISO_NAME).exists();
        StringBuilder s = new StringBuilder();
        s.append("set -e\ncd /vm\n");
        s.append("echo '[winemu] preparing VM files'\n");

        // Missing emulator? (e.g. setup interrupted) -> install on demand
        String bin = arm64 ? "qemu-system-aarch64" : "qemu-system-x86_64";
        s.append("command -v ").append(bin).append(" >/dev/null || apk add --no-progress ")
                .append(bin).append(" qemu-img swtpm\n");

        // Virtual disk
        s.append("[ -f ").append(diskName()).append(" ] || qemu-img create -f qcow2 ")
                .append(diskName()).append(' ').append(diskGb).append("G\n");

        // UEFI variable store
        if (arm64) {
            s.append("CODE=/usr/share/qemu/edk2-aarch64-code.fd\n");
            s.append("[ -f ").append(varsName()).append(" ] || truncate -s $(stat -c %s $CODE) ")
                    .append(varsName()).append("\n");
        } else {
            s.append("CODE=/usr/share/qemu/").append(secureBoot ? "edk2-x86_64-secure-code.fd" : "edk2-x86_64-code.fd").append("\n");
            s.append("[ -f ").append(varsName()).append(" ] || cp /usr/share/qemu/edk2-i386-vars.fd ")
                    .append(varsName()).append("\n");
        }

        // Software TPM 2.0 (required by Windows 11)
        if (tpm) {
            s.append("mkdir -p /vm/tpm\nrm -f /vm/tpm/sock\n");
            s.append("swtpm socket --tpm2 --tpmstate dir=/vm/tpm --ctrl type=unixio,path=/vm/tpm/sock "
                    + "--flags startup-clear &\n");
            s.append("for i in 1 2 3 4 5 6 7 8 9 10; do [ -S /vm/tpm/sock ] && break; sleep 0.5; done\n");
        }

        StringBuilder a = new StringBuilder();
        a.append(bin);
        if (arm64) {
            a.append(" -M virt");
            if (kvm) a.append(" -accel kvm -cpu host");
            else a.append(" -accel tcg,thread=multi,tb-size=512 -cpu ")
                    .append(cpuModel.equals("max") ? "max,pauth-impdef=on" : cpuModel);
        } else {
            a.append(" -M q35,smm=").append(secureBoot ? "on" : "off").append(",hpet=off");
            a.append(" -accel tcg,thread=multi,tb-size=512");
            a.append(" -cpu ").append(cpuModel);
            if (secureBoot) a.append(" -global driver=cfi.pflash01,property=secure,value=on");
            a.append(" -global ICH9-LPC.disable_s3=1");
        }
        a.append(" -smp ").append(cores).append(" -m ").append(ramMb);
        a.append(" -drive if=pflash,format=raw,unit=0,readonly=on,file=$CODE");
        a.append(" -drive if=pflash,format=raw,unit=1,file=/vm/").append(varsName());

        if (tpm) {
            a.append(" -chardev socket,id=chrtpm,path=/vm/tpm/sock -tpmdev emulator,id=tpm0,chardev=chrtpm");
            a.append(arm64 ? " -device tpm-tis-device,tpmdev=tpm0" : " -device tpm-tis,tpmdev=tpm0");
        }

        a.append(" -device qemu-xhci,id=xhci -device usb-tablet -device usb-kbd");

        if (arm64) {
            a.append(" -device ramfb");
            a.append(" -drive if=none,id=disk0,format=qcow2,cache=writeback,file=/vm/").append(diskName());
            a.append(" -device nvme,drive=disk0,serial=WINEMU0001,bootindex=1");
            if (hasIso) {
                a.append(" -drive if=none,id=cd0,media=cdrom,readonly=on,file=/vm/").append(ISO_NAME);
                a.append(" -device usb-storage,drive=cd0,bootindex=0");
            }
            a.append(" -netdev user,id=n0 -device usb-net,netdev=n0");
        } else {
            a.append(" -vga std");
            a.append(" -device ahci,id=ahci");
            a.append(" -drive if=none,id=disk0,format=qcow2,cache=writeback,file=/vm/").append(diskName());
            a.append(" -device ide-hd,drive=disk0,bus=ahci.0,bootindex=1");
            if (hasIso) {
                a.append(" -drive if=none,id=cd0,media=cdrom,readonly=on,file=/vm/").append(ISO_NAME);
                a.append(" -device ide-cd,drive=cd0,bus=ahci.1,bootindex=0");
            }
            a.append(" -netdev user,id=n0 -device e1000e,netdev=n0");
            a.append(" -audiodev none,id=snd0");
        }

        a.append(" -rtc base=localtime");
        a.append(" -vnc 127.0.0.1:0,websocket=on");
        a.append(" -qmp tcp:127.0.0.1:").append(QMP_PORT).append(",server=on,wait=off");
        a.append(" -monitor none -serial none -parallel none");
        if (extraArgs != null && !extraArgs.trim().isEmpty()) a.append(' ').append(extraArgs.trim());

        s.append("echo '[winemu] starting: ").append(a.toString().replace("'", "")).append("'\n");
        s.append(a).append("\n");
        return s.toString();
    }
}
