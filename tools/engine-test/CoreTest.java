import com.fanfold.core.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * JVM test: emulates the app pipeline using pdftoppm renders instead of Android's PdfRenderer.
 * Usage: java CoreTest <workdir> <pdf> [pageIndex]
 */
public class CoreTest {
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        affineTests();
        if (args.length >= 2) pipeline(new File(args[0]), args[1], args.length > 2 ? Integer.parseInt(args[2]) : 1,
                args.length > 3 ? args[3] : "LP_4X6", args.length > 4 ? Integer.parseInt(args[4]) : PrintSettings.LAYOUT_AUTO);
        if (System.getenv("PAYLOAD_DIR") != null) {
            for (int i = 0; i < payloads.size(); i++)
                Files.write(Paths.get(System.getenv("PAYLOAD_DIR"), "p" + i + ".z"), payloads.get(i));
        }
        if (failures > 0) { System.out.println("FAILURES: " + failures); System.exit(1); }
    }

    static void check(boolean ok, String msg) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + msg);
        if (!ok) failures++;
    }

    static void affineTests() {
        System.out.println("affine/fit tests");
        // 4x6 page, no rotation -> exact 203/72 scale, origin at 0
        Affine a = Layout.fit(0, 0, 288, 432, 0, 812, 1218);
        double[] p = a.map(288, 432);
        check(Math.abs(p[0] - 812) < 0.01 && Math.abs(p[1] - 1218) < 0.01, "4x6 1:1 maps corner to 812,1218 " + Arrays.toString(p));
        // landscape 6x4 rotated clockwise: top-left of source goes to top-right of label
        Affine r = Layout.fit(0, 0, 432, 288, 1, 812, 1218);
        double[] tl = r.map(0, 0), tr = r.map(432, 0), bl = r.map(0, 288);
        check(Math.abs(tl[0] - 812) < 0.6 && Math.abs(tl[1]) < 0.6, "rot90: src TL -> label TR " + Arrays.toString(tl));
        check(Math.abs(tr[0] - 812) < 0.6 && Math.abs(tr[1] - 1218) < 0.6, "rot90: src TR -> label BR " + Arrays.toString(tr));
        check(Math.abs(bl[0]) < 0.6 && Math.abs(bl[1]) < 0.6, "rot90: src BL -> label TL " + Arrays.toString(bl));
        // offset region stays inside the label for every rotation
        for (int q = 0; q < 4; q++) {
            Affine m = Layout.fit(100, 50, 300, 200, q, 812, 1218);
            double minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
            double[][] cs = {m.map(100, 50), m.map(400, 50), m.map(100, 250), m.map(400, 250)};
            for (double[] c : cs) { minx = Math.min(minx, c[0]); miny = Math.min(miny, c[1]); maxx = Math.max(maxx, c[0]); maxy = Math.max(maxy, c[1]); }
            check(minx >= -0.6 && miny >= -0.6 && maxx <= 812.6 && maxy <= 1218.6, "q=" + q + " region inside label [" + (int) minx + "," + (int) miny + "," + (int) maxx + "," + (int) maxy + "]");
        }
        Affine inv = r.inverse();
        double[] back = inv.map(r.map(123, 45)[0], r.map(123, 45)[1]);
        check(Math.abs(back[0] - 123) < 1e-6 && Math.abs(back[1] - 45) < 1e-6, "inverse round-trip");
        // Mono packing + TSPL polarity
        Mono m = new Mono(10, 2);
        m.setBlack(0, 0); m.setBlack(9, 1);
        check(m.wb == 2 && (m.bits[0] & 0xFF) == 0x7F && (m.bits[3] & 0xFF) == 0xBF, "bit packing 0=black MSB-first");
        // Encoders: random sparse label, compact and full, must reproduce exactly.
        try {
            LabelSize sz = LabelSize.byId("LP_4X6");
            Mono busy = new Mono(sz.widthDots, sz.heightDots);
            Random rnd = new Random(7);
            for (int i = 0; i < 3000; i++) {
                int x = rnd.nextInt(sz.widthDots - 40), y = rnd.nextInt(sz.heightDots - 10);
                for (int k = 0; k < 1 + rnd.nextInt(40); k++) busy.setBlack(x + k, y + rnd.nextInt(3));
            }
            for (int x = 0; x < sz.widthDots; x++) { busy.setBlack(x, 0); busy.setBlack(x, sz.heightDots - 1); }
            for (int y = 0; y < sz.heightDots; y++) { busy.setBlack(0, y); busy.setBlack(sz.widthDots - 1, y); }
            String[] names = {"compressed", "ink-only", "full"};
            for (int t = 0; t < 3; t++) {
                for (boolean flipBits : new boolean[]{false, true}) {
                    PrintSettings ps = new PrintSettings();
                    ps.transfer = t;
                    ps.invert = flipBits;
                    byte[] j = Tspl.job(ps, sz, Collections.singletonList(busy), 2);
                    byte[] out = simulate(j, sz.widthBytes(), sz.heightDots, flipBits);
                    check(out != null && Arrays.equals(out, busy.bits), names[t] + (flipBits ? " +invert" : "") + " encoding reproduces image (" + j.length + " bytes)");
                }
            }
            check(sz.widthBytes() * Tspl.BAND_ROWS <= 32768, "largest BITMAP chunk fits the flush pad");
            // 300 dpi sizes
            LabelSize s300 = LabelSize.byId("LP_4X6").at(300);
            check(s300.widthDots == 1200 && s300.heightDots == 1800 && s300.dpi == 300, "4x6 at 300 dpi = 1200x1800");
            LabelSize m300 = LabelSize.byId("LP_100X150").at(300);
            check(m300.widthDots == 1200 && m300.heightDots == 1800, "100x150mm at 300 dpi = 1200x1800");
            check(LabelSize.byId("LP_100X150").widthDots == 800, "100x150mm at 203 dpi = 800 wide");
            PrintSettings p3 = new PrintSettings();
            p3.dpiSetting = 300;
            check(p3.labelSize().widthDots == 1200, "settings dpi flows into label size");
            Mono big = new Mono(1200, 1800);
            for (int y = 100; y < 1700; y += 7) for (int x = 50; x < 1150; x += 3) big.setBlack(x, y);
            p3.transfer = PrintSettings.TRANSFER_COMPRESSED;
            byte[] j3 = Tspl.job(p3, p3.labelSize(), Collections.singletonList(big), 1);
            byte[] o3 = simulate(j3, 150, 1800);
            check(o3 != null && Arrays.equals(o3, big.bits), "300 dpi compressed job reproduces image");
        } catch (Exception e) { check(false, "encoder test threw " + e); }
        // Printer profiles
        String[][] cases = {
                {"JD268BT-4F2A", "JADENS / Beeprt", "0"}, {"Beeprt_BY-426BT_1234", "JADENS / Beeprt", "0"},
                {"JD-468BT", "JADENS / Beeprt", "0"}, {"BY426BT", "JADENS / Beeprt", "0"},
                {"XP-420B", "Xprinter", "1"}, {"MUNBYN RW402B", "MUNBYN", "1"}, {"RW402B-11", "MUNBYN", "1"},
                {"Printer_001", "Unrecognized printer", "1"}, {"Y43BT", "FlashLabel / Y4x", "1"},
                {"PL70e-BT", "Polono / Nelko", "1"}, {"iDPRT SP410BT", "iDPRT", "1"}, {"JBL Flip 5", "Unrecognized printer", "1"},
        };
        for (String[] cs : cases) {
            PrinterProfile pp = PrinterProfile.match(cs[0], null);
            check(pp.family.equals(cs[1]) && pp.transfer == Integer.parseInt(cs[2]), "profile '" + cs[0] + "' -> " + pp.family + " / transfer " + pp.transfer);
        }
        check(PrinterProfile.match("Printer_001", "TSC TTP-244 Pro").family.equals("TSC"), "model reply improves the match");
    }

    // --- pipeline emulation -------------------------------------------------
    static int[] readPgm(File f, int[] wh) throws IOException {
        byte[] data = Files.readAllBytes(f.toPath());
        int pos = 0; String[] tok = new String[4]; int t = 0;
        while (t < 4) {
            while (Character.isWhitespace(data[pos])) pos++;
            if (data[pos] == '#') { while (data[pos] != '\n') pos++; continue; }
            StringBuilder sb = new StringBuilder();
            while (!Character.isWhitespace(data[pos])) sb.append((char) data[pos++]);
            tok[t++] = sb.toString();
        }
        pos++;
        int w = Integer.parseInt(tok[1]), h = Integer.parseInt(tok[2]);
        int[] g = new int[w * h];
        for (int i = 0; i < g.length; i++) g[i] = data[pos + i] & 0xFF;
        wh[0] = w; wh[1] = h;
        return g;
    }

    static int[] render(File dir, String pdf, int page, double dpi, int[] wh) throws Exception {
        File base = new File(dir, "r" + (int) dpi);
        Process pr = new ProcessBuilder("pdftoppm", "-gray", "-r", String.valueOf(dpi), "-f", "" + page, "-l", "" + page,
                "-singlefile", pdf, base.getPath()).inheritIO().start();
        pr.waitFor();
        return readPgm(new File(base.getPath() + ".pgm"), wh);
    }

    static double[] pageSizePts(String pdf, int page) throws Exception {
        Process pr = new ProcessBuilder("pdfinfo", "-f", "" + page, "-l", "" + page, pdf).start();
        BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String l;
        while ((l = br.readLine()) != null) {
            if (l.startsWith("Page") && l.contains("size:")) {
                String[] p = l.substring(l.indexOf("size:") + 5).trim().split("\\s+");
                return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[2])};
            }
        }
        throw new IOException("no page size");
    }

    static void pipeline(File dir, String pdf, int page, String sizeId, int layout) throws Exception {
        System.out.println("pipeline " + pdf + " page " + page + " size " + sizeId + " layout " + layout);
        dir.mkdirs();
        PrintSettings s = new PrintSettings();
        s.labelSizeId = sizeId;
        s.layout = layout;
        LabelSize size = s.labelSize();
        double[] ps = pageSizePts(pdf, page);
        // low-res analysis render (long side ~1000px), same as the app
        double k = 1000.0 / Math.max(ps[0], ps[1]);
        int[] wh = new int[2];
        int[] low = render(dir, pdf, page, k * 72, wh);
        Layout.Plan plan = Layout.plan(low, wh[0], wh[1], wh[0] / ps[0], ps[0], ps[1], true, s, size);
        if (plan.blank) { System.out.println("  page is blank"); return; }
        System.out.printf("  plan: region [%.1f %.1f %.1f %.1f] q=%d note=%s%n", plan.l, plan.t, plan.r, plan.b, plan.quarterTurns, plan.note);
        // "final" render: high-res, then area-sample through the inverse transform
        double hiDpi = 600;
        int[] hwh = new int[2];
        int[] hi = render(dir, pdf, page, hiDpi, hwh);
        double hs = hwh[0] / ps[0]; // px per point
        Affine inv = plan.transform.inverse();
        int W = size.widthDots, H = size.heightDots;
        int[] gray = new int[W * H];
        int ss = 3;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int sum = 0;
            for (int j = 0; j < ss; j++) for (int i = 0; i < ss; i++) {
                double[] src = inv.map(x + (i + 0.5) / ss, y + (j + 0.5) / ss);
                int sx = (int) (src[0] * hs), sy = (int) (src[1] * hs);
                sum += (sx >= 0 && sy >= 0 && sx < hwh[0] && sy < hwh[1]) ? hi[sy * hwh[0] + sx] : 255;
            }
            gray[y * W + x] = sum / (ss * ss);
        }
        Mono mono = Mono.fromGray(gray, W, H, s.style, s.threshold());
        byte[] job = Tspl.job(s, size, Collections.singletonList(mono), 1);
        // Run the job through a tiny TSPL interpreter to prove it reproduces the image exactly.
        byte[] printed = simulate(job, size.widthBytes(), H);
        check(printed != null, "job parses and ends in PRINT");
        check(printed != null && Arrays.equals(printed, mono.bits), "printed image matches the label bit-for-bit");
        int full = Tspl.fullBitmapBytes(size);
        System.out.printf("  job size %,d bytes vs %,d for a full bitmap (%.0f%% of original)%n",
                job.length, full, 100.0 * job.length / full);
        File png = new File(dir, "out.pbm");
        try (OutputStream o = new FileOutputStream(png)) {
            int wb = size.widthBytes(), hh = H;
            o.write(("P4\n" + (wb * 8) + " " + hh + "\n").getBytes("US-ASCII"));
            byte[] bits = printed != null ? printed.clone() : new byte[wb * hh];
            for (int i = 0; i < bits.length; i++) bits[i] = (byte) ~bits[i]; // PBM: 1 = black
            o.write(bits);
        }
        System.out.println("  wrote " + png + "  black dots: " + mono.blackCount());
    }

    /** Minimal TSPL interpreter: CLS, BITMAP (mode 0), PRINT. Returns the buffer at the first PRINT. */
    static byte[] simulate(byte[] job, int wb, int h) throws Exception { return simulate(job, wb, h, false); }

    /** Every compressed payload seen, so the harness can re-check them with an independent inflater. */
    static List<byte[]> payloads = new ArrayList<>();

    static byte[] simulate(byte[] job, int wb, int h, boolean inverted) throws Exception {
        byte[] canvas = new byte[wb * h];
        Arrays.fill(canvas, (byte) 0xFF);
        int pos = 0;
        while (pos < job.length) {
            int eol = pos;
            if (startsWith(job, pos, "BITMAP ")) {
                int c = pos; for (int k = 0; k < 5; k++) { while (job[c] != ',') c++; c++; }
                String[] a = new String(job, pos + 7, c - 1 - (pos + 7), "US-ASCII").split(",");
                int x = Integer.parseInt(a[0]), y = Integer.parseInt(a[1]), w = Integer.parseInt(a[2]), hh = Integer.parseInt(a[3]);
                if (x % 8 != 0 || x / 8 + w > wb || y + hh > h) return null;
                byte[] img;
                if (a[4].equals("3")) {                      // BITMAP ...,3,len,<zlib>; 1 = black
                    int c2 = c; while (job[c2] != ',') c2++;
                    int len = Integer.parseInt(new String(job, c, c2 - c, "US-ASCII"));
                    byte[] z = Arrays.copyOfRange(job, c2 + 1, c2 + 1 + len);
                    payloads.add(z);
                    java.util.zip.Inflater inf = new java.util.zip.Inflater();
                    inf.setInput(z);
                    img = new byte[w * hh];
                    int got = inf.inflate(img);
                    if (got != img.length || !inf.finished()) return null;
                    for (int i = 0; i < img.length; i++) img[i] = (byte) ~img[i];
                    c = c2 + 1 + len - w * hh;   // so the shared code below lands after the payload
                    pos = c2 + 1 + len;
                } else if (a[4].equals("0")) {
                    img = Arrays.copyOfRange(job, c, c + w * hh);
                    pos = c + w * hh;
                } else return null;
                if (inverted) for (int i = 0; i < img.length; i++) img[i] = (byte) ~img[i];
                for (int r = 0; r < hh; r++) System.arraycopy(img, r * w, canvas, (y + r) * wb + x / 8, w);
                if (job[pos] != '\r' || job[pos + 1] != '\n') return null;
                pos += 2;
                continue;
            }
            while (eol < job.length && job[eol] != '\n') eol++;
            String line = new String(job, pos, eol - pos, "US-ASCII").trim();
            if (line.equals("CLS")) Arrays.fill(canvas, (byte) 0xFF);
            if (line.startsWith("PRINT")) return canvas;
            pos = eol + 1;
        }
        return null;
    }

    static boolean startsWith(byte[] b, int pos, String s) {
        if (pos + s.length() > b.length) return false;
        for (int i = 0; i < s.length(); i++) if (b[pos + i] != s.charAt(i)) return false;
        return true;
    }
}
