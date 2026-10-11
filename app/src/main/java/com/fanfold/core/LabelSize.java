package com.fanfold.core;

/** Label stock sizes. Dot counts depend on the printer's resolution (203 or 300 dpi); see {@link #at}. */
public final class LabelSize {
    /** Default resolution of nearly all cheap 4x6 printers. */
    public static final int DPI = 203;

    public final String id;        // stable id used in prefs + print service media ids
    public final String name;      // shown to the user
    public final String sizeCmd;   // TSPL SIZE arguments
    public final int dpi;
    public final int widthDots;
    public final int heightDots;
    public final int widthMils;    // for Android print framework (1/1000 inch)
    public final int heightMils;
    private final double wIn, hIn; // physical size (inches)
    private final boolean metric;
    private final int wMm, hMm;

    private LabelSize(String id, String name, String sizeCmd, int dpi, double wIn, double hIn, boolean metric, int wMm, int hMm) {
        this.id = id; this.name = name; this.sizeCmd = sizeCmd; this.dpi = dpi;
        this.wIn = wIn; this.hIn = hIn; this.metric = metric; this.wMm = wMm; this.hMm = hMm;
        if (metric) {
            // TSPL convention: 8 dots/mm at 203 dpi, 12 dots/mm at 300 dpi
            int perMm = dpi == 203 ? 8 : (int) Math.round(dpi / 25.4);
            this.widthDots = wMm * perMm;
            this.heightDots = hMm * perMm;
        } else {
            this.widthDots = (int) Math.floor(wIn * dpi);
            this.heightDots = (int) Math.floor(hIn * dpi);
        }
        this.widthMils = (int) Math.round(wIn * 1000);
        this.heightMils = (int) Math.round(hIn * 1000);
    }

    private static LabelSize inches(String id, String name, String w, String h) {
        return new LabelSize(id, name, w + "," + h, DPI, Double.parseDouble(w), Double.parseDouble(h), false, 0, 0);
    }

    private static LabelSize mm(String id, String name, int w, int h) {
        return new LabelSize(id, name, w + " mm," + h + " mm", DPI, w / 25.4, h / 25.4, true, w, h);
    }

    public static final LabelSize[] ALL = {
            inches("LP_4X6", "4 x 6 in (shipping)", "4", "6"),
            mm("LP_100X150", "100 x 150 mm", 100, 150),
            inches("LP_4X4", "4 x 4 in", "4", "4"),
            inches("LP_4X3", "4 x 3 in", "4", "3"),
            inches("LP_4X2", "4 x 2 in", "4", "2"),
            inches("LP_3X2", "3 x 2 in", "3", "2"),
            inches("LP_225X125", "2.25 x 1.25 in", "2.25", "1.25"),
            inches("LP_2X1", "2 x 1 in", "2", "1"),
    };

    /** The same physical label at another printer resolution. */
    public LabelSize at(int newDpi) {
        if (newDpi == dpi || newDpi <= 0) return this;
        return new LabelSize(id, name, sizeCmd, newDpi, wIn, hIn, metric, wMm, hMm);
    }

    public static LabelSize byId(String id) {
        if (id != null) for (LabelSize s : ALL) if (s.id.equals(id)) return s;
        return ALL[0];
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(id)) return i;
        return 0;
    }

    public int widthBytes() { return (widthDots + 7) / 8; }

    public String shortName() {
        int p = name.indexOf(" (");
        return p > 0 ? name.substring(0, p) : name;
    }
}
