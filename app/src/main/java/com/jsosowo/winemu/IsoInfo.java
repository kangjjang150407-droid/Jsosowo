package com.jsosowo.winemu;

import android.content.Context;
import android.net.Uri;

import java.util.Locale;

/** Remembers which ISO is in the VM folder and picks the matching VM architecture. */
final class IsoInfo {
    private IsoInfo() {}

    static final String PAGE_X64 = "https://www.microsoft.com/%s/software-download/windows11";
    static final String PAGE_ARM64 = "https://www.microsoft.com/%s/software-download/windows11arm64";

    static String pageUrl(boolean arm64) {
        String loc = Locale.getDefault().toLanguageTag().toLowerCase(Locale.ROOT);
        if (!loc.contains("-")) loc = "ko-kr";
        return String.format(arm64 ? PAGE_ARM64 : PAGE_X64, loc);
    }

    static String fileNameFromUrl(String url) {
        String n = Uri.parse(url).getLastPathSegment();
        return n == null || n.isEmpty() ? "Windows11.iso" : n;
    }

    /** True / false if the file name tells the architecture, null if unknown. */
    static Boolean isArm64(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("arm64") || n.contains("aarch64")) return true;
        if (n.contains("x64") || n.contains("amd64") || n.contains("x86_64")) return false;
        return null;
    }

    static void remember(Context ctx, String name) {
        ctx.getSharedPreferences("vm", Context.MODE_PRIVATE).edit().putString("isoName", name).apply();
        Boolean arm = isArm64(name);
        if (arm != null) {
            VmConfig cfg = new VmConfig(ctx);
            cfg.arm64 = arm;
            cfg.save();
        }
    }
}
