package com.fanfold.core;

/**
 * Decides which part of a page to print and how to map it onto the label
 * (crop, rotation, scale), independent of Android so it can be unit-tested.
 */
public final class Layout {
    /** Gray level below which a pixel counts as "ink" when looking for content. */
    public static final int INK = 200;

    private Layout() {}

    public static final class Plan {
        public boolean blank;
        public double l, t, r, b;       // chosen source region, in page units
        public int quarterTurns;        // rotation applied (clockwise)
        public Affine transform;        // page units -> label dots
        public String note = "";
    }

    /**
     * Bounding box [l, t, r, b) of ink inside [x0,x1) x [y0,y1), or null when empty.
     * A row/column only counts if it holds at least minInk ink pixels (ignores specks).
     */
    public static int[] contentBounds(int[] gray, int w, int h, int x0, int y0, int x1, int y1, int minInk) {
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(w, x1); y1 = Math.min(h, y1);
        if (x1 <= x0 || y1 <= y0) return null;
        int[] colCount = new int[x1 - x0];
        int top = -1, bottom = -1;
        for (int y = y0; y < y1; y++) {
            int row = y * w, n = 0;
            for (int x = x0; x < x1; x++) {
                if (gray[row + x] < INK) { n++; colCount[x - x0]++; }
            }
            if (n >= minInk) { if (top < 0) top = y; bottom = y + 1; }
        }
        if (top < 0) return null;
        int left = -1, right = -1;
        for (int i = 0; i < colCount.length; i++) {
            if (colCount[i] >= minInk) { if (left < 0) left = i + x0; right = i + x0 + 1; }
        }
        if (left < 0) return null;
        return new int[]{left, top, right, bottom};
    }

    /**
     * Like contentBounds, but drops thin isolated lines at the edges of the content
     * (dashed "fold here" / "cut here" lines) so they don't inflate the crop.
     */
    public static int[] refinedBounds(int[] gray, int w, int h, int x0, int y0, int x1, int y1, int minInk) {
        int[] r = contentBounds(gray, w, h, x0, y0, x1, y1, minInk);
        if (r == null) return null;
        int thinY = Math.max(3, Math.round(h * 0.005f));
        int[] ys = trimThinEdgeBands(gray, w, r[0], r[2], r[1], r[3], minInk, thinY, true);
        int[] r2 = contentBounds(gray, w, h, r[0], ys[0], r[2], ys[1], minInk);
        if (r2 == null) return r;
        int thinX = Math.max(3, Math.round(w * 0.005f));
        int[] xs = trimThinEdgeBands(gray, w, r2[1], r2[3], r2[0], r2[2], minInk, thinX, false);
        int[] r3 = contentBounds(gray, w, h, xs[0], r2[1], xs[1], r2[3], minInk);
        return r3 == null ? r2 : r3;
    }

    /**
     * Scans lines (rows if byRow, else columns) in [from,to) — each line spanning [a,b) —
     * groups inked lines into bands, and removes thin bands at either end.
     */
    private static int[] trimThinEdgeBands(int[] gray, int w, int a, int b, int from, int to,
                                           int minInk, int thin, boolean byRow) {
        java.util.ArrayList<int[]> bands = new java.util.ArrayList<int[]>();
        int start = -1, lastInk = -1;
        for (int i = from; i < to; i++) {
            int n = 0;
            for (int j = a; j < b; j++) {
                int v = byRow ? gray[i * w + j] : gray[j * w + i];
                if (v < INK) n++;
            }
            if (n >= minInk) {
                if (start < 0) start = i;
                else if (i - lastInk > 2) { bands.add(new int[]{start, lastInk + 1}); start = i; }
                lastInk = i;
            }
        }
        if (start >= 0) bands.add(new int[]{start, lastInk + 1});
        if (bands.isEmpty()) return new int[]{from, to};
        while (bands.size() > 1 && bands.get(0)[1] - bands.get(0)[0] <= thin) bands.remove(0);
        while (bands.size() > 1 && bands.get(bands.size() - 1)[1] - bands.get(bands.size() - 1)[0] <= thin)
            bands.remove(bands.size() - 1);
        return new int[]{bands.get(0)[0], bands.get(bands.size() - 1)[1]};
    }

    /**
     * @param gray      low-resolution grayscale render of the whole page (w x h pixels)
     * @param lowScale  pixels per page unit of that render
     * @param pageW     page width in page units (PDF points or image pixels)
     * @param pageH     page height in page units
     * @param isPdf     true for PDF pages (units are points, so physical size is known)
     */
    public static Plan plan(int[] gray, int w, int h, double lowScale, double pageW, double pageH,
                            boolean isPdf, PrintSettings s, LabelSize size) {
        Plan p = new Plan();
        int minInk = Math.max(1, Math.max(w, h) / 500);
        int[] full = contentBounds(gray, w, h, 0, 0, w, h, minInk);
        if (full == null) { p.blank = true; return p; }

        int mode = s.layout;
        int[] region;
        if (mode == PrintSettings.LAYOUT_AUTO) {
            mode = autoMode(full, w, h, pageW, pageH, isPdf, size);
        }
        switch (mode) {
            case PrintSettings.LAYOUT_WHOLE:
                region = new int[]{0, 0, w, h};
                p.note = "whole page";
                break;
            case PrintSettings.LAYOUT_TOP: {
                int[] r = refinedBounds(gray, w, h, 0, 0, w, h / 2, minInk);
                if (r == null) { r = refinedBounds(gray, w, h, 0, 0, w, h, minInk); p.note = "trimmed"; }
                else p.note = "top half";
                region = pad(r, w, h);
                break;
            }
            case PrintSettings.LAYOUT_BOTTOM: {
                int[] r = refinedBounds(gray, w, h, 0, h / 2, w, h, minInk);
                if (r == null) { r = refinedBounds(gray, w, h, 0, 0, w, h, minInk); p.note = "trimmed"; }
                else p.note = "bottom half";
                region = pad(r, w, h);
                break;
            }
            case PrintSettings.LAYOUT_TRIM:
            default:
                region = pad(refinedBounds(gray, w, h, 0, 0, w, h, minInk), w, h);
                p.note = "trimmed";
                break;
        }

        // Region back to page units, clamped to the page.
        p.l = Math.max(0, region[0] / lowScale);
        p.t = Math.max(0, region[1] / lowScale);
        p.r = Math.min(pageW, region[2] / lowScale);
        p.b = Math.min(pageH, region[3] / lowScale);
        if (mode == PrintSettings.LAYOUT_WHOLE) { p.l = 0; p.t = 0; p.r = pageW; p.b = pageH; }

        double rw = p.r - p.l, rh = p.b - p.t;
        int W = size.widthDots, H = size.heightDots;
        int q;
        if (s.rotation == PrintSettings.ROT_AUTO) {
            double s0 = fitScale(rw, rh, 0, W, H), s1 = fitScale(rw, rh, 1, W, H);
            q = (s1 > s0 * 1.05) ? 1 : 0;
        } else {
            q = s.rotation;
        }
        p.quarterTurns = q;
        p.transform = fit(p.l, p.t, rw, rh, q, W, H);
        if (q == 1 || q == 3) p.note += ", rotated";
        else if (q == 2) p.note += ", upside down";
        return p;
    }

    /** Picks the concrete layout mode for "Auto". */
    static int autoMode(int[] full, int w, int h, double pageW, double pageH, boolean isPdf, LabelSize size) {
        if (!isPdf) return PrintSettings.LAYOUT_TRIM;   // photos/screenshots: always trim
        double pageLong = Math.max(pageW, pageH), pageShort = Math.min(pageW, pageH);
        double pageAspect = pageLong / pageShort;
        double labelAspect = (double) Math.max(size.widthDots, size.heightDots) / Math.min(size.widthDots, size.heightDots);
        double contentW = (full[2] - full[0]) / (double) w;
        double contentH = (full[3] - full[1]) / (double) h;

        boolean labelSized = pageLong <= 7.5 * 72 && Math.abs(pageAspect - labelAspect) / labelAspect < 0.08;
        if (labelSized) {
            // A real label PDF: print 1:1 unless the content is a small island on the page.
            if (contentW >= 0.75 || contentH >= 0.75) return PrintSettings.LAYOUT_WHOLE;
            return PrintSettings.LAYOUT_TRIM;
        }
        boolean letterLike = pageLong >= 9.5 * 72 && pageAspect > 1.2 && pageAspect < 1.5 && pageH > pageW;
        if (letterLike && contentH > 0.55) {
            // Label on the top half, receipt/instructions below the fold.
            return PrintSettings.LAYOUT_TOP;
        }
        return PrintSettings.LAYOUT_TRIM;
    }

    static int[] pad(int[] r, int w, int h) {
        int pad = Math.max(2, Math.round(Math.max(w, h) * 0.012f));
        return new int[]{Math.max(0, r[0] - pad), Math.max(0, r[1] - pad), Math.min(w, r[2] + pad), Math.min(h, r[3] + pad)};
    }

    static double fitScale(double rw, double rh, int q, int W, int H) {
        boolean swap = (q & 1) == 1;
        double ew = swap ? rh : rw, eh = swap ? rw : rh;
        return Math.min(W / ew, H / eh);
    }

    /** Maps the source rect (l,t,rw,rh) onto a W x H label, rotated q quarter turns, fit + centered. */
    public static Affine fit(double l, double t, double rw, double rh, int q, int W, int H) {
        Affine m = Affine.translate(-l, -t).then(Affine.rotateQuarter(q));
        double[][] corners = {m.map(l, t), m.map(l + rw, t), m.map(l, t + rh), m.map(l + rw, t + rh)};
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (double[] c : corners) { minX = Math.min(minX, c[0]); minY = Math.min(minY, c[1]); }
        m = m.then(Affine.translate(-minX, -minY));
        double s = fitScale(rw, rh, q, W, H);
        boolean swap = (q & 1) == 1;
        double ew = (swap ? rh : rw) * s, eh = (swap ? rw : rh) * s;
        double ox = Math.round((W - ew) / 2.0), oy = Math.round((H - eh) / 2.0);
        return m.then(Affine.scale(s)).then(Affine.translate(ox, oy));
    }
}
