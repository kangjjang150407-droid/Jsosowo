package com.jsosowo.winemu;

import android.system.Os;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Minimal tar.gz extractor (ustar, GNU long names, pax paths, symlinks, hard links). */
public final class TarExtractor {
    private TarExtractor() {}

    public static void extract(File tarGz, File dest) throws Exception {
        dest.mkdirs();
        String destPath = dest.getCanonicalPath();
        try (InputStream in = new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(tarGz), 65536), 65536)) {
            byte[] header = new byte[512];
            String longName = null;
            String longLink = null;
            while (true) {
                if (!readFully(in, header)) break;
                if (isZero(header)) break;

                String name = str(header, 0, 100);
                String prefix = str(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                int mode = (int) octal(header, 100, 8);
                long size = octal(header, 124, 12);
                char type = (char) header[156];
                String link = str(header, 157, 100);

                if (type == 'L' || type == 'K' || type == 'x' || type == 'g') {
                    byte[] data = readData(in, size);
                    if (type == 'L') longName = cstr(data);
                    else if (type == 'K') longLink = cstr(data);
                    else if (type == 'x') {
                        String p = pax(data, "path");
                        if (p != null) longName = p;
                        String l = pax(data, "linkpath");
                        if (l != null) longLink = l;
                    }
                    continue;
                }
                if (longName != null) { name = longName; longName = null; }
                if (longLink != null) { link = longLink; longLink = null; }

                while (name.startsWith("./")) name = name.substring(2);
                if (name.isEmpty() || name.equals(".")) { skip(in, size); continue; }
                File out = new File(dest, name);
                if (!out.getCanonicalPath().startsWith(destPath) && type != '2') {
                    skip(in, size);
                    continue;
                }

                switch (type) {
                    case '5':
                        out.mkdirs();
                        chmod(out, mode);
                        break;
                    case '2':
                        out.getParentFile().mkdirs();
                        out.delete();
                        Os.symlink(link, out.getAbsolutePath());
                        break;
                    case '1': {
                        out.getParentFile().mkdirs();
                        File target = new File(dest, link.startsWith("./") ? link.substring(2) : link);
                        out.delete();
                        copy(target, out);
                        chmod(out, mode);
                        break;
                    }
                    case '0':
                    case '\0':
                    case '7': {
                        out.getParentFile().mkdirs();
                        out.delete();
                        try (OutputStream os = new FileOutputStream(out)) {
                            copyN(in, os, size);
                        }
                        skipPadding(in, size);
                        chmod(out, mode);
                        continue;
                    }
                    default:
                        break;
                }
                skip(in, size);
            }
        }
    }

    private static void chmod(File f, int mode) {
        try { Os.chmod(f.getAbsolutePath(), mode & 07777); } catch (Exception ignored) {}
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream i = new FileInputStream(from); OutputStream o = new FileOutputStream(to)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = i.read(b)) > 0) o.write(b, 0, n);
        }
    }

    private static void copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] b = new byte[65536];
        while (n > 0) {
            int r = in.read(b, 0, (int) Math.min(b.length, n));
            if (r < 0) throw new IOException("Unexpected EOF in tar");
            out.write(b, 0, r);
            n -= r;
        }
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        byte[] d = new byte[(int) size];
        if (!readFully(in, d)) throw new IOException("Unexpected EOF in tar");
        skipPadding(in, size);
        return d;
    }

    private static void skip(InputStream in, long size) throws IOException {
        long total = size + pad(size);
        while (total > 0) {
            long s = in.skip(total);
            if (s <= 0) {
                if (in.read() < 0) throw new IOException("Unexpected EOF in tar");
                s = 1;
            }
            total -= s;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long p = pad(size);
        while (p > 0) {
            long s = in.skip(p);
            if (s <= 0) {
                if (in.read() < 0) return;
                s = 1;
            }
            p -= s;
        }
    }

    private static long pad(long size) {
        long r = size % 512;
        return r == 0 ? 0 : 512 - r;
    }

    private static boolean readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int r = in.read(b, off, b.length - off);
            if (r < 0) return off != 0 && off == b.length;
            off += r;
        }
        return true;
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String str(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static String cstr(byte[] b) {
        int end = 0;
        while (end < b.length && b[end] != 0) end++;
        return new String(b, 0, end, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            int c = b[i];
            if (c == 0 || c == ' ') {
                if (v != 0) break;
                continue;
            }
            if (c < '0' || c > '7') break;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    private static String pax(byte[] data, String key) {
        String s = new String(data, StandardCharsets.UTF_8);
        for (String line : s.split("\n")) {
            int sp = line.indexOf(' ');
            if (sp < 0) continue;
            String kv = line.substring(sp + 1);
            if (kv.startsWith(key + "=")) return kv.substring(key.length() + 1);
        }
        return null;
    }
}
