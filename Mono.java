package com.fanfold.core;

/**
 * 1-bit label image in TSPL BITMAP layout: rows of (w+7)/8 bytes, MSB = leftmost dot,
 * bit 1 = white (no dot), bit 0 = black (dot).
 */
public final class Mono {
    public final int w, h, wb;
    public final byte[] bits;
    public String note = "";

    public Mono(int w, int h) {
        this.w = w; this.h = h; this.wb = (w + 7) / 8;
        this.bits = new byte[wb * h];
        java.util.Arrays.fill(bits, (byte) 0xFF);
    }

    public void setBlack(int x, int y) {
        bits[y * wb + (x >> 3)] &= (byte) ~(0x80 >> (x & 7));
    }

    public boolean isBlack(int x, int y) {
        return (bits[y * wb + (x >> 3)] & (0x80 >> (x & 7))) == 0;
    }

    /** Copy moved right by dx and down by dy dots (negative = left/up); edges fill white. */
    public Mono shifted(int dx, int dy) {
        if (dx == 0 && dy == 0) return this;
        Mono m = new Mono(w, h);
        m.note = note;
        for (int y = 0; y < h; y++) {
            int sy = y - dy;
            if (sy < 0 || sy >= h) continue;
            for (int x = 0; x < w; x++) {
                int sx = x - dx;
                if (sx >= 0 && sx < w && isBlack(sx, sy)) m.setBlack(x, y);
            }
        }
        return m;
    }

    public int blackCount() {
        int n = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (isBlack(x, y)) n++;
        return n;
    }

    /** Grayscale (0..255, w*h) to 1-bit. style: PrintSettings.STYLE_*; threshold used for sharp mode. */
    public static Mono fromGray(int[] gray, int w, int h, int style, int threshold) {
        Mono m = new Mono(w, h);
        if (style == PrintSettings.STYLE_PHOTO) {
            // Floyd–Steinberg, serpentine scan.
            float[] cur = new float[w + 2], next = new float[w + 2];
            for (int y = 0; y < h; y++) {
                boolean ltr = (y & 1) == 0;
                for (int i = 0; i < w; i++) {
                    int x = ltr ? i : (w - 1 - i);
                    float v = gray[y * w + x] + cur[x + 1];
                    float out = v < 128 ? 0 : 255;
                    if (out == 0) m.setBlack(x, y);
                    float e = v - out;
                    int dir = ltr ? 1 : -1;
                    cur[x + 1 + dir] += e * 7 / 16f;
                    next[x + 1 - dir] += e * 3 / 16f;
                    next[x + 1] += e * 5 / 16f;
                    next[x + 1 + dir] += e * 1 / 16f;
                }
                float[] t = cur; cur = next; next = t;
                java.util.Arrays.fill(next, 0f);
            }
        } else {
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) if (gray[row + x] < threshold) m.setBlack(x, y);
            }
        }
        return m;
    }

    /** ARGB pixels (black/white) for on-screen preview. */
    public int[] toArgb() {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) px[y * w + x] = isBlack(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        }
        return px;
    }

    /** Luma of an ARGB pixel composited over white. */
    public static int luma(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int y = (r * 299 + g * 587 + b * 114) / 1000;
        if (a < 255) y = (y * a + 255 * (255 - a)) / 255;
        return y;
    }

    public static int[] toGray(int[] argb) {
        int[] g = new int[argb.length];
        for (int i = 0; i < argb.length; i++) g[i] = luma(argb[i]);
        return g;
    }
}
