#!/usr/bin/env python3
"""Generate smoke-slides.pdf: a small deck that drives PDFBox through the
Java2D/AWT code paths whose JNI bindings the native image must have registered
(see README.md, "AWT/Java2D JNI config"). Each feature below maps to a distinct
JDK native code path; a blank page (what exercise.py used to upload) hits none
of them.

Needs: pip install reportlab pillow. Output is checked in; re-run only when
adding features.
"""
import io, os
from PIL import Image, ImageDraw
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.lib.colors import CMYKColor, Color
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen.pathobject import PDFPathObject

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "smoke-slides.pdf")
W, H = 720, 405  # 16:9 points

def star(path, cx, cy, r):
    import math
    pts = [(cx + r * math.cos(math.radians(90 + 144 * i)), cy + r * math.sin(math.radians(90 + 144 * i))) for i in range(5)]
    path.moveTo(*pts[0])
    for p in pts[1:]: path.lineTo(*p)
    path.close()

def draw_1bit(c, img, x, y, w, h):
    """reportlab widens mode-'1' images to 8-bit gray; emit a real 1 bpc DeviceGray XObject."""
    from reportlab.pdfbase import pdfdoc
    assert img.mode == "1"
    packed = img.tobytes()  # PIL packs mode '1' rows MSB-first, padded to bytes — the PDF layout
    name = f"Im1bit{c.getPageNumber()}"
    obj = pdfdoc.PDFStream(pdfdoc.PDFDictionary({
        "Type": pdfdoc.PDFName("XObject"), "Subtype": pdfdoc.PDFName("Image"),
        "Width": img.width, "Height": img.height,
        "ColorSpace": pdfdoc.PDFName("DeviceGray"), "BitsPerComponent": 1,
    }), packed, filters=[])
    reg = c._doc.getXObjectName(name)  # resources and content both use the internal name
    c._doc.Reference(obj, reg)
    c._doc.addForm(name, obj)
    c._formsinuse.append(name)
    c._code.append(f"q {w} 0 0 {h} {x} {y} cm /{reg} Do Q")

def main():
    c = canvas.Canvas(OUT, pagesize=(W, H), pageCompression=0)  # plain content streams: greppable
    ttf = None
    for cand in ("/usr/share/fonts/TTF/DejaVuSans.ttf", "/usr/share/fonts/TTF/cmunrm.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(cand):
            pdfmetrics.registerFont(TTFont("Embedded", cand)); ttf = "Embedded"; break

    # --- Slide 1: text (standard-14 + embedded TrueType), gradient, CMYK, alpha
    c.setFont("Helvetica-Bold", 36); c.drawString(40, H - 70, "Native image smoke deck")
    if ttf:
        c.setFont(ttf, 18); c.drawString(40, H - 100, "Embedded TrueType text — ěščřž ∑")
    c.saveState()
    p = c.beginPath(); p.rect(40, 60, 300, 200); c.clipPath(p, stroke=0, fill=0)   # gradient fills the whole clip
    c.linearGradient(40, 120, 340, 120, (Color(0.9, 0.2, 0.2), Color(0.2, 0.2, 0.9)), extend=True)
    c.restoreState()
    c.setFillColor(CMYKColor(0, 0.8, 0.9, 0.1)); c.rect(400, 100, 200, 120, fill=1, stroke=0)   # DeviceCMYK → ICC/LCMS
    c.setFillColor(Color(0.1, 0.7, 0.2, alpha=0.5)); c.circle(500, 160, 90, fill=1, stroke=0)   # ExtGState ca
    c.setFont("Helvetica", 12); c.drawString(W - 40, 20, "1")
    c.showPage()

    # --- Slide 2: even-odd clip (ShapeSpanIterator/SpanClipRenderer), non-rect nonzero clip, curves, dashes
    c.setFont("Helvetica-Bold", 28); c.drawString(40, H - 60, "Clipping and paths")
    c.saveState()
    p = c.beginPath(); star(p, 200, 180, 140)
    c.clipPath(p, stroke=0, fill=0, fillMode=0)   # 0 = even-odd → 'W*'
    c.setFillColorRGB(0.95, 0.6, 0.1); c.rect(0, 0, W, H, fill=1, stroke=0)
    c.restoreState()
    c.saveState()
    p = c.beginPath(); p.circle(520, 180, 110)
    c.clipPath(p, stroke=0, fill=0, fillMode=1)   # nonzero
    c.setStrokeColorRGB(0.1, 0.3, 0.8); c.setLineWidth(6); c.setDash(12, 6)
    for y in range(60, 320, 24): c.line(380, y, 680, y + 40)
    c.restoreState()
    c.setStrokeColorRGB(0.2, 0.2, 0.2); c.setLineWidth(3)
    c.bezier(40, 40, 200, 120, 300, -40, 680, 60)
    c.showPage()

    # --- Slide 3: images of every raster flavour
    c.setFont("Helvetica-Bold", 28); c.drawString(40, H - 60, "Images")
    rgb = Image.new("RGB", (120, 80)); d = ImageDraw.Draw(rgb)
    for x in range(120): d.line([(x, 0), (x, 80)], fill=(x * 2, 255 - x * 2, 128))
    gray = Image.linear_gradient("L").resize((120, 80))                       # 8-bit gray → ByteComponentRaster
    bw = Image.new("1", (120, 80)); d = ImageDraw.Draw(bw)
    for i in range(0, 120, 10): d.rectangle([i, 0, i + 4, 80], fill=1)
    rgba = Image.new("RGBA", (120, 80), (0, 0, 0, 0)); d = ImageDraw.Draw(rgba)
    d.ellipse([10, 5, 110, 75], fill=(200, 30, 30, 160))                        # SMask
    jpg = io.BytesIO(); rgb.rotate(90, expand=True).save(jpg, "JPEG", quality=80); jpg.seek(0)  # DCTDecode → libjavajpeg
    c.drawImage(ImageReader(rgb), 40, 200, 160, 106)
    c.drawImage(ImageReader(gray), 220, 200, 160, 106)
    draw_1bit(c, bw, 400, 200, 160, 106)                                       # 1-bit → BytePackedRaster
    c.drawImage(ImageReader(rgba), 40, 60, 160, 106, mask="auto")
    c.drawImage(ImageReader(jpg), 220, 40, 100, 150)
    c.saveState(); c.translate(500, 60); c.rotate(20); c.drawImage(ImageReader(rgb), 0, 0, 120, 80); c.restoreState()  # TransformHelper
    c.showPage()

    # --- Slide 4: mixed font sizes for the title heuristic, plus a bare page number
    c.setFont("Helvetica", 14); c.drawString(40, H - 40, "Small header line")
    c.setFont("Helvetica-Bold", 40); c.drawString(40, H - 120, "The real title")
    c.setFont("Helvetica", 16)
    for i, t in enumerate(["first bullet", "second bullet", "third bullet"]):
        c.drawString(60, H - 170 - 28 * i, "• " + t)
    c.setFont("Helvetica", 12); c.drawString(W - 40, 20, "4")
    c.showPage()
    c.save()
    print("wrote", OUT, os.path.getsize(OUT), "bytes")

if __name__ == "__main__":
    main()
