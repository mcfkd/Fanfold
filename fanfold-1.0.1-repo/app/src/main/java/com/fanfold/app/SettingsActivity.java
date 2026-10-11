package com.fanfold.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.fanfold.core.LabelSize;
import com.fanfold.core.PrintSettings;

public class SettingsActivity extends Activity {
    private PrintSettings s;
    private LinearLayout root;

    private interface IntSetter { void set(int v); }
    private interface BoolSetter { void set(boolean v); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setTitle("Settings");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        build();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void save() { Prefs.save(this, s); }

    private void build() {
        s = Prefs.load(this);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root);

        LinearLayout label = section("Label");
        String[] sizes = new String[LabelSize.ALL.length];
        for (int i = 0; i < sizes.length; i++) sizes[i] = LabelSize.ALL[i].name;
        spinner(label, "Label size", null, sizes, LabelSize.indexOf(s.labelSizeId), new IntSetter() {
            @Override public void set(int v) { s.labelSizeId = LabelSize.ALL[v].id; }
        });
        spinner(label, "Page layout", "Auto prints real 4×6 PDFs at exact size, grabs the label off the top half of letter-size pages, and trims whitespace from everything else.",
                PrintSettings.LAYOUT_NAMES, s.layout, new IntSetter() {
                    @Override public void set(int v) { s.layout = v; }
                });
        int rotIdx = 0;
        for (int i = 0; i < PrintSettings.ROT_VALUES.length; i++) if (PrintSettings.ROT_VALUES[i] == s.rotation) rotIdx = i;
        spinner(label, "Rotation", null, PrintSettings.ROT_NAMES, rotIdx, new IntSetter() {
            @Override public void set(int v) { s.rotation = PrintSettings.ROT_VALUES[v]; }
        });
        spinner(label, "Image style", null, PrintSettings.STYLE_NAMES, s.style, new IntSetter() {
            @Override public void set(int v) { s.style = v; }
        });
        spinner(label, "Line weight", "Bolder helps faint text; lighter helps if barcodes look smeared.",
                PrintSettings.WEIGHT_NAMES, s.weight, new IntSetter() {
                    @Override public void set(int v) { s.weight = v; }
                });

        LinearLayout printer = section("Printer");
        darkness(printer);
        spinner(printer, "Print speed", "Slower speeds print a little darker.", PrintSettings.SPEED_NAMES, s.speedIndex, new IntSetter() {
            @Override public void set(int v) { s.speedIndex = v; }
        });
        spinner(printer, "Label gap", "Standard fanfold and roll shipping labels use about 3 mm.", PrintSettings.GAP_NAMES, s.gapIndex, new IntSetter() {
            @Override public void set(int v) { s.gapIndex = v; }
        });
        spinner(printer, "Nudge left / right", "Use if the print sits off-center on the label.",
                PrintSettings.nudgeNames("left", "right"), PrintSettings.nudgeIndex(s.shiftX), new IntSetter() {
                    @Override public void set(int v) { s.shiftX = PrintSettings.NUDGE_VALUES[v]; }
                });
        spinner(printer, "Nudge up / down", "Use if the print starts too high or too low.",
                PrintSettings.nudgeNames("up", "down"), PrintSettings.nudgeIndex(s.shiftY), new IntSetter() {
                    @Override public void set(int v) { s.shiftY = PrintSettings.NUDGE_VALUES[v]; }
                });
        final com.fanfold.core.PrinterProfile prof = Prefs.profile(this);
        String[] tNames = PrintSettings.TRANSFER_NAMES.clone();
        tNames[0] = "Auto \u2014 " + PrintSettings.transferShort(prof.transfer) + " (" + prof.family + ")";
        int tIdx = 0;
        for (int i = 0; i < PrintSettings.TRANSFER_VALUES.length; i++) if (PrintSettings.TRANSFER_VALUES[i] == s.transfer) tIdx = i;
        spinner(printer, "Transfer mode", "How the label is sent. Compressed is near-instant but only some printers support it. If test labels come out blank or garbled, pick Standard.",
                tNames, tIdx, new IntSetter() {
                    @Override public void set(int v) { s.transfer = PrintSettings.TRANSFER_VALUES[v]; }
                });
        String[] dNames = {"Auto \u2014 " + prof.dpi + " dpi (" + prof.family + ")", "203 dpi (most 4\u00d76 printers)", "300 dpi"};
        int dIdx = s.dpiSetting == 203 ? 1 : s.dpiSetting == 300 ? 2 : 0;
        spinner(printer, "Printer resolution", "If labels print too big or too small, your printer is probably the other resolution.",
                dNames, dIdx, new IntSetter() {
                    @Override public void set(int v) { s.dpiSetting = PrintSettings.DPI_VALUES[v]; }
                });
        final int[] mins = Prefs.KEEP_ALIVE_MINUTES;
        int kIdx = 1;
        for (int i = 0; i < mins.length; i++) if (mins[i] == Prefs.keepAliveMinutes(this)) kIdx = i;
        spinner(printer, "Stay connected after printing", "Longer means no reconnect wait between orders. While connected, other phones and apps can't use the printer (tap Disconnect on the main screen).",
                new String[]{"2 minutes", "10 minutes (recommended)", "30 minutes"}, kIdx, new IntSetter() {
                    @Override public void set(int v) { Prefs.setKeepAliveMinutes(SettingsActivity.this, mins[v]); }
                });
        toggle(printer, "Check paper & cover before printing", "Stops with a clear message if the printer reports it’s out of labels or open.", s.statusCheck, new BoolSetter() {
            @Override public void set(boolean v) { s.statusCheck = v; }
        });
        toggle(printer, "Flip 180°", "Rotates the printout if labels come out upside down for you.", s.flip, new BoolSetter() {
            @Override public void set(boolean v) { s.flip = v; }
        });
        toggle(printer, "Invert", "Only turn on if the test label prints white-on-black.", s.invert, new BoolSetter() {
            @Override public void set(boolean v) { s.invert = v; }
        });

        LinearLayout flow = section("Sharing");
        toggle(flow, "Print right away when shared", "Skip the preview: sharing a label to Fanfold prints it immediately.", s.autoPrintShared, new BoolSetter() {
            @Override public void set(boolean v) { s.autoPrintShared = v; }
        });

        LinearLayout info = section("Printer info");
        final TextView diag = Ui.text(this, BtPrinter.diagnostics(this), 12, Ui.TEXT2, false);
        diag.setTypeface(android.graphics.Typeface.MONOSPACE);
        diag.setTextIsSelectable(true);
        info.addView(diag, Ui.matchWrap(this, 12));
        Button copy = Ui.button(this, "Copy printer info", 2);
        info.addView(copy, Ui.matchWrap(this, 10));
        info.addView(Ui.spacer(this, 8));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Fanfold printer info", BtPrinter.diagnostics(SettingsActivity.this)));
                android.widget.Toast.makeText(SettingsActivity.this, "Copied \u2014 paste it into your printer report", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        Button reset = Ui.button(this, "Reset to defaults", 3);
        root.addView(reset, Ui.matchWrap(this, 12));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setMessage("Reset all settings? Your chosen printer is kept.")
                        .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                Prefs.reset(SettingsActivity.this);
                                build();
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        });

        String version = "";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = " " + pi.versionName;
        } catch (Exception ignored) { }
        TextView about = Ui.text(this, "Fanfold" + version + "\nFree and open source (MIT). Prints shipping labels to TSPL thermal printers over Bluetooth \u2014 no account, no cloud, no ads.\nNot affiliated with JADENS, Beeprt or any printer maker.", 12, Ui.TEXT2, false);
        about.setGravity(Gravity.CENTER);
        root.addView(about, Ui.matchWrap(this, 16));

        setContentView(scroll);
    }

    private LinearLayout section(String title) {
        TextView t = Ui.sectionTitle(this, title);
        LinearLayout.LayoutParams tlp = Ui.matchWrap(this, root.getChildCount() == 0 ? 0 : 20);
        tlp.leftMargin = Ui.dp(this, 4);
        root.addView(t, tlp);
        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 8));
        root.addView(card, Ui.matchWrap(this, 8));
        return card;
    }

    private void divider(LinearLayout parent) {
        if (parent.getChildCount() == 0) return;
        View d = new View(this);
        d.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.7f)));
        p.topMargin = Ui.dp(this, 4);
        parent.addView(d, p);
    }

    private void spinner(LinearLayout parent, String title, String hint, String[] options, int selected, final IntSetter setter) {
        divider(parent);
        TextView t = Ui.text(this, title, 16, Ui.TEXT, true);
        parent.addView(t, Ui.matchWrap(this, 12));
        if (hint != null) parent.addView(Ui.text(this, hint, 13, Ui.TEXT2, false), Ui.matchWrap(this, 2));
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(Math.max(0, Math.min(options.length - 1, selected)));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { setter.set(pos); save(); }
            @Override public void onNothingSelected(AdapterView<?> a) { }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        lp.topMargin = Ui.dp(this, 8);
        lp.bottomMargin = Ui.dp(this, 8);
        parent.addView(sp, lp);
    }

    private void toggle(LinearLayout parent, String title, String hint, boolean value, final BoolSetter setter) {
        divider(parent);
        LinearLayout row = Ui.row(this);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        if (hint != null) texts.addView(Ui.text(this, hint, 13, Ui.TEXT2, false), Ui.matchWrap(this, 2));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean v) { setter.set(v); save(); }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 12);
        row.addView(sw, slp);
        row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        parent.addView(row, Ui.matchWrap(this, 0));
    }

    private void darkness(LinearLayout parent) {
        divider(parent);
        final TextView t = Ui.text(this, "", 16, Ui.TEXT, true);
        parent.addView(t, Ui.matchWrap(this, 12));
        parent.addView(Ui.text(this, "Raise it if labels look faint or gray. 8–12 is typical.", 13, Ui.TEXT2, false), Ui.matchWrap(this, 2));
        SeekBar sb = new SeekBar(this);
        sb.setMax(14);                        // 1..15
        sb.setProgress(Math.max(1, Math.min(15, s.density)) - 1);
        t.setText("Darkness: " + s.density);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                s.density = p + 1;
                t.setText("Darkness: " + s.density);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { save(); }
        });
        LinearLayout.LayoutParams lp = Ui.matchWrap(this, 10);
        lp.bottomMargin = Ui.dp(this, 10);
        parent.addView(sb, lp);
    }
}
