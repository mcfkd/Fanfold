package com.fanfold.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;

/** Builds TSPL print jobs for the JADENS JD268BT / Beeprt BY-426BT family. */
public final class Tspl {
    private Tspl() {}

    private static final Charset ASCII = Charset.forName("US-ASCII");

    /** Immediate status query: printer answers with one status byte. */
    public static final byte[] STATUS_QUERY = {0x1B, '!', '?'};

    public static byte[] job(PrintSettings s, LabelSize size, List<Mono> pages, int copies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pages.size() * (size.widthBytes() * size.heightDots + 200) + 200);
        write(out, "\r\n");   // flush any half-received line left from an earlier session
        line(out, "SIZE " + size.sizeCmd);
        String gap = s.gapCmd();
        if (gap != null) line(out, gap);
        line(out, "DIRECTION " + (s.flip ? 0 : 1));
        line(out, "REFERENCE 0,0");
        line(out, "DENSITY " + Math.max(0, Math.min(15, s.density)));
        if (s.speed() > 0) line(out, "SPEED " + s.speed());
        copies = Math.max(1, Math.min(99, copies));
        for (Mono page : pages) {
            // Nudges are stored in 203-dpi dots (8 per mm); scale them for other resolutions.
            Mono m = page.shifted(s.shiftX * size.dpi / LabelSize.DPI, s.shiftY * size.dpi / LabelSize.DPI);
            line(out, "CLS");
            int mode = s.transferMode();
            if (mode == PrintSettings.TRANSFER_COMPRESSED) {
                writeCompressed(out, m, s.invert);
            } else if (mode == PrintSettings.TRANSFER_INK_ONLY) {
                writeInkOnly(out, m, s.invert);
            } else {
                write(out, "BITMAP 0,0," + m.wb + "," + m.h + ",0,");
                byte[] data = m.bits;
                if (s.invert) { data = new byte[m.bits.length]; for (int i = 0; i < data.length; i++) data[i] = (byte) ~m.bits[i]; }
                out.write(data, 0, data.length);
                write(out, "\r\n");
            }
            line(out, "PRINT " + copies);
        }
        return out.toByteArray();
    }

    /** Rows per band when splitting the image; small enough to skip white space, big enough to keep overhead low. */
    public static final int BAND_ROWS = 16;

    /**
     * Sends only the parts of the label that have ink. CLS already blanks the whole label, so
     * white areas don't need to be transmitted at all — on a typical shipping label that's half
     * the data or more, which matters a lot over a slow Bluetooth link.
     * The image is cut into horizontal bands; within each band, runs of inked byte-columns become
     * separate BITMAP commands (runs closer together than it's worth splitting are merged).
     */
    static void writeInkOnly(ByteArrayOutputStream out, Mono m, boolean invert) {
        final int wb = m.wb;
        final byte WHITE = (byte) 0xFF;
        for (int y0 = 0; y0 < m.h; y0 += BAND_ROWS) {
            int y1 = Math.min(m.h, y0 + BAND_ROWS);
            boolean[] ink = new boolean[wb];
            boolean any = false;
            for (int y = y0; y < y1; y++) {
                int row = y * wb;
                for (int bx = 0; bx < wb; bx++) {
                    if (m.bits[row + bx] != WHITE) { ink[bx] = true; any = true; }
                }
            }
            if (!any) continue;
            int rows = y1 - y0;
            // Merge runs separated by fewer white columns than a command's overhead is worth.
            int minGap = Math.max(2, (30 + rows - 1) / rows);
            int bx = 0;
            while (bx < wb) {
                while (bx < wb && !ink[bx]) bx++;
                if (bx >= wb) break;
                int a = bx, b = bx;
                int gap = 0;
                while (bx < wb) {
                    if (ink[bx]) { b = bx + 1; gap = 0; }
                    else if (++gap >= minGap) break;
                    bx++;
                }
                // Tighten the rows for this segment.
                int ya = -1, yb = -1;
                for (int y = y0; y < y1; y++) {
                    int row = y * wb;
                    for (int c = a; c < b; c++) {
                        if (m.bits[row + c] != WHITE) { if (ya < 0) ya = y; yb = y + 1; break; }
                    }
                }
                int w = b - a, h = yb - ya;
                write(out, "BITMAP " + (a * 8) + "," + ya + "," + w + "," + h + ",0,");
                byte[] data = new byte[w * h];
                for (int y = 0; y < h; y++) System.arraycopy(m.bits, (ya + y) * wb + a, data, y * w, w);
                if (invert) for (int i = 0; i < data.length; i++) data[i] = (byte) ~data[i];
                out.write(data, 0, data.length);
                write(out, "\r\n");
            }
        }
    }

    /** zlib window the printer's decompressor works with (matches the vendor SDK: windowBits 10). */
    public static final int COMPRESS_WINDOW_BITS = 10;

    /**
     * Whole label as one zlib-compressed bitmap: "BITMAP x,y,widthBytes,height,3,length,<data>".
     * This is how the vendor's own app talks to these printers. Note the polarity is the opposite
     * of plain BITMAP: here a 1 bit means a black dot.
     */
    static void writeCompressed(ByteArrayOutputStream out, Mono m, boolean invert) {
        byte[] raw = new byte[m.bits.length];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) ~m.bits[i];   // our 1 = white -> printer's 1 = black
        if (invert) for (int i = 0; i < raw.length; i++) raw[i] = (byte) ~raw[i];
        byte[] z = ZDeflate.compress(raw, COMPRESS_WINDOW_BITS);
        write(out, "BITMAP 0,0," + m.wb + "," + m.h + ",3," + z.length + ",");
        out.write(z, 0, z.length);
        write(out, "\r\n");
    }

    /** Size of the old single-BITMAP encoding, for diagnostics. */
    public static int fullBitmapBytes(LabelSize size) { return size.widthBytes() * size.heightDots; }

    private static void line(ByteArrayOutputStream out, String cmd) { write(out, cmd + "\r\n"); }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(ASCII);
        out.write(b, 0, b.length);
    }

    /** True when a status reply looks like a real TSPL status byte. */
    public static boolean plausibleStatus(int v) {
        if (v < 0 || v > 0xFF) return false;
        if ((v & 0x40) != 0) return false;            // undefined bit: probably not a status byte
        if ((v & 0x30) == 0x30) return false;         // "paused" and "printing" together: implausible
        return true;
    }

    /** Problems that should stop a print job (head/cover open, jam, out of labels). */
    public static String blockingProblem(int v) {
        if (!plausibleStatus(v)) return null;
        StringBuilder sb = new StringBuilder();
        if ((v & 0x01) != 0) sb.append("cover is open");
        if ((v & 0x02) != 0) sb.append(sb.length() > 0 ? ", " : "").append("labels are jammed");
        if ((v & 0x04) != 0) sb.append(sb.length() > 0 ? ", " : "").append("out of labels");
        return sb.length() == 0 ? null : sb.toString();
    }

    public static String describeStatus(int v) {
        if (!plausibleStatus(v)) return "Printer replied (0x" + Integer.toHexString(v) + ")";
        if (v == 0) return "Ready";
        String p = blockingProblem(v);
        StringBuilder sb = new StringBuilder(p == null ? "" : capitalize(p));
        if ((v & 0x10) != 0) sb.append(sb.length() > 0 ? ", " : "").append("Paused");
        if ((v & 0x20) != 0) sb.append(sb.length() > 0 ? ", " : "").append("Printing");
        if ((v & 0x80) != 0) sb.append(sb.length() > 0 ? ", " : "").append("Printer error");
        if (sb.length() == 0) sb.append("Status 0x").append(Integer.toHexString(v));
        return sb.toString();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
