# Fanfold

**Print shipping labels from your Android phone to a cheap Bluetooth thermal label printer. Free, no account, no subscription, no ads, no cloud.**

Fanfold makes your label printer work like a normal printer on Android. Share a label to it, or pick it in any app's Print menu, and the label comes out the right size, fast. It talks to the printer directly in the printer's own command language (TSPL), so there's no vendor app in the middle.

<!--
Screenshots: put PNGs in docs/ and uncomment this line.
Use the built-in test label or a sample label, never a real one with addresses.
<p><img src="docs/main.png" width="260"> <img src="docs/settings.png" width="260"></p>
-->

## Why it exists

- **The printer's own app sometimes printed an old label.** When you're shipping orders, that's unacceptable. Fanfold never does it.
- **A label printer should act like a printer.** You pick it from the Print menu like any other printer, with no extra app to open first.
- **No one can take it away.** Vendor apps get discontinued, paywalled or loaded with ads. Fanfold is open source, so it keeps working and anyone can fix it.
- **It's made for the printers sellers actually buy:** the affordable 4x6 Bluetooth printers most online sellers use, not the expensive Zebras.

It's built for people who just want to print the label and get back to work. Once it's set up, you shouldn't notice it.

## What you can do with it

**Print from a selling app's Print button.** Turn Fanfold on once in Android's printing settings (see [Print menu setup](#print-menu-setup)). After that, when eBay, Chrome, Gmail or a PDF viewer offers *Print*, choose **Fanfold** as the printer and the label prints at 4x6.

**Print a label you downloaded or got by email.** Pirate Ship, Etsy, Poshmark, Mercari, Shopify, USPS, UPS and the rest all hand you a PDF or image. Tap **Share** and pick Fanfold, or open the file from inside Fanfold. You get a preview, then you tap **Print**. Want to skip the preview? Turn on *Print right away when shared*.

**Print letter-size labels on 4x6 stock.** Many sites lay the label on the top half of a letter page with a receipt or instructions below. Fanfold finds the label, crops it out, rotates it and fills the 4x6, ignoring the dashed fold line. If your label sits on the bottom half instead, set **Page layout › Bottom half of page**.

**Print a batch.** A multi-page PDF or several files shared at once print as one batch, one label each. Blank pages are skipped, and you can page through the preview first.

**Print smaller labels.** Inventory, SKU or barcode labels on 4x4, 4x3, 4x2, 3x2, 2.25x1.25 or 2x1 stock. Pick the size in Settings. Screenshots and photos work too; use **Image style › Photo** for pictures and logos.

## Features

- **Gets 4x6 right.** Real 4x6 PDFs print 1:1 at the printer's native resolution, so barcodes stay sharp and scan. Sideways labels are rotated, and anything else is trimmed of whitespace and fitted.
- **Fast.** On printers that support it, labels are sent compressed (about 10 KB instead of about 124 KB). Fanfold also connects ahead of time, when you open the app or the Print screen, so the label comes out moments after you tap Print.
- **Stays connected.** After a print it holds the connection (10 minutes by default; 2 or 30 in Settings), so a run of orders prints back to back with no reconnect wait. **Disconnect** frees the printer for other apps.
- **Never prints an old label.** If a print ever gets cut off, Fanfold clears what the printer was holding before the next job.
- **Sets itself up.** It recognizes common printer brands by their Bluetooth name and picks the transfer mode and resolution for you. The **Test print** asks how the label came out and fixes the setting that's wrong.
- **Controls when you need them:** paper and cover check, darkness, speed, label gap, alignment nudge, 203/300 dpi, flip, invert, line weight, 8 label sizes.
- **Tiny and private.** The app is about 100 KB, has no internet permission, no analytics and no ads. See [Privacy](#privacy).

## Will it work with my printer?

Fanfold works with label printers that:

1. **speak TSPL**, the command language most cheap 4-inch thermal label printers use, and
2. **use classic Bluetooth**, meaning the printer shows up and pairs in Android's own **Settings › Bluetooth** screen.

You also need an Android phone on Android 6.0 or newer.

| Printer | Status | Auto settings |
|---|---|---|
| JADENS JD268BT | ✅ Confirmed, in daily use | Compressed, 203 dpi |
| JADENS JD-168BT / JD-468BT, Beeprt BY-426BT (same hardware family) | Very likely | Compressed, 203 dpi |
| Bluetooth models from Xprinter, iDPRT, HPRT, MUNBYN, Polono / Nelko, Rongta, FlashLabel, Mvgges, TSC | Expected to work (TSPL brands); not tested yet | Standard, 203 dpi |
| Other TSPL printers with classic Bluetooth | Worth a try | Standard, 203 dpi |
| 300 dpi TSPL printers | Supported in the code; not tested yet | Standard, 300 dpi |
| Bluetooth LE-only printers (if it only shows up inside its own app, not in Android's Bluetooth settings) | ❌ Not supported | — |
| Wi-Fi-only or USB-only printers | ❌ Not supported | — |
| Zebra, DYMO, Brother, and most mini sticker printers such as Niimbot (they use other command languages) | ❌ Not supported | — |

**Quick way to find out:** pair the printer in Android's Bluetooth settings, choose it in Fanfold, and tap **Test print**. If the label isn't right, answer the "How did it come out?" question and Fanfold switches the setting that usually fixes it. It takes a minute and can't harm the printer.

**Good signs before you buy:** the product page or manual mentions *TSPL* (often listed with EPL/ZPL), it's a 4-inch printer sold for shipping labels, and it says it works with Android by Bluetooth rather than only through its own app.

Tried one? [Open a printer report](../../issues/new/choose) and paste in the text from **Settings › Printer info › Copy printer info**. Every report helps the next seller, and confirmed printers get added to this table.

<details>
<summary>Which Bluetooth names Fanfold recognizes</summary>

Fanfold matches the name your printer shows when pairing (and, if the printer answers, the model it reports). Anything not listed starts on safe defaults: standard transfer, 203 dpi.

| Family | Name contains | Transfer | DPI |
|---|---|---|---|
| JADENS / Beeprt | `JADENS`, `BEEPRT`, `JD` + number, `168BT` `268BT` `468BT` `426BT`, `BY-4x` | Compressed | 203 |
| 300 dpi printers | `300DPI`, `LW650`, `HZD950` | Standard | 300 |
| Xprinter | `XPRINTER`, `XP-` + number | Standard | 203 |
| iDPRT | `IDPRT`, `SP41x`, `SP42x` | Standard | 203 |
| HPRT | `HPRT`, `N41`, `SL42`, `SK41` | Standard | 203 |
| MUNBYN | `MUNBYN`, `ITPP`, `RW4x` | Standard | 203 |
| Polono / Nelko | `POLONO`, `NELKO`, `PL420`, `PL70` | Standard | 203 |
| Rongta | `RONGTA`, `RP4x` | Standard | 203 |
| FlashLabel | `FLASHLABEL`, `Y4xBT` | Standard | 203 |
| Mvgges | `MVGGES` | Standard | 203 |
| TSC | `TSC`, `TTP-`, `TDP-`, `TE2xx`, `DA2xx` | Standard | 203 |

You can override both settings in **Settings › Printer**. The rules live in [`PrinterProfile.java`](app/src/main/java/com/fanfold/core/PrinterProfile.java).
</details>

## Install

1. Download the latest `Fanfold-x.y.z.apk` from [Releases](../../releases) on your phone and open it. Android will ask you to allow installs from your browser or file manager. Fanfold isn't on the Play Store, so Play Protect may also show a warning for an unknown app; that's expected for apps installed this way.
2. Turn the printer on and pair it in **Settings › Bluetooth**. If it asks for a PIN, try `0000` or `1234`.
3. Open Fanfold, allow **Nearby devices** (needed to talk to the printer), tap **Choose printer**, then **Test print**.
4. Optional, but recommended: [set up the Print menu](#print-menu-setup).

The printer only talks to one app at a time. If Fanfold can't connect, close the printer maker's app first.

<details>
<summary>Verify the download (optional)</summary>

Official releases are signed with this certificate:

```
Signer certificate SHA-256 digest: c4bfe57c5f4d55bc8152a264237fcf42486e76633fef9768d9b1f5eb5bebdb25
```

Check with `apksigner verify --print-certs Fanfold-x.y.z.apk`. Android also refuses to update an installed copy with an APK signed by anyone else.
</details>

## Print menu setup

Do this once and every app's **Print** button can send to your label printer.

1. In Fanfold, tap **Printing settings** (or search your phone's Settings for "Printing").
2. Tap **Fanfold** and turn it **On**.
3. In any app, tap **Print**, choose **Fanfold** as the printer, and set the paper size to your label size (4 x 6 in by default).

## Troubleshooting

**Can't connect.**
Make sure the printer is on, nearby, and paired in Android's Bluetooth settings. Close the printer maker's app, since only one app can hold the connection. Still stuck? Turn the printer off and on. If another app needs the printer, tap **Disconnect** in Fanfold first.

**The label is blank, garbled, or only partly printed.**
Run **Test print** and answer *Blank, garbled or only part printed*. Fanfold steps down to a more compatible transfer mode. You can also set **Settings › Transfer mode** to *Standard* or *Full image* yourself.

**It printed solid black, or white text on black.**
Answer *Solid black* after a test print, or turn on **Settings › Invert**.

**The label is too big or too small.**
Your printer is probably 300 dpi, not 203, or the reverse. Answer *Too big or too small* after a test print, or set **Settings › Printer resolution**.

**It prints across two labels, skips labels, or starts in the wrong place.**
Check **Settings › Label gap** (3 mm suits standard shipping labels; use *Continuous roll* for gapless paper). Then let the printer re-learn the label size. Many models do this when you hold the feed button until it feeds a label or two and stops, but check your printer's manual. If it's only a little off, use **Nudge left / right** and **Nudge up / down**.

**It fed extra labels or lost alignment after it slept or jammed.**
Turn the printer off and on, and recalibrate as above. If it keeps happening, please [report it](../../issues/new/choose) with your **Printer info**.

**The print is faint.**
Raise **Darkness** (8 to 12 is typical), choose a slower **Print speed**, or set **Line weight** to *Bolder*.

**Labels come out upside down.**
Turn on **Settings › Flip 180°**.

**Fanfold isn't in the Print menu.**
Follow [Print menu setup](#print-menu-setup). The service has to be switched on once before it appears.

**Nothing comes out at all.**
Check that labels are loaded and the cover is closed. With **Check paper & cover before printing** on, Fanfold tells you when the printer reports either problem. Then try **Transfer mode › Standard**.

## Privacy

- **No internet permission.** Fanfold can't send anything off your phone, even if it wanted to.
- **One permission:** *Nearby devices* (Bluetooth connect), used only to talk to the printer you choose. On Android 11 and older there's nothing to allow; basic Bluetooth access is granted at install.
- **Your labels stay on your phone.** A shared label is copied into Fanfold's private storage so it can be rendered, and old copies are cleared out after a day. Labels sent from the Print menu are deleted as soon as they print.
- **Copy printer info** gives you text to paste into a report: app version, phone model, Android version, your printer's Bluetooth name and your print settings. It never includes labels, addresses or your printer's hardware address.

When you post photos in an issue, use the Fanfold test label, or black out the addresses on a real one first.

## Limitations

- Android only. No iPhone version.
- Classic Bluetooth only. No Bluetooth LE, Wi-Fi or USB.
- One printer is confirmed so far. The rest is expected to work but needs real reports.
- It prints labels you already have. It isn't a label designer.
- Not on the Play Store, at least for now.

## Contributing

The most useful thing you can do is test it on your printer and [file a printer report](../../issues/new/choose), whether it worked or not. Bug reports are welcome too: say where the label came from (which site or app) and how you printed it (share or Print menu).

Code contributions are welcome as long as they keep Fanfold small. That means no third-party libraries, no internet access, and no account features. Please run the engine tests before opening a pull request.

## Building from source

### Command line (how releases are built)
```bash
sudo apt install default-jdk aapt dalvik-exchange apksigner zipalign android-sdk-platform-23
bash build.sh               # -> build/Fanfold.apk, signed with a throwaway local key
```
For a release build, set `KEYSTORE`, `KEYSTORE_PASS` and `KEY_ALIAS`. CI runs the same script on every push; see [`.github/workflows/build.yml`](.github/workflows/build.yml).

### Android Studio
Gradle files are included and there are no third-party dependencies, so the folder should open as a normal Android project. Releases and CI don't use Gradle, so this path is less tested. If it gives you trouble, use `build.sh` or open an issue.

### Engine tests
The layout and printer-command engine (`com.fanfold.core`) is plain Java and is tested on a desktop. The tests generate sample labels, run them through the engine and a TSPL simulator, check that the result matches bit for bit, check that compressed data unpacks with the printer's 1 KB window, and scan the barcodes back.
```bash
sudo apt install default-jdk poppler-utils zbar-tools python3-reportlab python3-pil
bash tools/engine-test/run_tests.sh
```

## How it works

| Piece | Job |
|---|---|
| `core/Layout` | Finds the label on the page and decides crop, rotation and scale (whole page, top or bottom half, trimmed) |
| `core/Mono` | Converts to 1-bit black and white (sharp threshold, or dithered for photos) |
| `core/Tspl`, `core/ZDeflate` | Builds the TSPL job: `SIZE`, `GAP`, `DIRECTION`, `DENSITY`, `SPEED`, the bitmap, `PRINT` |
| `core/PrinterProfile` | Recognizes printer families by Bluetooth name and picks the Auto settings |
| `SourceDoc`, `LabelEngine` | Render PDFs (Android's PdfRenderer) and images at the printer's resolution |
| `BtPrinter` | Bluetooth serial link: connects ahead of time, keeps the link open between jobs, checks paper and cover status (`ESC !?`), and recovers safely if a job was cut off |
| `LabelPrintService` | Android Print menu integration |

<details>
<summary>Transfer modes, for the curious</summary>

- **Compressed:** one `BITMAP x,y,w,h,3,len,<zlib data>` with the zlib window limited to 1 KB, because these printers unpack into a tiny buffer. In this mode 1 means black. A 4x6 label is typically 7 to 10 KB.
- **Standard:** plain `BITMAP` mode 0, but only for the strips of the label that have ink. Any TSPL printer understands it.
- **Full image:** one plain `BITMAP` of the whole label, about 124 KB at 203 dpi. Slowest, but the most compatible.

Fanfold's code is its own. No code from any printer maker's app is included.
</details>

## License

MIT. See [LICENSE](LICENSE).

Built by an online seller with no programming background, with help from AI (Claude).

*Fanfold is an independent project and is not affiliated with or endorsed by JADENS, Beeprt, or any printer maker or selling platform. Brand and product names are used only to describe compatibility.*
