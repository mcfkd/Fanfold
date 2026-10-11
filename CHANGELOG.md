# Changelog

## 1.0.1
- Fix: after the printer slept or was switched off while Fanfold was still connected, the next print
  could be delayed a few seconds and then misfeed (more than one label, alignment off until the
  printer was reset). Fanfold wrongly thought a label had been cut off and sent a long "cleanup"
  burst the printer misread. The cut-off flag is now only raised when part of a job really went out,
  and the cleanup is a short run of blank lines the printer safely ignores.
- Setup hints and connection errors no longer assume you have a JADENS printer.

## 1.0 (first public release)

Prints shipping labels from Android to cheap Bluetooth thermal label printers (TSPL), with no account, ads or cloud.

- **Two ways to print:** share a PDF or image to Fanfold, or pick Fanfold in any app's Android Print menu.
- **Gets 4x6 right:** real 4x6 PDFs print 1:1 at 203 dpi. Letter-size labels are cropped from the top half, with fold lines ignored. Sideways labels are rotated, and everything else is trimmed and fitted.
- **Fast:** labels are sent compressed (about 10 KB instead of 124 KB) to printers that support it (JADENS / Beeprt). The app connects ahead of time and stays connected between orders (10 minutes by default).
- **Reliable:** it never prints a previous label. If a send is ever cut off, the printer's half-received image is flushed before the next job.
- **Sets itself up:** it recognizes common printer families by Bluetooth name (JADENS/Beeprt, Xprinter, iDPRT, HPRT, MUNBYN, Polono/Nelko, Rongta, FlashLabel, Mvgges, TSC). Unknown printers start in the safe standard mode. The guided test print fixes transfer mode, invert or resolution based on how the label came out.
- **Controls:** 203/300 dpi, darkness, speed, label gap, alignment nudge, flip, line weight, 8 label sizes, paper/cover check, and Copy printer info for bug reports.
- **Tiny and private:** about 100 KB, with no internet permission.

### Pre-release history (private testing on a JADENS JD268BT)
- Fixed jobs being cut off mid-label, which caused scrambled half-and-half labels.
- Added pre-connecting, a kept-open connection, and remembering which connection method works.
- Added the compressed transfer mode used by the vendor's own app (one zlib-compressed bitmap with a 1 KB window).
- Fixed a window where tapping Print while a new label loaded could print the previous one.
- Fixed a possible UI freeze while a connection attempt was in progress.
