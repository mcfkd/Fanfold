package com.fanfold.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.fanfold.core.PrintSettings;
import com.fanfold.core.PrinterProfile;

/** Persists settings and the chosen printer. */
final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("fanfold", Context.MODE_PRIVATE);
    }

    static PrintSettings load(Context c) {
        SharedPreferences p = sp(c);
        // 1.5/1.6 always sent compressed. Keep that for existing setups instead of re-guessing from the name.
        if (!p.getBoolean("migrated17", false)) {
            SharedPreferences.Editor e = p.edit().putBoolean("migrated17", true);
            if (p.contains("printerAddress") && !p.contains("transfer")) e.putInt("transfer", PrintSettings.TRANSFER_COMPRESSED);
            e.commit();
        }
        PrintSettings s = new PrintSettings();
        s.labelSizeId = p.getString("labelSize", s.labelSizeId);
        s.layout = p.getInt("layout", s.layout);
        s.rotation = p.getInt("rotation", s.rotation);
        s.style = p.getInt("style", s.style);
        s.weight = p.getInt("weight", s.weight);
        s.density = p.getInt("density", s.density);
        s.speedIndex = p.getInt("speedIndex", s.speedIndex);
        s.gapIndex = p.getInt("gapIndex", s.gapIndex);
        s.flip = p.getBoolean("flip", s.flip);
        s.invert = p.getBoolean("invert", s.invert);
        s.statusCheck = p.getBoolean("statusCheck", s.statusCheck);
        s.autoPrintShared = p.getBoolean("autoPrintShared", s.autoPrintShared);
        s.shiftX = p.getInt("shiftX", s.shiftX);
        s.shiftY = p.getInt("shiftY", s.shiftY);
        s.transfer = p.getInt("transfer", p.getBoolean("compact", true) ? s.transfer : PrintSettings.TRANSFER_FULL);
        s.dpiSetting = p.getInt("dpi", s.dpiSetting);
        s.applyProfile(profile(c));
        return s;
    }

    static void save(Context c, PrintSettings s) {
        sp(c).edit()
                .putString("labelSize", s.labelSizeId)
                .putInt("layout", s.layout)
                .putInt("rotation", s.rotation)
                .putInt("style", s.style)
                .putInt("weight", s.weight)
                .putInt("density", s.density)
                .putInt("speedIndex", s.speedIndex)
                .putInt("gapIndex", s.gapIndex)
                .putBoolean("flip", s.flip)
                .putBoolean("invert", s.invert)
                .putBoolean("statusCheck", s.statusCheck)
                .putBoolean("autoPrintShared", s.autoPrintShared)
                .putInt("shiftX", s.shiftX)
                .putInt("shiftY", s.shiftY)
                .putInt("transfer", s.transfer)
                .putInt("dpi", s.dpiSetting)
                .apply();
    }

    static void reset(Context c) {
        String addr = printerAddress(c), name = printerName(c);
        sp(c).edit().clear().apply();
        if (addr != null) setPrinter(c, addr, name);
    }

    /** Settings that change what the label looks like (used to decide when to re-render the preview). */
    static String renderSignature(PrintSettings s) {
        return s.labelSizeId + "|" + s.layout + "|" + s.rotation + "|" + s.style + "|" + s.weight + "|" + s.dpi();
    }

    /** Best guess at the chosen printer's family (from its Bluetooth name and any model it reported). */
    static PrinterProfile profile(Context c) {
        String addr = printerAddress(c);
        return PrinterProfile.match(printerName(c), addr == null ? null : sp(c).getString("model_" + addr, null));
    }

    static String printerModel(Context c) {
        String addr = printerAddress(c);
        return addr == null ? null : sp(c).getString("model_" + addr, null);
    }

    static final int[] KEEP_ALIVE_MINUTES = {2, 10, 30};

    static int keepAliveMinutes(Context c) { return sp(c).getInt("keepAliveMin", 10); }

    static void setKeepAliveMinutes(Context c, int min) { sp(c).edit().putInt("keepAliveMin", min).apply(); }

    static String printerAddress(Context c) { return sp(c).getString("printerAddress", null); }

    static String printerName(Context c) { return sp(c).getString("printerName", null); }

    static void setPrinter(Context c, String address, String name) {
        sp(c).edit().putString("printerAddress", address).putString("printerName", name)
                .remove("statusReply_" + address).remove("connMethod_" + address).apply();
    }
}
