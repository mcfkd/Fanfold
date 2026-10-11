package com.fanfold.core;

/**
 * Small zlib (RFC 1950/1951) compressor with a configurable window.
 *
 * The printer inflates BITMAP data in a tiny buffer, so the vendor app compresses with a
 * 1 KB window (zlib windowBits = 10). java.util.zip.Deflater can't limit the window, so this
 * does it directly: LZ77 matching restricted to the window, coded with the fixed Huffman
 * table, one final block, zlib header declaring the window, Adler-32 trailer.
 * Label bitmaps are long runs of identical bytes, which this handles very well.
 */
public final class ZDeflate {
    private ZDeflate() {}

    private static final int MIN_MATCH = 3, MAX_MATCH = 258;
    private static final int HASH_BITS = 14, HASH_SIZE = 1 << HASH_BITS;
    private static final int MAX_CHAIN = 48;

    private static final int[] LEN_BASE = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
            35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
    private static final int[] LEN_EXTRA = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
            3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
    private static final int[] DIST_BASE = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
            257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577};
    private static final int[] DIST_EXTRA = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
            7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};

    /** Bit writer, LSB-first as deflate requires. */
    private static final class Bits {
        byte[] buf;
        int len, acc, n;

        Bits(int cap) { buf = new byte[Math.max(64, cap)]; }

        void put(int value, int bits) {
            acc |= value << n;
            n += bits;
            while (n >= 8) { byte_(acc & 0xFF); acc >>>= 8; n -= 8; }
        }

        /** Huffman codes are defined MSB-first; emit them reversed. */
        void code(int code, int bits) {
            int r = 0;
            for (int i = 0; i < bits; i++) { r = (r << 1) | (code & 1); code >>>= 1; }
            put(r, bits);
        }

        void flush() { if (n > 0) { byte_(acc & 0xFF); acc = 0; n = 0; } }

        void byte_(int b) {
            if (len == buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
            buf[len++] = (byte) b;
        }
    }

    private static void literal(Bits out, int v) {
        if (v < 144) out.code(0x30 + v, 8);
        else out.code(0x190 + (v - 144), 9);
    }

    private static void lengthCode(Bits out, int sym) {      // 256..287
        if (sym < 280) out.code(sym - 256, 7);
        else out.code(0xC0 + (sym - 280), 8);
    }

    private static void match(Bits out, int length, int dist) {
        int li = 0;
        while (li < 28 && LEN_BASE[li + 1] <= length) li++;
        lengthCode(out, 257 + li);
        if (LEN_EXTRA[li] > 0) out.put(length - LEN_BASE[li], LEN_EXTRA[li]);
        int di = 0;
        while (di < 29 && DIST_BASE[di + 1] <= dist) di++;
        out.code(di, 5);
        if (DIST_EXTRA[di] > 0) out.put(dist - DIST_BASE[di], DIST_EXTRA[di]);
    }

    private static int hash(byte[] d, int i) {
        int h = ((d[i] & 0xFF) << 10) ^ ((d[i + 1] & 0xFF) << 5) ^ (d[i + 2] & 0xFF);
        return (h * 0x9E3779B1 >>> (32 - HASH_BITS));
    }

    /** zlib stream for data, never referencing further back than 2^windowBits bytes (8..15). */
    public static byte[] compress(byte[] data, int windowBits) {
        int window = 1 << windowBits;
        Bits out = new Bits(data.length / 8 + 64);
        // zlib header: CM=8 (deflate), CINFO=windowBits-8; FCHECK makes it a multiple of 31.
        int cmf = ((windowBits - 8) << 4) | 8;
        int flg = 31 - ((cmf << 8) % 31);
        if (flg == 31) flg = 0;
        out.byte_(cmf);
        out.byte_(flg);
        // One final block, fixed Huffman (BFINAL=1, BTYPE=01).
        out.put(1, 1);
        out.put(1, 2);

        int[] head = new int[HASH_SIZE];
        java.util.Arrays.fill(head, -1);
        int[] prev = new int[window];
        int n = data.length, i = 0;
        while (i < n) {
            int bestLen = 0, bestDist = 0;
            if (i + MIN_MATCH <= n) {
                int h = hash(data, i);
                int cand = head[h];
                int chain = MAX_CHAIN;
                int maxLen = Math.min(MAX_MATCH, n - i);
                while (cand >= 0 && i - cand <= window && chain-- > 0) {
                    if (data[cand + bestLen] == data[i + bestLen] && data[cand] == data[i]) {
                        int l = 0;
                        while (l < maxLen && data[cand + l] == data[i + l]) l++;
                        if (l > bestLen) {
                            bestLen = l;
                            bestDist = i - cand;
                            if (l == maxLen) break;
                        }
                    }
                    int p = prev[cand & (window - 1)];
                    if (p >= cand) break;   // slot was overwritten by a newer position
                    cand = p;
                }
            }
            if (bestLen >= MIN_MATCH) {
                match(out, bestLen, bestDist);
                int end = i + bestLen;
                for (; i < end; i++) insert(data, i, n, head, prev, window);
            } else {
                literal(out, data[i] & 0xFF);
                insert(data, i, n, head, prev, window);
                i++;
            }
        }
        lengthCode(out, 256);   // end of block
        out.flush();
        int adler = adler32(data);
        out.byte_(adler >>> 24);
        out.byte_(adler >>> 16);
        out.byte_(adler >>> 8);
        out.byte_(adler);
        return java.util.Arrays.copyOf(out.buf, out.len);
    }

    private static void insert(byte[] d, int i, int n, int[] head, int[] prev, int window) {
        if (i + MIN_MATCH > n) return;
        int h = hash(d, i);
        prev[i & (window - 1)] = head[h];
        head[h] = i;
    }

    static int adler32(byte[] d) {
        int a = 1, b = 0;
        for (byte x : d) {
            a = (a + (x & 0xFF)) % 65521;
            b = (b + a) % 65521;
        }
        return (b << 16) | a;
    }
}
