package com.fanfold.core;

/** Plain settings holder (loaded from SharedPreferences by the app). */
public final class PrintSettings {
    // Page layout
    public static final int LAYOUT_AUTO = 0, LAYOUT_WHOLE = 1, LAYOUT_TRIM = 2, LAYOUT_TOP = 3, LAYOUT_BOTTOM = 4;
    public static final String[] LAYOUT_NAMES = {
            "Auto (recommended)", "Whole page", "Trim whitespace", "Top half of page", "Bottom half of page"};

    // Rotation: -1 auto, otherwise quarter turns clockwise
    public static final int ROT_AUTO = -1;
    public static final int[] ROT_VALUES = {ROT_AUTO, 0, 1, 2, 3};
    public static final String[] ROT_NAMES = {"Auto", "None", "90° right", "180°", "90° left"};

    // Image style
    public static final int STYLE_SHARP = 0, STYLE_PHOTO = 1;
    public static final String[] STYLE_NAMES = {"Sharp (labels, text, barcodes)", "Photo (dithered)"};

    // Line weight -> threshold (gray below threshold prints black)
    public static final int[] WEIGHT_THRESHOLDS = {140, 190, 215};
    public static final String[] WEIGHT_NAMES = {"Lighter", "Normal", "Bolder"};

    // Speed (0 = don't send)
    public static final int[] SPEED_VALUES = {0, 2, 3, 4, 5, 6};
    public static final String[] SPEED_NAMES = {"Printer default", "2 in/s (best quality)", "3 in/s", "4 in/s", "5 in/s", "6 in/s (fastest)"};

    // Gap
    public static final String[] GAP_CMDS = {"GAP 3 mm,0 mm", "GAP 2 mm,0 mm", "GAP 0,0", null};
    public static final String[] GAP_NAMES = {"3 mm (standard labels)", "2 mm", "Continuous roll (no gap)", "Use printer's calibration"};

    // Alignment nudge, in dots (8 dots = 1 mm), -4 mm .. +4 mm
    public static final int[] NUDGE_VALUES = {-32, -24, -16, -12, -8, -4, 0, 4, 8, 12, 16, 24, 32};
    public static String[] nudgeNames(String neg, String pos) {
        String[] n = new String[NUDGE_VALUES.length];
        for (int i = 0; i < n.length; i++) {
            int v = NUDGE_VALUES[i];
            n[i] = v == 0 ? "None" : (Math.abs(v) / 8.0 + " mm " + (v < 0 ? neg : pos)).replace(".0 mm", " mm");
        }
        return n;
    }
    public static int nudgeIndex(int v) {
        for (int i = 0; i < NUDGE_VALUES.length; i++) if (NUDGE_VALUES[i] == v) return i;
        return 6;
    }

    public int shiftX = 0, shiftY = 0;
    // How the label image is sent to the printer.
    public static final int TRANSFER_AUTO = -1, TRANSFER_COMPRESSED = 0, TRANSFER_INK_ONLY = 1, TRANSFER_FULL = 2;
    public static final int[] TRANSFER_VALUES = {TRANSFER_AUTO, TRANSFER_COMPRESSED, TRANSFER_INK_ONLY, TRANSFER_FULL};
    public static final String[] TRANSFER_NAMES = {
            "Auto",
            "Compressed (fastest \u2014 JADENS / Beeprt)",
            "Standard (any TSPL printer)",
            "Full image (slowest, most compatible)"};
    public static String transferShort(int t) {
        return t == TRANSFER_COMPRESSED ? "compressed" : t == TRANSFER_FULL ? "full image" : "standard";
    }
    public int transfer = TRANSFER_AUTO;
    /** What "Auto" resolves to for the current printer (filled in by the app from PrinterProfile). */
    public int resolvedTransfer = TRANSFER_INK_ONLY;
    public int transferMode() { return transfer >= 0 ? transfer : resolvedTransfer; }

    // Printer resolution: 0 = auto
    public static final int[] DPI_VALUES = {0, 203, 300};
    public int dpiSetting = 0;
    public int resolvedDpi = LabelSize.DPI;
    public int dpi() { return dpiSetting > 0 ? dpiSetting : resolvedDpi; }

    /** Fill in the "Auto" choices for a printer. */
    public void applyProfile(PrinterProfile p) {
        resolvedTransfer = p.transfer;
        resolvedDpi = p.dpi;
    }
    public String labelSizeId = LabelSize.ALL[0].id;
    public int layout = LAYOUT_AUTO;
    public int rotation = ROT_AUTO;
    public int style = STYLE_SHARP;
    public int weight = 1;
    public int density = 10;      // TSPL DENSITY 0..15
    public int speedIndex = 0;
    public int gapIndex = 0;
    public boolean flip = false;
    public boolean invert = false;
    public boolean statusCheck = true;
    public boolean autoPrintShared = false;

    public LabelSize labelSize() { return LabelSize.byId(labelSizeId).at(dpi()); }
    public int threshold() { return WEIGHT_THRESHOLDS[clamp(weight, 0, WEIGHT_THRESHOLDS.length - 1)]; }
    public int speed() { return SPEED_VALUES[clamp(speedIndex, 0, SPEED_VALUES.length - 1)]; }
    public String gapCmd() { return GAP_CMDS[clamp(gapIndex, 0, GAP_CMDS.length - 1)]; }

    static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
