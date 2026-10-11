#!/bin/bash
# Runs the JVM engine tests against generated sample PDFs and checks that
# every barcode still decodes from the 1-bit output.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
W=${1:-/tmp/fanfold-test}
rm -rf "$W"; mkdir -p "$W/samples" "$W/classes"
python3 tools/engine-test/make_samples.py "$W/samples" >/dev/null
javac -nowarn -d "$W/classes" app/src/main/java/com/fanfold/core/*.java tools/engine-test/CoreTest.java 2>&1 | grep -v "^Picked" || true
fail=0
run() { # name pdf page size layout
  local out="$W/out_$1"
  mkdir -p "$out/pl"
  PAYLOAD_DIR="$out/pl" java -cp "$W/classes" CoreTest "$out" "$W/samples/$2" "$3" "$4" "$5" 2>&1 | grep -E "plan:|FAIL|blank|Exception|job size|matches" || true
  if [ -f "$out/out.pbm" ]; then
    python3 -c "from PIL import Image; Image.open('$out/out.pbm').convert('L').save('$W/$1.png')"
    # every compressed payload must inflate with the printer's 1 KB window
    python3 -c "
import sys, zlib, glob
for f in glob.glob('$out/pl/*.z'):
    d = zlib.decompressobj(wbits=10); d.decompress(open(f,'rb').read()); assert d.eof, f
" || { echo "  zlib 1KB-window check FAIL ($1)"; fail=1; }
    code=$(zbarimg -q --raw "$W/$1.png" 2>/dev/null | tail -1)
    if [ "$code" = "9400111899223344556677" ]; then echo "  barcode OK  ($1)"; else echo "  barcode FAIL ($1): '$code'"; fail=1; fi
  fi
}
run a_4x6_auto   label_4x6.pdf    1 LP_4X6 0
run b_4x6_trim   label_4x6.pdf    1 LP_4X6 2
run c_letter     label_letter.pdf 1 LP_4X6 0
run d_6x4        label_6x4.pdf    1 LP_4X6 0
run e_4x6_on_100 label_4x6.pdf    1 LP_100X150 0
run f_blankpage  label_2page.pdf  2 LP_4X6 0
run g_usps       label_usps.pdf   1 LP_4X6 0
exit $fail
