package com.fanfold.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import com.fanfold.core.Tspl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Classic Bluetooth (SPP / RFCOMM) link to the printer. Every job opens a fresh
 * connection, sends, waits until the printer has everything, and closes — no
 * long-lived connection to go stale.
 */
final class BtPrinter {
    private BtPrinter() {}

    static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static final String PERM_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private static final Object LOCK = new Object();
    private static final byte[] CRLF = {'\r', '\n'};

    interface Progress { void update(String message, int percent); }

    static final class PrinterException extends Exception {
        /** True when we never got a connection (printer off, asleep, busy) — worth retrying later. */
        final boolean connectFailed;
        PrinterException(String msg) { this(msg, false); }
        PrinterException(String msg, boolean connectFailed) { super(msg); this.connectFailed = connectFailed; }
    }

    static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(PERM_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    static BluetoothAdapter adapter(Context c) {
        BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter a = bm != null ? bm.getAdapter() : null;
        return a != null ? a : BluetoothAdapter.getDefaultAdapter();
    }

    static boolean isEnabled(Context c) {
        BluetoothAdapter a = adapter(c);
        try { return a != null && a.isEnabled(); } catch (SecurityException e) { return false; }
    }

    static boolean looksLikePrinter(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        return n.contains("jd") || n.contains("jaden") || n.contains("268") || n.contains("426") || n.contains("beeprt")
                || n.contains("print") || n.contains("label") || n.contains("by-") || n.contains("rollo");
    }

    static String safeName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n != null && n.length() > 0 ? n : d.getAddress();
        } catch (SecurityException e) {
            return d.getAddress();
        }
    }

    /** Paired devices, likely printers first. */
    static List<BluetoothDevice> bonded(Context c) {
        List<BluetoothDevice> out = new ArrayList<BluetoothDevice>();
        BluetoothAdapter a = adapter(c);
        if (a == null || !hasPermission(c)) return out;
        try {
            Set<BluetoothDevice> set = a.getBondedDevices();
            if (set != null) out.addAll(set);
        } catch (SecurityException ignored) {
        }
        Collections.sort(out, new Comparator<BluetoothDevice>() {
            @Override public int compare(BluetoothDevice x, BluetoothDevice y) {
                boolean px = looksLikePrinter(safeName(x)), py = looksLikePrinter(safeName(y));
                if (px != py) return px ? -1 : 1;
                return safeName(x).compareToIgnoreCase(safeName(y));
            }
        });
        return out;
    }

    private static BluetoothDevice device(Context c, String address) throws PrinterException {
        BluetoothAdapter a = adapter(c);
        if (a == null) throw new PrinterException("This phone doesn't have Bluetooth.");
        if (!hasPermission(c)) throw new PrinterException("Bluetooth permission is off. Open Fanfold and allow \"Nearby devices\".");
        if (!isEnabled(c)) throw new PrinterException("Bluetooth is turned off. Turn it on and try again.");
        if (address == null) throw new PrinterException("No printer chosen yet. Open Fanfold and tap \"Choose printer\".");
        try {
            return a.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            throw new PrinterException("Saved printer address is invalid. Choose the printer again.");
        }
    }

    static final String[] METHOD_NAMES = {"standard", "no-auth", "direct channel 1"};

    private static BluetoothSocket open(BluetoothDevice d, int kind) throws Exception {
        if (kind == 0) return d.createRfcommSocketToServiceRecord(SPP);
        if (kind == 1) return d.createInsecureRfcommSocketToServiceRecord(SPP);
        Method m = d.getClass().getMethod("createRfcommSocket", int.class);   // skips the service lookup
        return (BluetoothSocket) m.invoke(d, 1);
    }

    /**
     * Opens the link. Remembers which of the three connection styles worked for this printer and
     * tries that one first next time (a style that doesn't suit the printer can hang ~10 s before
     * failing). Retries a few rounds because these printers doze when idle. Records timing so the
     * app can show it.
     */
    private static BluetoothSocket connect(Context c, BluetoothDevice d, Progress p) throws IOException {
        return connect(c, d, p, false);
    }

    /** quick = one pass only (used for background warm-ups so they never hold up a real print for long). */
    private static BluetoothSocket connect(Context c, BluetoothDevice d, Progress p, boolean quick) throws IOException {
        if (Build.VERSION.SDK_INT < 31) {
            try { adapter(c).cancelDiscovery(); } catch (SecurityException ignored) { }
        }
        String addr = d.getAddress();
        int preferred = Prefs.sp(c).getInt("connMethod_" + addr, -1);
        int[] order = preferred == 1 ? new int[]{1, 2, 0} : preferred == 2 ? new int[]{2, 1, 0} : new int[]{0, 1, 2};
        long start = SystemClock.elapsedRealtime();
        long budget = quick ? 12000 : 40000;
        long[] pauses = quick ? new long[]{0} : new long[]{0, 700, 1500, 3000};
        IOException last = null;
        int tries = 0;
        for (int round = 0; round < pauses.length; round++) {
            if (round > 0) {
                if (SystemClock.elapsedRealtime() - start > budget) break;
                if (p != null) p.update("Waking the printer\u2026 (round " + (round + 1) + ")", -1);
                SystemClock.sleep(pauses[round]);
            }
            for (int kind : order) {
                if (SystemClock.elapsedRealtime() - start > budget) break;
                BluetoothSocket s = null;
                long t0 = SystemClock.elapsedRealtime();
                tries++;
                try {
                    s = open(d, kind);
                    s.connect();
                    long took = SystemClock.elapsedRealtime() - start;
                    Prefs.sp(c).edit()
                            .putInt("connMethod_" + addr, kind)
                            .putString("lastConnect_" + addr, String.format(java.util.Locale.US,
                                    "Last connect: %.1f s (%s, try %d)", took / 1000.0, METHOD_NAMES[kind], tries))
                            .apply();
                    return s;
                } catch (IOException e) {
                    last = new IOException(METHOD_NAMES[kind] + " failed after "
                            + (SystemClock.elapsedRealtime() - t0) / 100 / 10.0 + " s: " + e.getMessage());
                    closeQuietly(s);
                    SystemClock.sleep(150);
                } catch (SecurityException e) {
                    closeQuietly(s);
                    throw new IOException("Bluetooth permission denied", e);
                } catch (Exception e) {   // hidden-API fallback not available on this phone
                    closeQuietly(s);
                    if (last == null) last = new IOException(e.toString());
                }
            }
        }
        Prefs.sp(c).edit().putString("lastConnect_" + addr, String.format(java.util.Locale.US,
                "Last connect FAILED after %.0f s", (SystemClock.elapsedRealtime() - start) / 1000.0)).apply();
        throw last != null ? last : new IOException("connect failed");
    }

    static String lastConnectInfo(Context c, String address) {
        return address == null ? null : Prefs.sp(c).getString("lastConnect_" + address, null);
    }

    private static void closeQuietly(BluetoothSocket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) { }
    }

    /** Sends the status query and returns the single status byte, -1 for no reply, -2 for a non-status reply. */
    private static int queryStatus(InputStream in, OutputStream out, long timeoutMs) throws IOException {
        while (in.available() > 0) in.read();   // discard anything stale
        out.write(Tspl.STATUS_QUERY);
        out.write(CRLF);   // ends the line in case the printer doesn't treat it as an immediate command
        out.flush();
        long end = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < end) {
            if (in.available() > 0) {
                int first = in.read();
                SystemClock.sleep(120);
                int extra = 0;
                while (in.available() > 0) { in.read(); extra++; }
                return extra == 0 ? first : -2;
            }
            SystemClock.sleep(25);
        }
        return -1;
    }

    // Does this printer answer the status query? Learned on first use so we never wait on silence twice.
    private static final int REPLY_UNKNOWN = 0, REPLY_YES = 1, REPLY_NO = 2;

    private static int replyMode(Context c, String address) {
        return Prefs.sp(c).getInt("statusReply_" + address, REPLY_UNKNOWN);
    }

    private static void setReplyMode(Context c, String address, int mode) {
        Prefs.sp(c).edit().putInt("statusReply_" + address, mode).apply();
    }

    // ------------------------------------------------------------------ kept-open link
    //
    // Closing the Bluetooth socket right after writing throws away whatever the phone hasn't
    // finished transmitting yet, and this printer doesn't say when it has everything. A cut-off
    // job leaves the printer holding half an image, which then gets glued onto the next label.
    // So the link stays open after a print (also making back-to-back prints instant) and is only
    // closed after it has been idle long enough for everything to have arrived.

    // How long the link stays open after the last print (Settings > Stay connected). Read without
    // the lock so the UI never waits behind a connect in progress.
    private static volatile long keepOpenMs = 10 * 60 * 1000L;
    private static volatile BluetoothSocket link;          // written only under LOCK
    private static volatile String linkAddress;
    private static volatile long linkIdleSince;

    static void configure(Context c) {
        keepOpenMs = Prefs.keepAliveMinutes(c) * 60 * 1000L;
    }

    private static BluetoothSocket reusableLink(String address) {
        if (link == null) return null;
        if (!address.equals(linkAddress) || SystemClock.elapsedRealtime() - linkIdleSince > keepOpenMs) {
            dropLink();
            return null;
        }
        return link;
    }

    private static void dropLink() {
        closeQuietly(link);
        link = null;
        linkAddress = null;
    }

    private static void keepLink(BluetoothSocket s, String address) {
        link = s;
        linkAddress = address;
        linkIdleSince = SystemClock.elapsedRealtime();
        final long stamp = linkIdleSince;
        Jobs.MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                Jobs.printer(new Runnable() {
                    @Override public void run() {
                        synchronized (LOCK) { if (link != null && linkIdleSince == stamp) dropLink(); }
                    }
                });
            }
        }, keepOpenMs + 1000);
    }

    /**
     * Connect in the background ahead of time (app opened, label shared in, Print dialog showing
     * Fanfold) so that tapping Print sends immediately. Does nothing if already connected except
     * keep the link alive; failures are silent — the real print will try properly.
     */
    static void warmUp(final Context ctx) { warmUp(ctx, null); }

    /** @param onDone runs on the main thread afterwards (connected or not), e.g. to refresh the UI. */
    static void warmUp(final Context ctx, final Runnable onDone) {
        final Context c = ctx.getApplicationContext();
        final String address = Prefs.printerAddress(c);
        if (address == null || !hasPermission(c) || !isEnabled(c)) return;
        configure(c);
        Jobs.printer(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) {
                    BluetoothSocket s = reusableLink(address);
                    if (s == null) {
                        try {
                            s = connect(c, adapter(c).getRemoteDevice(address), null, true);
                        } catch (Exception e) {
                            s = null;
                        }
                    }
                    if (s != null) keepLink(s, address);
                }
                if (onDone != null) Jobs.main(onDone);
            }
        });
    }

    /** Hang up now (e.g. when a different printer is chosen). Safe to call from any thread. */
    static void disconnect() { disconnect(null); }

    static void disconnect(final Runnable onDone) {
        Jobs.printer(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) { dropLink(); }
                if (onDone != null) Jobs.main(onDone);
            }
        });
    }

    /** Lock-free on purpose: safe to call from the UI thread even while a connect is in progress. */
    static boolean isLinked(String address) {
        String a = linkAddress;
        return link != null && address != null && address.equals(a)
                && SystemClock.elapsedRealtime() - linkIdleSince <= keepOpenMs;
    }

    /*
     * Cut-off recovery. If a job dies partway through an image, the printer keeps waiting for the
     * rest of that image and would swallow the start of the next job into it. So when (and only
     * when) we know bytes of a job went out without the job finishing, the next job is preceded by
     * a run of empty lines: enough bytes to complete any half-received image (the next CLS wipes
     * whatever they draw), and if the printer was *not* mid-image they are just blank command
     * lines, which TSPL ignores. Never one giant junk line.
     */
    private static final int FLUSH_MAX = 32768;

    private static void writeFlush(Context c, String address, OutputStream out) throws IOException {
        int n = Prefs.sp(c).getInt("lastJobLen_" + address, 16384);
        n = Math.max(2048, Math.min(FLUSH_MAX, n + 64));
        byte[] pad = new byte[n & ~1];
        for (int i = 0; i < pad.length; i += 2) { pad[i] = '\r'; pad[i + 1] = '\n'; }
        out.write(pad);
        out.flush();
    }

    private static void setDirty(Context c, String address, boolean dirty) {
        Prefs.sp(c).edit().putBoolean("dirty_" + address, dirty).commit();
    }

    private static BluetoothSocket openLink(Context c, BluetoothDevice d, String name, Progress p) throws PrinterException {
        if (p != null) p.update("Connecting to " + name + "\u2026", -1);
        try {
            return connect(c, d, p);
        } catch (IOException e) {
            throw new PrinterException("Couldn't connect to " + name + ".\n\n"
                    + "\u2022 Make sure the printer is on and nearby.\n"
                    + "\u2022 Close the printer maker's app (the printer only talks to one app/phone at a time).\n"
                    + "\u2022 If it still fails, turn the printer off and on.", true);
        }
    }

    /**
     * Writes the job; sentHolder tracks how many bytes went out. The "might be holding half a
     * label" flag is raised only once the first bytes have actually been accepted.
     */
    private static int writeJob(Context c, String address, OutputStream out, byte[] job, Progress p, int[] sentHolder)
            throws IOException {
        int chunk = 4096, sent = 0, lastPct = -1;
        sentHolder[0] = 0;
        while (sent < job.length) {
            int n = Math.min(chunk, job.length - sent);
            out.write(job, sent, n);
            if (sent == 0) setDirty(c, address, true);
            sent += n;
            sentHolder[0] = sent;
            int pct = (int) (sent * 100L / job.length);
            if (p != null && pct != lastPct && (pct % 5 == 0 || sent == job.length)) {
                p.update("Sending to printer\u2026", pct);
                lastPct = pct;
            }
        }
        out.flush();
        return sent;
    }

    static String send(Context c, String address, String printerName, byte[] job, boolean statusCheck, Progress p)
            throws PrinterException {
        return send(c, address, printerName, job, statusCheck, p, null);
    }

    /**
     * Sends a job over the kept-open link (connecting if needed). Blocking — background thread only.
     * @param onDelivered runs once the job has been handed to Bluetooth.
     */
    static String send(Context c, String address, String printerName, byte[] job, boolean statusCheck, Progress p,
                       Runnable onDelivered) throws PrinterException {
        configure(c);
        synchronized (LOCK) {
            BluetoothDevice d = device(c, address);
            String name = printerName != null ? printerName : safeName(d);
            BluetoothSocket s = reusableLink(address);
            boolean reused = s != null;
            if (!reused) {
                s = openLink(c, d, name, p);
                maybeProbeModel(c, address, s);
            }
            keepLink(s, address);
            boolean dirty = Prefs.sp(c).getBoolean("dirty_" + address, false);
            int[] sent = new int[1];
            long t0 = SystemClock.elapsedRealtime();
            try {
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                int mode = replyMode(c, address);
                if (statusCheck && mode != REPLY_NO) {
                    if (p != null) p.update("Checking printer\u2026", -1);
                    int st = queryStatus(in, out, mode == REPLY_YES ? 1500 : 1000);
                    if (st == -1) {
                        if (mode == REPLY_UNKNOWN) setReplyMode(c, address, REPLY_NO);
                    } else {
                        if (mode != REPLY_YES) setReplyMode(c, address, REPLY_YES);
                        String problem = st >= 0 ? Tspl.blockingProblem(st) : null;
                        if (problem != null) {
                            throw new PrinterException("Printer says: " + problem + ".\n\n"
                                    + "Fix it and print again. (If this is wrong, turn off \"Check paper & cover before printing\" in Settings.)");
                        }
                    }
                }
                if (dirty) writeFlush(c, address, out);
                writeJob(c, address, out, job, p, sent);
            } catch (IOException e) {
                dropLink();
                if (!(reused && sent[0] == 0)) throw dropped(c, address, sent[0]);
                // The kept-open link had gone stale (printer turned off/on, slept, out of range):
                // nothing of this job went out, so reconnect once and send it fresh.
                s = openLink(c, d, name, p);
                keepLink(s, address);
                try {
                    t0 = SystemClock.elapsedRealtime();
                    OutputStream out2 = s.getOutputStream();
                    if (dirty) writeFlush(c, address, out2);
                    writeJob(c, address, out2, job, p, sent);
                    reused = false;
                } catch (IOException e2) {
                    dropLink();
                    throw dropped(c, address, sent[0]);
                }
            }
            long took = SystemClock.elapsedRealtime() - t0;
            setDirty(c, address, false);
            Prefs.sp(c).edit()
                    .putInt("lastJobLen_" + address, job.length)
                    .putString("lastSend_" + address, String.format(java.util.Locale.US,
                            "Last print: %.0f KB handed off in %.1f s (%s%s)", job.length / 1024.0, took / 1000.0,
                            reused ? "already connected" : "new connection",
                            dirty ? ", cleared a half-received label first" : ""))
                    .apply();
            keepLink(s, address);
            if (onDelivered != null) onDelivered.run();
            return "Sent to " + name;
        }
    }

    private static PrinterException dropped(Context c, String address, int sent) {
        // If nothing went out, the printer is clean; otherwise it may hold part of an image.
        if (sent > 0) setDirty(c, address, true);   // only if part of this job really went out
        return new PrinterException("The Bluetooth connection dropped while sending. Tap Print again.\n\n"
                + "If that label comes out scrambled, turn the printer off and on once, then print again.", sent == 0);
    }

    /**
     * For printers we don't recognise by Bluetooth name, ask once for the model ("~!T" in TSPL).
     * Many clones never answer; that's fine — we just remember that we asked.
     */
    private static void maybeProbeModel(Context c, String address, BluetoothSocket s) {
        android.content.SharedPreferences sp = Prefs.sp(c);
        if (sp.getBoolean("modelProbed_" + address, false)) return;
        sp.edit().putBoolean("modelProbed_" + address, true).apply();
        if (com.fanfold.core.PrinterProfile.match(Prefs.printerName(c), null).known) return;
        try {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            while (in.available() > 0) in.read();
            out.write(new byte[]{'~', '!', 'T', '\r', '\n'});
            out.flush();
            StringBuilder sb = new StringBuilder();
            long end = SystemClock.uptimeMillis() + 900;
            while (SystemClock.uptimeMillis() < end && sb.length() < 64) {
                if (in.available() > 0) {
                    int b = in.read();
                    if (b == '\r' || b == '\n' || b == 0) { if (sb.length() > 0) break; else continue; }
                    if (b < 0x20 || b > 0x7E) { sb.setLength(0); break; }   // not text: not a model reply
                    sb.append((char) b);
                    end = Math.max(end, SystemClock.uptimeMillis() + 150);
                } else {
                    SystemClock.sleep(20);
                }
            }
            String model = sb.toString().trim();
            if (model.length() >= 2) sp.edit().putString("model_" + address, model).apply();
        } catch (IOException ignored) {
        }
    }

    /** Plain-text summary for printer reports / bug reports ("Copy printer info"). */
    static String diagnostics(Context c) {
        android.content.SharedPreferences sp = Prefs.sp(c);
        String addr = Prefs.printerAddress(c);
        com.fanfold.core.PrintSettings s = Prefs.load(c);
        StringBuilder b = new StringBuilder();
        String ver = "?";
        try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        b.append("Fanfold ").append(ver).append('\n');
        b.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        if (addr == null) return b.append("No printer chosen\n").toString();
        b.append("Printer Bluetooth name: ").append(Prefs.printerName(c)).append('\n');
        String model = sp.getString("model_" + addr, null);
        b.append("Reported model: ").append(model != null ? model : (sp.getBoolean("modelProbed_" + addr, false) ? "(no reply)" : "(not asked)")).append('\n');
        b.append("Detected family: ").append(Prefs.profile(c).family).append('\n');
        b.append("Resolution: ").append(s.dpi()).append(" dpi").append(s.dpiSetting == 0 ? " (auto)" : "").append('\n');
        b.append("Transfer: ").append(com.fanfold.core.PrintSettings.transferShort(s.transferMode()))
                .append(s.transfer < 0 ? " (auto)" : "").append('\n');
        int reply = sp.getInt("statusReply_" + addr, REPLY_UNKNOWN);
        b.append("Answers status queries: ").append(reply == REPLY_YES ? "yes" : reply == REPLY_NO ? "no" : "unknown").append('\n');
        int method = sp.getInt("connMethod_" + addr, -1);
        b.append("Connection style: ").append(method >= 0 && method < METHOD_NAMES.length ? METHOD_NAMES[method] : "unknown").append('\n');
        String lc = lastConnectInfo(c, addr), ls = lastSendInfo(c, addr);
        if (lc != null) b.append(lc).append('\n');
        if (ls != null) b.append(ls).append('\n');
        b.append("Density ").append(s.density).append(", speed ").append(com.fanfold.core.PrintSettings.SPEED_NAMES[Math.max(0, Math.min(5, s.speedIndex))])
                .append(", gap ").append(com.fanfold.core.PrintSettings.GAP_NAMES[Math.max(0, Math.min(3, s.gapIndex))]).append('\n');
        return b.toString();
    }

    static String lastSendInfo(Context c, String address) {
        return address == null ? null : Prefs.sp(c).getString("lastSend_" + address, null);
    }

    /** Connects (or reuses the open link) and asks the printer for its status. Leaves the link open. */
    static String status(Context c, String address, String printerName) throws PrinterException {
        synchronized (LOCK) {
            BluetoothDevice d = device(c, address);
            String name = printerName != null ? printerName : safeName(d);
            BluetoothSocket s = reusableLink(address);
            if (s == null) {
                s = openLink(c, d, name, null);
                maybeProbeModel(c, address, s);
            }
            keepLink(s, address);
            try {
                int st = queryStatus(s.getInputStream(), s.getOutputStream(), 2000);
                if (st == -1) return "Connected to " + name + " \u2713 (it doesn't report paper status)";
                if (st == -2) return "Connected to " + name + " \u2713";
                return "Connected to " + name + " \u2713  Status: " + Tspl.describeStatus(st);
            } catch (IOException e) {
                dropLink();
                throw new PrinterException("Connected, but the link dropped. Try again.", true);
            }
        }
    }
}
