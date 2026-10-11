package com.fanfold.app;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.print.PrintAttributes;
import android.print.PrintJobId;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;

import com.fanfold.core.LabelSize;
import com.fanfold.core.PrintSettings;
import com.fanfold.core.Tspl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes the printer show up in Android's system Print dialog, so any app
 * (eBay, Chrome, Gmail, a PDF viewer) can print labels to it.
 */
public class LabelPrintService extends PrintService {
    /** One stable printer id, so the Print dialog remembers "Fanfold" even when you switch or re-pick printers. */
    private static final String PRINTER_ID = "fanfold";
    private static final int SEND_ATTEMPTS = 3;
    private static final long RETRY_WAIT_MS = 6000;
    private final Set<PrintJobId> cancelled = Collections.newSetFromMap(new ConcurrentHashMap<PrintJobId, Boolean>());

    @Override protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new Session();
    }

    @Override protected void onRequestCancelPrintJob(PrintJob job) {
        cancelled.add(job.getId());
        job.cancel();
    }

    @Override protected void onPrintJobQueued(final PrintJob job) {
        if (job.isCancelled()) return;
        final PrintJobInfo info = job.getInfo();
        final String address = Prefs.printerAddress(this);
        final String name = Prefs.printerName(this);
        final PrintSettings settings = Prefs.load(this);
        PrintAttributes.MediaSize media = info.getAttributes() != null ? info.getAttributes().getMediaSize() : null;
        final LabelSize size = media != null ? LabelSize.byId(media.getId()).at(settings.dpi()) : settings.labelSize();
        final int copies = Math.max(1, info.getCopies());
        final ParcelFileDescriptor data = job.getDocument().getData();
        job.start();
        if (data == null) { job.fail("No document to print."); return; }
        final PrintJobId id = job.getId();

        Jobs.printer(new Runnable() {
            @Override public void run() {
                File tmp = new File(getCacheDir(), "printjob_" + System.currentTimeMillis() + ".pdf");
                String error = null;
                try {
                    FileInputStream in = new FileInputStream(data.getFileDescriptor());
                    FileOutputStream out = new FileOutputStream(tmp);
                    try {
                        LabelEngine.copy(in, out);
                    } finally {
                        out.close();
                        try { in.close(); } catch (IOException ignored) { }
                        try { data.close(); } catch (IOException ignored) { }
                    }
                    if (cancelled.contains(id)) return;
                    List<File> files = new ArrayList<File>();
                    files.add(tmp);
                    LabelEngine.Result r = LabelEngine.process(files, settings, size);
                    if (r.pages.isEmpty()) {
                        error = !r.errors.isEmpty() ? r.errors.get(0) : "The document is blank.";
                    } else if (!cancelled.contains(id)) {
                        byte[] bytes = Tspl.job(settings, size, r.pages, copies);
                        for (int attempt = 1; ; attempt++) {
                            try {
                                BtPrinter.send(getApplicationContext(), address, name, bytes, settings.statusCheck, null, new Runnable() {
                                    @Override public void run() { finish(job, id, null); }
                                });
                                break;
                            } catch (BtPrinter.PrinterException e) {
                                // Printer asleep/off/busy: show "blocked" in the print queue and try again
                                // instead of failing straight away.
                                if (!e.connectFailed || attempt >= SEND_ATTEMPTS || cancelled.contains(id)) throw e;
                                setBlocked(job, "Can't reach the printer \u2014 is it on? Retrying\u2026");
                                SystemClock.sleep(RETRY_WAIT_MS);
                                if (cancelled.contains(id)) return;
                                setStarted(job);
                            }
                        }
                    }
                } catch (BtPrinter.PrinterException e) {
                    error = e.getMessage().replace("\n\n", " ").replace("\n", " ");
                } catch (Exception e) {
                    error = "Print failed: " + e.getMessage();
                } finally {
                    tmp.delete();
                }
                finish(job, id, error);
            }
        });
    }

    private static void setBlocked(final PrintJob job, final String why) {
        Jobs.main(new Runnable() { @Override public void run() { if (!job.isCancelled()) job.block(why); } });
    }

    private static void setStarted(final PrintJob job) {
        Jobs.main(new Runnable() { @Override public void run() { if (job.isBlocked()) job.start(); } });
    }

    private final Set<PrintJobId> finished = Collections.newSetFromMap(new ConcurrentHashMap<PrintJobId, Boolean>());

    /** Completes or fails the spooler job exactly once. */
    private void finish(final PrintJob job, final PrintJobId id, final String err) {
        if (!finished.add(id)) return;
        Jobs.main(new Runnable() {
            @Override public void run() {
                if (cancelled.remove(id) || job.isCancelled()) return;
                if (err == null) job.complete();
                else job.fail(err);
            }
        });
    }

    // ------------------------------------------------------------------ discovery

    private PrinterInfo printerInfo(boolean withCaps) {
        String addr = Prefs.printerAddress(this);
        PrinterId id = generatePrinterId(PRINTER_ID);
        if (addr == null) {
            return new PrinterInfo.Builder(id, "Fanfold", PrinterInfo.STATUS_UNAVAILABLE)
                    .setDescription("Open the Fanfold app and choose your printer").build();
        }
        String name = Prefs.printerName(this);
        PrinterInfo.Builder b = new PrinterInfo.Builder(id, "Fanfold", PrinterInfo.STATUS_IDLE)
                .setDescription(name != null ? name : addr);
        if (withCaps) b.setCapabilities(capabilities(id));
        return b.build();
    }

    private PrinterCapabilitiesInfo capabilities(PrinterId id) {
        String def = Prefs.load(this).labelSize().id;
        PrinterCapabilitiesInfo.Builder c = new PrinterCapabilitiesInfo.Builder(id);
        for (LabelSize s : LabelSize.ALL) {
            c.addMediaSize(new PrintAttributes.MediaSize(s.id, s.name, s.widthMils, s.heightMils), s.id.equals(def));
        }
        int dpi = Prefs.load(this).dpi();
        c.addResolution(new PrintAttributes.Resolution("r" + dpi, dpi + " dpi", dpi, dpi), true);
        c.setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME);
        c.setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0));
        return c.build();
    }

    private final class Session extends PrinterDiscoverySession {
        @Override public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
            BtPrinter.warmUp(LabelPrintService.this);
            PrinterInfo current = printerInfo(true);
            List<PrinterId> stale = new ArrayList<PrinterId>();
            for (PrinterInfo p : getPrinters()) if (!p.getId().equals(current.getId())) stale.add(p.getId());
            if (!stale.isEmpty()) removePrinters(stale);
            List<PrinterInfo> list = new ArrayList<PrinterInfo>();
            list.add(current);
            addPrinters(list);
        }

        @Override public void onStopPrinterDiscovery() { }

        @Override public void onValidatePrinters(List<PrinterId> printerIds) {
            List<PrinterInfo> list = new ArrayList<PrinterInfo>();
            list.add(printerInfo(true));
            addPrinters(list);
        }

        @Override public void onStartPrinterStateTracking(PrinterId printerId) {
            BtPrinter.warmUp(LabelPrintService.this);   // connect while the user is still looking at the preview
            List<PrinterInfo> list = new ArrayList<PrinterInfo>();
            list.add(printerInfo(true));
            addPrinters(list);
        }

        @Override public void onStopPrinterStateTracking(PrinterId printerId) { }

        @Override public void onDestroy() { }
    }
}
