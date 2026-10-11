package com.fanfold.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What we know about printer families, matched by Bluetooth name (and the model string the
 * printer reports, if it answers). Drives the "Auto" choices for resolution and transfer mode.
 * Only the JADENS / Beeprt family is confirmed to accept compressed bitmaps; everyone else
 * starts on the standard (uncompressed) mode, which any TSPL printer understands.
 */
public final class PrinterProfile {
    public final String family;
    public final int dpi;
    public final int transfer;
    public final boolean known;

    private PrinterProfile(String family, int dpi, int transfer, boolean known) {
        this.family = family; this.dpi = dpi; this.transfer = transfer; this.known = known;
    }

    private static final class Rule {
        final Pattern p; final PrinterProfile profile;
        Rule(String regex, String family, int dpi, int transfer) {
            p = Pattern.compile(regex);
            profile = new PrinterProfile(family, dpi, transfer, true);
        }
    }

    private static final int C = PrintSettings.TRANSFER_COMPRESSED, S = PrintSettings.TRANSFER_INK_ONLY;

    // Order matters: first match wins. Patterns run on the upper-cased "name model" string.
    private static final Rule[] RULES = {
            new Rule("JADENS|BEEPRT|(^|[^A-Z])JD[-_ ]?\\d|(^|[^0-9])(168|268|468|426)BT|BY-?4\\d", "JADENS / Beeprt", 203, C),
            new Rule("300 ?DPI|LW650|HZD950", "300 dpi TSPL printer", 300, S),
            new Rule("XPRINTER|(^|[^A-Z])XP-?\\d", "Xprinter", 203, S),
            new Rule("IDPRT|(^|[^A-Z])SP4[12]\\d", "iDPRT", 203, S),
            new Rule("HPRT|(^|[^A-Z])(N41|SL42|SK41)", "HPRT", 203, S),
            new Rule("MUNBYN|ITPP|(^|[^A-Z])RW4\\d", "MUNBYN", 203, S),
            new Rule("POLONO|NELKO|(^|[^A-Z])PL(420|70)", "Polono / Nelko", 203, S),
            new Rule("RONGTA|(^|[^A-Z])RP4\\d", "Rongta", 203, S),
            new Rule("FLASHLABEL|(^|[^A-Z])Y4\\dBT", "FlashLabel / Y4x", 203, S),
            new Rule("MVGGES", "Mvgges", 203, S),
            new Rule("(^|[^A-Z])(TSC|TTP-|TDP-|TE2\\d\\d|DA2\\d\\d)", "TSC", 203, S),
    };

    public static final PrinterProfile UNKNOWN = new PrinterProfile("Unrecognized printer", 203, S, false);

    public static PrinterProfile match(String bluetoothName, String model) {
        String s = ((bluetoothName == null ? "" : bluetoothName) + " " + (model == null ? "" : model))
                .toUpperCase(Locale.US);
        for (Rule r : RULES) if (r.p.matcher(s).find()) return r.profile;
        return UNKNOWN;
    }
}
