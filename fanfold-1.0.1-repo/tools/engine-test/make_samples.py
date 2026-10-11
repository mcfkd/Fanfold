"""Generates sample shipping-label PDFs for exercising the layout engine. All names, addresses and numbers are made up."""
import sys
from reportlab.pdfgen import canvas
from reportlab.lib.units import inch
from reportlab.graphics.barcode import code128

out = sys.argv[1] if len(sys.argv) > 1 else "."
TRACK = "9400111899223344556677"


def draw_label(c, x, y, w, h):
    """Draws a 4x6-style label with its bottom-left at (x, y), size w x h points."""
    c.setLineWidth(2)
    c.rect(x + 4, y + 4, w - 8, h - 8)
    c.setFont("Helvetica-Bold", 40)
    c.drawString(x + 14, y + h - 56, "P")
    c.setFont("Helvetica-Bold", 14)
    c.drawString(x + 60, y + h - 34, "USPS GROUND ADVANTAGE")
    c.setFont("Helvetica", 10)
    c.drawString(x + 14, y + h - 90, "SAMPLE SELLER CO")
    c.drawString(x + 14, y + h - 102, "123 EXAMPLE RD, ANYTOWN ST 00000")
    c.setFont("Helvetica-Bold", 12)
    c.drawString(x + 40, y + h * 0.55, "SHIP TO: JANE BUYER")
    c.drawString(x + 40, y + h * 0.55 - 15, "456 SAMPLE AVE, ANYTOWN ST 00000")
    bc = code128.Code128(TRACK, barHeight=0.9 * inch, barWidth=0.0148 * inch * 72 / 72 * 1.0)
    bc.drawOn(c, x + (w - bc.width) / 2, y + 60)
    c.setFont("Helvetica", 9)
    c.drawCentredString(x + w / 2, y + 45, "USPS TRACKING # " + TRACK)


# 1) proper 4x6 label PDF
c = canvas.Canvas(f"{out}/label_4x6.pdf", pagesize=(4 * inch, 6 * inch))
draw_label(c, 0, 0, 4 * inch, 6 * inch)
c.showPage(); c.save()

# 2) letter page: label on top half, receipt below the fold
c = canvas.Canvas(f"{out}/label_letter.pdf", pagesize=(8.5 * inch, 11 * inch))
draw_label(c, 0.5 * inch, 5.75 * inch, 6 * inch, 4.8 * inch)  # landscape-ish label area
c.setDash(4, 4); c.line(0.3 * inch, 5.5 * inch, 8.2 * inch, 5.5 * inch); c.setDash()
c.setFont("Helvetica", 11)
for i, t in enumerate(["Fold here along line", "RECEIPT", "Order 12-34567-89012", "Postage $5.12", "Thank you"]):
    c.drawString(1 * inch, 4.8 * inch - i * 18, t)
c.showPage(); c.save()

# 3) landscape 6x4 label
c = canvas.Canvas(f"{out}/label_6x4.pdf", pagesize=(6 * inch, 4 * inch))
draw_label(c, 0, 0, 6 * inch, 4 * inch)
c.showPage(); c.save()

# 4) two-page 4x6 with a blank trailing page
c = canvas.Canvas(f"{out}/label_2page.pdf", pagesize=(4 * inch, 6 * inch))
draw_label(c, 0, 0, 4 * inch, 6 * inch); c.showPage()
c.showPage(); c.save()
print("ok")

# 5) busier label laid out like a real USPS Ground Advantage label (fake data)
import random

def datamatrix(c, x, y, size, data, n=26):
    """Draws a DataMatrix-like 2D code (random modules seeded by data) — same density as the real thing."""
    rnd = random.Random(data)
    m = size / n
    for r in range(n):
        for k in range(n):
            edge = k == 0 or r == 0 or (r == n - 1 and k % 2 == 0) or (k == n - 1 and r % 2 == 1)
            if edge or rnd.random() < 0.5:
                c.rect(x + k * m, y + r * m, m, m, stroke=0, fill=1)

W, H = 4 * inch, 6 * inch
c = canvas.Canvas(f"{out}/label_usps.pdf", pagesize=(W, H))
c.setLineWidth(1.2)
c.rect(8, 8, W - 16, H - 16)
c.line(8, H - 100, W - 8, H - 100); c.line(8, H - 125, W - 8, H - 125)
c.line(80, H - 100, 80, H - 8)
c.setFont("Helvetica-Bold", 44); c.drawString(22, H - 70, "G")
c.setFont("Helvetica-Bold", 9); c.drawString(88, H - 22, "US POSTAGE PAID IMI")
c.setFont("Helvetica", 8)
for i, t in enumerate(["09/30/2026", "From 00000", "0 lbs 1 ozs", "Zone 6"]):
    c.drawString(88, H - 34 - i * 10, t)
datamatrix(c, 170, H - 70, 50, "SAMPLE-IMI-000W0000000000-TESTDATA-0123456789")
c.drawString(230, H - 40, "000W0000000000")
c.setFont("Helvetica-Bold", 13); c.drawCentredString(W / 2, H - 118, "USPS GROUND ADVANTAGE")
c.setFont("Helvetica", 9)
for i, t in enumerate(["SAMPLE SELLER CO", "123 EXAMPLE RD", "ANYTOWN ST 00000-0000"]):
    c.drawString(16, H - 140 - i * 11, t)
c.setFont("Helvetica-Bold", 14); c.drawString(W - 70, H - 160, "0001")
c.rect(W - 80, H - 215, 52, 18); c.setFont("Helvetica-Bold", 12); c.drawString(W - 75, H - 210, "C001")
datamatrix(c, 20, H - 290, 36, "420812019400111899223344556677")
c.setFont("Helvetica", 11)
for i, t in enumerate(["JANE BUYER", "456 SAMPLE AVE", "ANYTOWN ST 00000-0000"]):
    c.drawString(66, H - 252 - i * 13, t)
c.setLineWidth(2.5); c.line(8, 150, W - 8, 150); c.setLineWidth(1.2)
c.setFont("Helvetica-Bold", 10); c.drawCentredString(W / 2, 138, "USPS TRACKING #")
bc = code128.Code128(TRACK, barHeight=0.85 * inch, barWidth=0.0148 * inch)
bc.drawOn(c, (W - bc.width) / 2, 50)
c.setFont("Helvetica-Bold", 10); c.drawCentredString(W / 2, 36, "9400 1118 9922 3344 5566 77")
c.showPage(); c.save()
print("usps ok")
