package com.fanfold.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.fanfold.core.Affine;
import com.fanfold.core.LabelSize;
import com.fanfold.core.Layout;
import com.fanfold.core.Mono;
import com.fanfold.core.PrintSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Turns PDFs/images into printer-ready 1-bit label pages. */
final class LabelEngine {
    private LabelEngine() {}

    static final class Result {
        final List<Mono> pages = new ArrayList<Mono>();
        int blankSkipped;
        final List<String> errors = new ArrayList<String>();
    }

    // ------------------------------------------------------------ importing

    /** Copies shared/picked content into our cache so it stays readable (and seekable for PdfRenderer). */
    static File importUri(Context c, Uri uri, int index) throws IOException {
        File dir = new File(c.getCacheDir(), "in");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Can't create cache folder");
        String name = displayName(c, uri);
        String ext = name != null && name.toLowerCase().endsWith(".pdf") ? ".pdf" : "";
        File f = new File(dir, System.currentTimeMillis() + "_" + index + ext);
        ContentResolver cr = c.getContentResolver();
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new IOException("Couldn't open the shared file");
        OutputStream out = new FileOutputStream(f);
        try {
            copy(in, out);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
            out.close();
        }
        if (f.length() == 0) { f.delete(); throw new IOException("The shared file was empty"); }
        return f;
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static String displayName(Context c, Uri uri) {
        if ("file".equals(uri.getScheme())) return uri.getLastPathSegment();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return uri.getLastPathSegment();
    }

    /** Deletes imported files older than a day. */
    static void cleanCache(Context c) {
        File dir = new File(c.getCacheDir(), "in");
        File[] fs = dir.listFiles();
        if (fs == null) return;
        long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : fs) if (f.lastModified() < cutoff) f.delete();
    }

    // ------------------------------------------------------------ rendering

    static Result process(List<File> files, PrintSettings s, LabelSize size) {
        Result r = new Result();
        for (File f : files) {
            SourceDoc doc = null;
            try {
                doc = SourceDoc.open(f);
                for (int i = 0; i < doc.pageCount(); i++) {
                    Mono m = renderPage(doc, i, s, size);
                    if (m == null) r.blankSkipped++;
                    else r.pages.add(m);
                }
            } catch (IOException e) {
                r.errors.add(e.getMessage());
            } catch (OutOfMemoryError e) {
                r.errors.add("Ran out of memory rendering that file.");
            } catch (RuntimeException e) {
                r.errors.add("Couldn't render that file (" + e.getClass().getSimpleName() + ").");
            } finally {
                if (doc != null) try { doc.close(); } catch (IOException ignored) { }
            }
        }
        return r;
    }

    static Mono renderPage(SourceDoc doc, int i, PrintSettings s, LabelSize size) throws IOException {
        double pw = doc.pageWidth(i), ph = doc.pageHeight(i);
        if (pw <= 0 || ph <= 0) return null;
        // 1) quick low-res look at the page to decide crop + rotation
        double k = 1000.0 / Math.max(pw, ph);
        int lw = Math.max(1, (int) Math.ceil(pw * k)), lh = Math.max(1, (int) Math.ceil(ph * k));
        Bitmap low = doc.render(i, Affine.scale(k), lw, lh);
        int[] px = new int[lw * lh];
        low.getPixels(px, 0, lw, 0, 0, lw, lh);
        low.recycle();
        Layout.Plan plan = Layout.plan(Mono.toGray(px), lw, lh, k, pw, ph, doc.isPdf(), s, size);
        if (plan.blank) return null;
        // 2) render straight at printer resolution through the chosen transform (vector-sharp for PDFs)
        int W = size.widthDots, H = size.heightDots;
        Bitmap full = doc.render(i, plan.transform, W, H);
        int[] fp = new int[W * H];
        full.getPixels(fp, 0, W, 0, 0, W, H);
        full.recycle();
        Mono m = Mono.fromGray(Mono.toGray(fp), W, H, s.style, s.threshold());
        m.note = plan.note;
        return m;
    }

    static Bitmap toBitmap(Mono m) {
        return Bitmap.createBitmap(m.toArgb(), m.w, m.h, Bitmap.Config.ARGB_8888);
    }

    // ------------------------------------------------------------ test label

    static Mono testLabel(LabelSize size, PrintSettings s, String printerName) {
        int W = size.widthDots, H = size.heightDots;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        float u = Math.min(W, H) / 20f;           // layout unit
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(3, u / 6));
        c.drawRect(u / 2, u / 2, W - u / 2, H - u / 2, p);
        p.setStyle(Paint.Style.FILL);

        float x = u * 1.4f, y = u * 3.2f;
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(u * 1.9f);
        c.drawText("Fanfold", x, y, p);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        p.setTextSize(u * 1.0f);
        y += u * 1.8f;
        c.drawText("Test print ✓  it works!", x, y, p);
        p.setTextSize(u * 0.75f);
        String[] lines = {
                "Printer: " + (printerName != null ? printerName : "?"),
                "Label: " + size.shortName() + "  (" + W + " x " + H + " dots)",
                "Darkness: " + s.density + "   Speed: " + PrintSettings.SPEED_NAMES[Math.max(0, Math.min(PrintSettings.SPEED_NAMES.length - 1, s.speedIndex))],
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date()),
        };
        for (String l : lines) {
            y += u * 1.15f;
            if (y > H - u * 2) break;
            c.drawText(l, x, y, p);
        }
        // line-weight ladder (1..6 dots) + a gray ramp: shows darkness and detail
        int[] ramp = null;
        y += u * 1.2f;
        if (y < H - u * 4) {
            float lx = x;
            for (int t = 1; t <= 6; t++) {
                c.drawRect(lx, y, lx + t, y + u * 2.2f, p);
                lx += u * 0.9f;
            }
            float rampX = lx + u, rampW = W - u * 1.4f - rampX;
            if (rampW > u * 3) {
                ramp = new int[]{(int) rampX, (int) y, (int) (rampX + rampW), (int) (y + u * 2.2f)};
                Paint g = new Paint();
                for (int k = 0; k < 16; k++) {
                    int v = 255 - k * 17;
                    g.setColor(Color.rgb(v, v, v));
                    c.drawRect(rampX + rampW * k / 16f, y, rampX + rampW * (k + 1) / 16f, y + u * 2.2f, g);
                }
            }
        }
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        b.recycle();
        int[] gray = Mono.toGray(px);
        Mono m = Mono.fromGray(gray, W, H, PrintSettings.STYLE_SHARP, 128);
        if (ramp != null) {   // dither just the gray ramp
            Mono d = Mono.fromGray(gray, W, H, PrintSettings.STYLE_PHOTO, 128);
            for (int yy = Math.max(0, ramp[1]); yy < Math.min(H, ramp[3]); yy++)
                for (int xx = Math.max(0, ramp[0]); xx < Math.min(W, ramp[2]); xx++)
                    if (d.isBlack(xx, yy) != m.isBlack(xx, yy)) {
                        if (d.isBlack(xx, yy)) m.setBlack(xx, yy);
                        else m.bits[yy * m.wb + (xx >> 3)] |= (byte) (0x80 >> (xx & 7));
                    }
        }
        m.note = "test label";
        return m;
    }
}
