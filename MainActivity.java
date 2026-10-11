package com.fanfold.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.fanfold.core.LabelSize;
import com.fanfold.core.Mono;
import com.fanfold.core.PrintSettings;
import com.fanfold.core.Tspl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1, REQ_ENABLE_BT = 2, REQ_PERM = 3;

    private final ArrayList<File> files = new ArrayList<File>();
    private List<Mono> pages = new ArrayList<Mono>();
    private int pageIndex = 0;
    private int copies = 1;
    private boolean busy = false;
    private boolean autoPrintPending = false;
    private int renderGen = 0;
    private String renderedSignature = null;
    private boolean destroyed = false;
    private Runnable afterPermission = null;

    private TextView connState;
    private Button disconnectBtn;
    private TextView printerName, printerSub, pageInfo, copiesText, statusText, emptyText;
    private ImageView preview;
    private FrameLayout previewFrame;
    private LinearLayout pageNav;
    private Button prevBtn, nextBtn, printBtn, openBtn;
    private Spinner sizeSpinner;
    private ProgressBar progress;
    private boolean sizeSpinnerReady = false;

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildUi();
        LabelEngine.cleanCache(this);
        if (saved != null) {
            String[] paths = saved.getStringArray("files");
            setCopies(saved.getInt("copies", 1));
            if (paths != null) for (String p : paths) { File f = new File(p); if (f.exists()) files.add(f); }
            if (!files.isEmpty()) render();
        } else {
            handleIntent(getIntent());
        }
        if (!BtPrinter.hasPermission(this)) requestBtPermission(null);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        String[] paths = new String[files.size()];
        for (int i = 0; i < paths.length; i++) paths[i] = files.get(i).getPath();
        out.putStringArray("files", paths);
        out.putInt("copies", copies);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPrinterCard();
        BtPrinter.warmUp(this, new Runnable() {
            @Override public void run() { if (!destroyed) refreshPrinterCard(); }
        });
        PrintSettings s = Prefs.load(this);
        sizeSpinnerReady = false;
        sizeSpinner.setSelection(LabelSize.indexOf(s.labelSizeId));
        sizeSpinner.post(new Runnable() { @Override public void run() { sizeSpinnerReady = true; } });
        if (!files.isEmpty() && renderedSignature != null && !renderedSignature.equals(Prefs.renderSignature(s))) render();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- printer card
        LinearLayout pc = Ui.card(this);
        pc.addView(Ui.sectionTitle(this, "Printer"));
        printerName = Ui.text(this, "", 19, Ui.TEXT, true);
        pc.addView(printerName, Ui.matchWrap(this, 6));
        printerSub = Ui.text(this, "", 13, Ui.TEXT2, false);
        pc.addView(printerSub, Ui.matchWrap(this, 2));
        LinearLayout stateRow = Ui.row(this);
        connState = Ui.text(this, "", 13, Ui.TEXT2, true);
        stateRow.addView(connState, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        disconnectBtn = Ui.button(this, "Disconnect", 3);
        disconnectBtn.setTextSize(13);
        disconnectBtn.setMinHeight(0);
        disconnectBtn.setMinimumHeight(0);
        disconnectBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        disconnectBtn.setContentDescription("Disconnect from the printer so other apps can use it");
        stateRow.addView(disconnectBtn, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pc.addView(stateRow, Ui.matchWrap(this, 4));
        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BtPrinter.disconnect(new Runnable() {
                    @Override public void run() { if (!destroyed) { refreshPrinterCard(); setStatus("Disconnected \u2014 other apps can use the printer now.", Ui.TEXT2); } }
                });
            }
        });
        LinearLayout pr = Ui.row(this);
        Button choose = Ui.button(this, "Choose printer", 2);
        Button test = Ui.button(this, "Test print", 2);
        Button check = Ui.button(this, "Check", 2);
        pr.addView(choose, Ui.weighted(this, 1.3f, 3));
        pr.addView(test, Ui.weighted(this, 1f, 3));
        pr.addView(check, Ui.weighted(this, 0.8f, 3));
        pc.addView(pr, Ui.matchWrap(this, 12));
        root.addView(pc, Ui.matchWrap(this, 0));
        choose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { choosePrinter(); } });
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { testPrint(); } });
        check.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { checkPrinter(); } });

        // --- label card
        LinearLayout lc = Ui.card(this);
        lc.addView(Ui.sectionTitle(this, "Label"));
        previewFrame = new FrameLayout(this);
        previewFrame.setBackground(Ui.rounded(0xFFE6E9EE, Ui.dp(this, 12), 0, 0));
        int fp = Ui.dp(this, 10);
        previewFrame.setPadding(fp, fp, fp, fp);
        preview = new ImageView(this);
        preview.setContentDescription("Label preview. Tap to enlarge.");
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        emptyText = Ui.text(this, EMPTY_MSG, 15, Ui.TEXT2, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(fp * 2, 0, fp * 2, 0);
        previewFrame.addView(emptyText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 400));
        plp.topMargin = Ui.dp(this, 10);
        lc.addView(previewFrame, plp);
        preview.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showZoom(); } });

        pageNav = Ui.row(this);
        prevBtn = Ui.button(this, "‹ Prev", 3);
        nextBtn = Ui.button(this, "Next ›", 3);
        prevBtn.setContentDescription("Previous label");
        nextBtn.setContentDescription("Next label");
        pageInfo = Ui.text(this, "", 13, Ui.TEXT2, false);
        pageInfo.setGravity(Gravity.CENTER);
        pageNav.addView(prevBtn, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageNav.addView(pageInfo, Ui.weighted(this, 1, 4));
        pageNav.addView(nextBtn, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lc.addView(pageNav, Ui.matchWrap(this, 6));
        prevBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(pageIndex - 1); } });
        nextBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPage(pageIndex + 1); } });

        // size + copies row
        LinearLayout opts = Ui.row(this);
        sizeSpinner = new Spinner(this);
        String[] names = new String[LabelSize.ALL.length];
        for (int i = 0; i < names.length; i++) names[i] = LabelSize.ALL[i].name;
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sizeSpinner.setAdapter(ad);
        sizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (!sizeSpinnerReady) return;
                PrintSettings s = Prefs.load(MainActivity.this);
                if (!s.labelSizeId.equals(LabelSize.ALL[pos].id)) {
                    s.labelSizeId = LabelSize.ALL[pos].id;
                    Prefs.save(MainActivity.this, s);
                    if (!files.isEmpty()) render();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        opts.addView(sizeSpinner, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f));
        Button minus = Ui.button(this, "−", 2);
        Button plus = Ui.button(this, "+", 2);
        minus.setContentDescription("One fewer copy");
        plus.setContentDescription("One more copy");
        copiesText = Ui.text(this, "", 15, Ui.TEXT, true);
        copiesText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        mlp.leftMargin = Ui.dp(this, 10);
        opts.addView(minus, mlp);
        opts.addView(copiesText, new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        opts.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        minus.setPadding(0, 0, 0, 0);
        plus.setPadding(0, 0, 0, 0);
        lc.addView(opts, Ui.matchWrap(this, 12));
        minus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setCopies(copies - 1); } });
        plus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setCopies(copies + 1); } });

        printBtn = Ui.button(this, "Print", 1);
        lc.addView(printBtn, Ui.matchWrap(this, 14));
        printBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { print(); } });

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        lc.addView(progress, Ui.matchWrap(this, 10));
        statusText = Ui.text(this, "", 14, Ui.TEXT2, false);
        statusText.setVisibility(View.GONE);
        lc.addView(statusText, Ui.matchWrap(this, 8));

        openBtn = Ui.button(this, "Open PDF or image…", 2);
        lc.addView(openBtn, Ui.matchWrap(this, 12));
        openBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openDocument(); } });
        root.addView(lc, Ui.matchWrap(this, 14));

        // --- more
        LinearLayout mc = Ui.card(this);
        mc.addView(Ui.sectionTitle(this, "More"));
        TextView tip = Ui.text(this, "Print straight from any app’s Print menu (eBay, Chrome, Gmail): turn on “Fanfold” in Android’s printing settings, then pick it as the printer.", 14, Ui.TEXT2, false);
        mc.addView(tip, Ui.matchWrap(this, 8));
        LinearLayout mr = Ui.row(this);
        Button printSettings = Ui.button(this, "Printing settings", 3);
        Button settings = Ui.button(this, "App settings", 3);
        mr.addView(printSettings, Ui.weighted(this, 1, 2));
        mr.addView(settings, Ui.weighted(this, 1, 2));
        mc.addView(mr, Ui.matchWrap(this, 6));
        root.addView(mc, Ui.matchWrap(this, 14));
        printSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { safeStart(new Intent(Settings.ACTION_PRINT_SETTINGS)); }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });

        setContentView(scroll);
        setCopies(copies);
        updatePreview();
    }

    private void setCopies(int n) {
        copies = Math.max(1, Math.min(99, n));
        copiesText.setText(copies == 1 ? "1 copy" : copies + " copies");
    }

    private void refreshPrinterCard() {
        String name = Prefs.printerName(this), addr = Prefs.printerAddress(this);
        if (addr == null) {
            printerName.setText("No printer chosen");
            printerSub.setText("Pair your label printer in Bluetooth settings, then tap Choose printer.");
        } else {
            printerName.setText(name != null ? name : addr);
            PrintSettings ps = Prefs.load(this);
            printerSub.setText(Prefs.profile(this).family + "  \u2022  " + ps.dpi() + " dpi  \u2022  "
                    + PrintSettings.transferShort(ps.transferMode()) + " transfer");
        }
        boolean linked = addr != null && BtPrinter.isLinked(addr);
        if (addr == null) connState.setText("");
        else if (BtPrinter.hasPermission(this) && !BtPrinter.isEnabled(this)) { connState.setText("Bluetooth is off"); connState.setTextColor(Ui.ERR); }
        else if (linked) { connState.setText("\u25CF Connected"); connState.setTextColor(Ui.OK); }
        else { connState.setText("\u25CB Not connected (connects when you print)"); connState.setTextColor(Ui.TEXT2); }
        disconnectBtn.setVisibility(linked ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String msg, int color) {
        if (msg == null) { statusText.setVisibility(View.GONE); return; }
        statusText.setText(msg);
        statusText.setTextColor(color);
        statusText.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean b) {
        busy = b;
        printBtn.setEnabled(!b);
        printBtn.setAlpha(b ? 0.6f : 1f);
        if (!b) progress.setVisibility(View.GONE);
    }

    private void updatePreview() {
        boolean has = !pages.isEmpty();
        emptyText.setVisibility(has ? View.GONE : View.VISIBLE);
        emptyText.setText(loading ? "Loading label\u2026" : EMPTY_MSG);
        preview.setVisibility(has ? View.VISIBLE : View.GONE);
        pageNav.setVisibility(has ? View.VISIBLE : View.GONE);
        printBtn.setText(loading ? "Loading label\u2026" : pages.size() > 1 ? "Print " + pages.size() + " labels" : "Print");
        openBtn.setText(has ? "Open a different file…" : "Open PDF or image…");
        if (has) showPage(pageIndex);
    }

    private void showPage(int i) {
        if (pages.isEmpty()) return;
        pageIndex = Math.max(0, Math.min(pages.size() - 1, i));
        Mono m = pages.get(pageIndex);
        preview.setImageBitmap(LabelEngine.toBitmap(m));
        LabelSize size = Prefs.load(this).labelSize();
        String info = size.shortName() + (TextUtils.isEmpty(m.note) ? "" : " • " + m.note);
        if (pages.size() > 1) info = "Label " + (pageIndex + 1) + " of " + pages.size() + "\n" + info;
        pageInfo.setText(info);
        prevBtn.setVisibility(pages.size() > 1 ? View.VISIBLE : View.INVISIBLE);
        nextBtn.setVisibility(pages.size() > 1 ? View.VISIBLE : View.INVISIBLE);
        prevBtn.setEnabled(pageIndex > 0);
        nextBtn.setEnabled(pageIndex < pages.size() - 1);
    }

    private void showZoom() {
        if (pages.isEmpty()) return;
        ImageView big = new ImageView(this);
        big.setImageBitmap(LabelEngine.toBitmap(pages.get(pageIndex)));
        big.setAdjustViewBounds(true);
        big.setBackgroundColor(Color.WHITE);
        ScrollView sv = new ScrollView(this);
        sv.addView(big);
        new AlertDialog.Builder(this).setView(sv).setPositiveButton("Close", null).show();
    }

    // ------------------------------------------------------------------ input

    private void handleIntent(Intent in) {
        if (in == null) return;
        String action = in.getAction();
        ArrayList<Uri> uris = new ArrayList<Uri>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
            else if (in.getClipData() != null) addClip(in.getClipData(), uris);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else if (Intent.ACTION_VIEW.equals(action) && in.getData() != null) {
            uris.add(in.getData());
        }
        if (!uris.isEmpty()) {
            in.setAction(Intent.ACTION_MAIN);   // don't re-import if the activity is recreated
            importUris(uris, true);
        }
    }

    private static void addClip(ClipData clip, List<Uri> out) {
        for (int i = 0; i < clip.getItemCount(); i++) {
            Uri u = clip.getItemAt(i).getUri();
            if (u != null) out.add(u);
        }
    }

    private void openDocument() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_OPEN);
        } catch (Exception e) {
            setStatus("No file picker available on this phone.", Ui.ERR);
        }
    }

    /** True while a label is being opened/rendered; the preview and Print never show a previous label meanwhile. */
    private boolean loading = false;
    private static final String EMPTY_MSG = "No label loaded yet.\n\nShare a shipping label PDF to “Fanfold” from eBay, Gmail, Files or Chrome — or tap Open below.";

    private void importUris(final List<Uri> uris, final boolean fromShare) {
        // Drop the previous label right away so it can't be printed by mistake while the new one loads.
        renderGen++;
        files.clear();
        pages = new ArrayList<Mono>();
        loading = true;
        updatePreview();
        setStatus("Opening new label…", Ui.TEXT2);
        Jobs.render(new Runnable() {
            @Override public void run() {
                final ArrayList<File> imported = new ArrayList<File>();
                final ArrayList<String> errs = new ArrayList<String>();
                for (int i = 0; i < uris.size(); i++) {
                    try {
                        imported.add(LabelEngine.importUri(MainActivity.this, uris.get(i), i));
                    } catch (Exception e) {
                        errs.add(e.getMessage() != null ? e.getMessage() : e.toString());
                    }
                }
                Jobs.main(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        if (imported.isEmpty()) {
                            loading = false;
                            autoPrintPending = false;
                            updatePreview();
                            setStatus("Couldn't open that file." + (errs.isEmpty() ? "" : "\n" + errs.get(0)), Ui.ERR);
                            return;
                        }
                        files.clear();
                        files.addAll(imported);
                        pageIndex = 0;
                        autoPrintPending = autoPrintPending || (fromShare && Prefs.load(MainActivity.this).autoPrintShared);
                        render();
                    }
                });
            }
        });
    }

    private void render() {
        final int gen = ++renderGen;
        final PrintSettings s = Prefs.load(this);
        final ArrayList<File> fs = new ArrayList<File>(files);
        loading = true;
        updatePreview();
        setStatus("Preparing label…", Ui.TEXT2);
        Jobs.render(new Runnable() {
            @Override public void run() {
                final LabelEngine.Result r = LabelEngine.process(fs, s, s.labelSize());
                Jobs.main(new Runnable() {
                    @Override public void run() {
                        if (destroyed || gen != renderGen) return;
                        loading = false;
                        pages = r.pages;
                        renderedSignature = Prefs.renderSignature(s);
                        if (pageIndex >= pages.size()) pageIndex = 0;
                        updatePreview();
                        if (pages.isEmpty()) {
                            setStatus(!r.errors.isEmpty() ? r.errors.get(0) : "That file looks blank — nothing to print.", Ui.ERR);
                            autoPrintPending = false;
                            return;
                        }
                        String msg = null;
                        if (!r.errors.isEmpty()) msg = "Some files couldn't be read: " + r.errors.get(0);
                        else if (r.blankSkipped > 0) msg = r.blankSkipped + " blank page" + (r.blankSkipped > 1 ? "s" : "") + " skipped.";
                        setStatus(msg, Ui.TEXT2);
                        if (autoPrintPending) { autoPrintPending = false; print(); }
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ printer

    private void requestBtPermission(Runnable then) {
        afterPermission = then;
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{BtPrinter.PERM_CONNECT}, REQ_PERM);
    }

    /** Makes sure we have permission, Bluetooth is on, and a printer is chosen; then runs action. */
    private boolean ready(final Runnable action) {
        if (!BtPrinter.hasPermission(this)) { requestBtPermission(action); return false; }
        if (BtPrinter.adapter(this) == null) { setStatus("This phone has no Bluetooth.", Ui.ERR); return false; }
        if (!BtPrinter.isEnabled(this)) {
            afterPermission = action;
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT);
            } catch (Exception e) {
                setStatus("Turn on Bluetooth, then try again.", Ui.ERR);
            }
            return false;
        }
        if (Prefs.printerAddress(this) == null) { choosePrinterThen(action); return false; }
        return true;
    }

    private void choosePrinter() { choosePrinterThen(null); }

    private void choosePrinterThen(final Runnable then) {
        if (!BtPrinter.hasPermission(this)) { requestBtPermission(new Runnable() { @Override public void run() { choosePrinterThen(then); } }); return; }
        if (!BtPrinter.isEnabled(this)) {
            afterPermission = new Runnable() { @Override public void run() { choosePrinterThen(then); } };
            try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT); }
            catch (Exception e) { setStatus("Turn on Bluetooth, then try again.", Ui.ERR); }
            return;
        }
        final List<BluetoothDevice> devs = BtPrinter.bonded(this);
        if (devs.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No paired printers")
                    .setMessage("Turn the printer on, then pair it in Android’s Bluetooth settings. It usually shows up under its model name (for example “JD268BT”). If it asks for a PIN, try 0000 or 1234.\n\nThen come back and tap Choose printer again.")
                    .setPositiveButton("Open Bluetooth settings", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { safeStart(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        String[] items = new String[devs.size()];
        String current = Prefs.printerAddress(this);
        for (int i = 0; i < items.length; i++) {
            BluetoothDevice d = devs.get(i);
            String n = BtPrinter.safeName(d);
            items[i] = n + (d.getAddress().equals(current) ? "  ✓" : "") + (BtPrinter.looksLikePrinter(n) ? "" : "  (not a printer?)");
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose your printer")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        BluetoothDevice d = devs.get(which);
                        BtPrinter.disconnect();
                        Prefs.setPrinter(MainActivity.this, d.getAddress(), BtPrinter.safeName(d));
                        refreshPrinterCard();
                        setStatus("Printer set. Tap Test print to check it.", Ui.OK);
                        if (then != null) then.run();
                    }
                })
                .setNeutralButton("Pair new…", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { safeStart(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void print() {
        if (busy) return;
        if (loading) {
            // Never print whatever was on screen before; print the new label as soon as it's ready.
            autoPrintPending = true;
            setStatus("Loading the label \u2014 it will print as soon as it's ready.", Ui.TEXT2);
            return;
        }
        if (pages.isEmpty()) {
            setStatus("Open or share a label first.", Ui.ERR);
            return;
        }
        if (renderedSignature != null && !renderedSignature.equals(Prefs.renderSignature(Prefs.load(this)))) {
            // Settings (size/resolution) changed since the preview was made: re-render, then print.
            autoPrintPending = true;
            render();
            return;
        }
        if (!ready(new Runnable() { @Override public void run() { print(); } })) return;
        final PrintSettings s = Prefs.load(this);
        final LabelSize size = s.labelSize();
        final List<Mono> job = new ArrayList<Mono>(pages);
        final int n = copies;
        final int labelCount = job.size() * n;
        sendJob(new JobBuilder() {
            @Override public byte[] build() { return Tspl.job(s, size, job, n); }
        }, "Sent " + labelCount + " label" + (labelCount > 1 ? "s" : "") + " to the printer ✓", s.statusCheck);
    }

    private void testPrint() {
        if (busy) return;
        if (!ready(new Runnable() { @Override public void run() { testPrint(); } })) return;
        final PrintSettings s = Prefs.load(this);
        final LabelSize size = s.labelSize();
        final String name = Prefs.printerName(this);
        sendJob(new JobBuilder() {
            @Override public byte[] build() {
                List<Mono> one = new ArrayList<Mono>();
                one.add(LabelEngine.testLabel(size, s, name));
                return Tspl.job(s, size, one, 1);
            }
        }, "Test label sent \u2713", s.statusCheck, new Runnable() {
            @Override public void run() {
                // Give the label a moment to come out before asking about it.
                Jobs.MAIN.postDelayed(new Runnable() {
                    @Override public void run() { if (!destroyed) askHowTestLooked(); }
                }, 2500);
            }
        });
    }

    /** Guided setup: one question after a test print, and the matching fix. */
    private void askHowTestLooked() {
        final String[] answers = {
                "Looks good",
                "Blank, garbled or only part printed",
                "Solid black / white text on black",
                "Too big or too small",
                "Nothing came out"};
        new AlertDialog.Builder(this)
                .setTitle("How did the test label come out?")
                .setItems(answers, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { applyTestFeedback(which); }
                })
                .setNegativeButton("Skip", null)
                .show();
    }

    private void applyTestFeedback(int answer) {
        PrintSettings s = Prefs.load(this);
        String change = null;
        switch (answer) {
            case 0:
                setStatus("All set \u2014 your printer is ready.", Ui.OK);
                return;
            case 1: {   // step down to a more compatible transfer mode
                int mode = s.transferMode();
                if (mode == PrintSettings.TRANSFER_COMPRESSED) {
                    s.transfer = PrintSettings.TRANSFER_INK_ONLY;
                    change = "Switched to Standard transfer.";
                } else if (mode == PrintSettings.TRANSFER_INK_ONLY) {
                    s.transfer = PrintSettings.TRANSFER_FULL;
                    change = "Switched to Full image transfer.";
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Still not right")
                            .setMessage("Fanfold is already on its most compatible setting. Turn the printer off and on (it may be holding a half-received label) and try again. If it still prints garbage, this printer may use a different command language than TSPL \u2014 please open a printer report on the Fanfold GitHub page.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                break;
            }
            case 2:
                s.invert = !s.invert;
                change = s.invert ? "Turned on Invert." : "Turned off Invert.";
                break;
            case 3:
                s.dpiSetting = s.dpi() == 300 ? 203 : 300;
                change = "Switched to " + s.dpiSetting + " dpi.";
                break;
            default:
                new AlertDialog.Builder(this)
                        .setTitle("Nothing printed")
                        .setMessage("Check the printer is on, has labels loaded and the lid is closed, then turn it off and on and tap Test print again. If it never prints, try Settings \u2192 Transfer mode \u2192 Standard.")
                        .setPositiveButton("OK", null).show();
                return;
        }
        Prefs.save(this, s);
        refreshPrinterCard();
        if (!files.isEmpty() && !Prefs.renderSignature(Prefs.load(this)).equals(renderedSignature)) render();
        final String msg = change;
        new AlertDialog.Builder(this)
                .setTitle(msg)
                .setMessage("Print another test label to check?")
                .setPositiveButton("Print test", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { testPrint(); }
                })
                .setNegativeButton("Later", null).show();
    }

    private interface JobBuilder { byte[] build(); }

    private void sendJob(final JobBuilder builder, final String okMsg, final boolean statusCheck) {
        sendJob(builder, okMsg, statusCheck, null);
    }

    private void sendJob(final JobBuilder builder, final String okMsg, final boolean statusCheck, final Runnable onSuccess) {
        setBusy(true);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        setStatus("Getting ready…", Ui.TEXT2);
        final String addr = Prefs.printerAddress(this), name = Prefs.printerName(this);
        Jobs.printer(new Runnable() {
            @Override public void run() {
                String result;
                boolean ok;
                try {
                    byte[] data = builder.build();
                    BtPrinter.send(getApplicationContext(), addr, name, data, statusCheck, progressSink(), new Runnable() {
                        @Override public void run() {
                            Jobs.main(new Runnable() {
                                @Override public void run() {
                                    if (destroyed) return;
                                    setBusy(false);
                                    setStatus(okMsg, Ui.OK);
                                }
                            });
                        }
                    });
                    result = okMsg;
                    ok = true;
                } catch (BtPrinter.PrinterException e) {
                    result = e.getMessage();
                    ok = false;
                } catch (Exception e) {
                    result = "Something went wrong: " + e;
                    ok = false;
                }
                final String fr = result;
                final boolean fok = ok;
                Jobs.main(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        setBusy(false);
                        setStatus(fr, fok ? Ui.OK : Ui.ERR);
                        refreshPrinterCard();
                        if (fok && onSuccess != null) onSuccess.run();
                    }
                });
            }
        });
    }

    private BtPrinter.Progress progressSink() {
        return new BtPrinter.Progress() {
            @Override public void update(final String message, final int percent) {
                Jobs.main(new Runnable() {
                    @Override public void run() {
                        if (destroyed || !busy) return;
                        setStatus(message, Ui.TEXT2);
                        if (percent < 0) progress.setIndeterminate(true);
                        else { progress.setIndeterminate(false); progress.setProgress(percent); }
                    }
                });
            }
        };
    }

    private void checkPrinter() {
        if (busy) return;
        if (!ready(new Runnable() { @Override public void run() { checkPrinter(); } })) return;
        setBusy(true);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        setStatus("Connecting…", Ui.TEXT2);
        final String addr = Prefs.printerAddress(this), name = Prefs.printerName(this);
        Jobs.printer(new Runnable() {
            @Override public void run() {
                String msg;
                boolean ok;
                try {
                    msg = BtPrinter.status(getApplicationContext(), addr, name);
                    ok = true;
                } catch (BtPrinter.PrinterException e) {
                    msg = e.getMessage();
                    ok = false;
                }
                final String fm = msg;
                final boolean fok = ok;
                Jobs.main(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        setBusy(false);
                        setStatus(fm, fok ? Ui.OK : Ui.ERR);
                        refreshPrinterCard();
                    }
                });
            }
        });
    }

    private void safeStart(Intent i) {
        try { startActivity(i); } catch (Exception e) { setStatus("Couldn't open that settings screen on this phone.", Ui.ERR); }
    }

    // ------------------------------------------------------------------ results

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN && res == RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<Uri>();
            if (data.getClipData() != null) addClip(data.getClipData(), uris);
            else if (data.getData() != null) uris.add(data.getData());
            if (!uris.isEmpty()) importUris(uris, false);
        } else if (req == REQ_ENABLE_BT) {
            refreshPrinterCard();
            Runnable r = afterPermission;
            afterPermission = null;
            if (res == RESULT_OK && r != null) r.run();
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req != REQ_PERM) return;
        refreshPrinterCard();
        Runnable r = afterPermission;
        afterPermission = null;
        if (BtPrinter.hasPermission(this)) {
            if (r != null) r.run();
        } else {
            setStatus("Fanfold needs the “Nearby devices” permission to talk to the printer over Bluetooth.", Ui.ERR);
        }
    }
}
