package com.fanfold.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.os.ParcelFileDescriptor;

import com.fanfold.core.Affine;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** A PDF or image file that can be rendered through an arbitrary affine transform. */
abstract class SourceDoc implements Closeable {
    abstract int pageCount();
    abstract double pageWidth(int i);
    abstract double pageHeight(int i);
    abstract boolean isPdf();
    /** Renders page i onto a white w x h ARGB bitmap using the page->bitmap transform. */
    abstract Bitmap render(int i, Affine transform, int w, int h) throws IOException;

    static boolean isPdfFile(File f) {
        byte[] head = new byte[1024];
        int n = 0;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            n = in.read(head);
        } catch (IOException e) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
        String s = new String(head, 0, Math.max(0, n), java.nio.charset.Charset.forName("ISO-8859-1"));
        return s.contains("%PDF-");
    }

    static SourceDoc open(File f) throws IOException {
        return isPdfFile(f) ? new Pdf(f) : new Image(f);
    }

    static Matrix toMatrix(Affine a) {
        Matrix m = new Matrix();
        m.setValues(a.toMatrixValues());
        return m;
    }

    // ---------------------------------------------------------------- PDF
    static final class Pdf extends SourceDoc {
        private final ParcelFileDescriptor pfd;
        private final PdfRenderer renderer;
        private final double[] w, h;

        Pdf(File f) throws IOException {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer r;
            try {
                r = new PdfRenderer(pfd);
            } catch (SecurityException e) {
                pfd.close();
                throw new IOException("This PDF is password-protected.");
            } catch (IOException e) {
                pfd.close();
                throw new IOException("This PDF couldn't be opened (it may be damaged).");
            }
            renderer = r;
            int n = renderer.getPageCount();
            w = new double[n];
            h = new double[n];
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page p = renderer.openPage(i);
                w[i] = p.getWidth();
                h[i] = p.getHeight();
                p.close();
            }
        }

        @Override int pageCount() { return w.length; }
        @Override double pageWidth(int i) { return w[i]; }
        @Override double pageHeight(int i) { return h[i]; }
        @Override boolean isPdf() { return true; }

        @Override Bitmap render(int i, Affine t, int bw, int bh) {
            Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);   // PdfRenderer leaves unpainted areas transparent
            PdfRenderer.Page p = renderer.openPage(i);
            try {
                p.render(bmp, null, toMatrix(t), PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            } finally {
                p.close();
            }
            return bmp;
        }

        @Override public void close() {
            try { renderer.close(); } catch (Exception ignored) { }
            try { pfd.close(); } catch (IOException ignored) { }
        }
    }

    // ---------------------------------------------------------------- Image
    static final class Image extends SourceDoc {
        private final Bitmap bmp;

        Image(File f) throws IOException {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("That file isn't a PDF or an image I can read.");
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 4096) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
            if (b == null) throw new IOException("That image couldn't be decoded.");
            int deg = exifDegrees(f);
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            bmp = b;
        }

        private static int exifDegrees(File f) {
            try {
                ExifInterface e = new ExifInterface(f.getPath());
                switch (e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                    case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                    case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                    default: return 0;
                }
            } catch (Exception e) {
                return 0;
            }
        }

        @Override int pageCount() { return 1; }
        @Override double pageWidth(int i) { return bmp.getWidth(); }
        @Override double pageHeight(int i) { return bmp.getHeight(); }
        @Override boolean isPdf() { return false; }

        /** Halved copies of the image, so big downscales don't alias (bilinear only looks at 2x2 pixels). */
        private final java.util.ArrayList<Bitmap> pyramid = new java.util.ArrayList<Bitmap>();

        @Override Bitmap render(int i, Affine t, int bw, int bh) {
            double scale = Math.sqrt(Math.abs(t.a * t.d - t.b * t.c));
            int level = 0;
            while (scale * (1 << (level + 1)) <= 1.0 && level < 6) level++;
            Bitmap src = level(level);
            int f = 1 << level;
            Affine m = Affine.scale((double) bmp.getWidth() / src.getWidth()).then(t);
            if (f == 1) m = t;
            Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawColor(Color.WHITE);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(src, toMatrix(m), p);
            return out;
        }

        private Bitmap level(int n) {
            if (pyramid.isEmpty()) pyramid.add(bmp);
            while (pyramid.size() <= n) {
                Bitmap prev = pyramid.get(pyramid.size() - 1);
                int w = Math.max(1, prev.getWidth() / 2), h = Math.max(1, prev.getHeight() / 2);
                if (w == prev.getWidth() && h == prev.getHeight()) return prev;
                pyramid.add(Bitmap.createScaledBitmap(prev, w, h, true));
            }
            return pyramid.get(n);
        }

        @Override public void close() {
            for (Bitmap b : pyramid) if (b != bmp) b.recycle();
            pyramid.clear();
            bmp.recycle();
        }
    }
}
