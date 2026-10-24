#!/usr/bin/env python3

"""Generate icon.svg and icon*.png — the window icon, which is what Alt+Tab
shows.

The mark is the shared one: the same #133155 ground and the same white
symbolic line-art, on the same 24-unit grid at the same 1.6 stroke with round
caps, as the audio player next door. Only the glyph differs — a picture here,
a sound wave there — so the two read as one family in the launcher.

Kept as a generator rather than a committed binary blob nobody can edit, and
the geometry is written once, below: both the SVG and the PNGs come off it, so
neither can drift from the other.

Run: python3 make_icon.py
"""

import os

from PIL import Image, ImageDraw

GROUND = "#133155"          # the tile
MARK   = "#FFFFFF"          # the line-art on it
RADIUS = 26 / 128           # tile corner radius, as a fraction of the side

# The glyph lives on a 24-unit grid placed into the tile exactly as the audio
# player places its wave, so the two marks come out the same optical size.
GRID_SCALE  = 3.1 / 128     # grid unit, as a fraction of the side
GRID_OFFSET = 26.8 / 128    # grid origin, likewise
STROKE      = 1.6           # in grid units

# A picture: frame, sun, and the hill breaking the horizon. Lucide's "image",
# unchanged, because the launcher is not the place to be inventive.
FRAME = (3, 3, 18, 18, 2)               # x, y, w, h, corner radius
SUN   = (8.5, 8.5, 1.5)                 # cx, cy, r
HILL  = [(21, 15), (16, 10), (5, 21)]   # polyline

SIZES = (256, 128, 64, 48, 32)
SUPERSAMPLE = 4             # PIL has no antialiasing of its own; this is it


def svg() -> str:
    x, y, w, h, r = FRAME
    cx, cy, rad = SUN
    hill = " ".join(f"{'M' if i == 0 else 'L'}{px},{py}"
                    for i, (px, py) in enumerate(HILL))
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
  <!-- Shared app mark: the same {GROUND} ground and the same white symbolic
       line-art (24-unit grid, {STROKE} stroke, round caps) as the audio player.
       Here the glyph is a picture, for the image annotator. -->
  <rect width="128" height="128" rx="{round(RADIUS * 128)}" fill="{GROUND}"/>
  <g transform="translate({GRID_OFFSET * 128:.1f},{GRID_OFFSET * 128:.1f}) scale({GRID_SCALE * 128:.1f})"
     fill="none" stroke="{MARK}" stroke-width="{STROKE}"
     stroke-linecap="round" stroke-linejoin="round">
    <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}"/>
    <circle cx="{cx}" cy="{cy}" r="{rad}"/>
    <path d="{hill}"/>
  </g>
</svg>
"""


def render(side: int) -> Image.Image:
    """The mark at `side` pixels, drawn large and shrunk down for the edges."""
    s = side * SUPERSAMPLE
    unit = s * GRID_SCALE
    origin = s * GRID_OFFSET
    sw = STROKE * unit                  # stroke width, in pixels
    half = sw / 2

    def at(gx: float, gy: float) -> tuple[float, float]:
        return origin + gx * unit, origin + gy * unit

    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=RADIUS * s, fill=GROUND)

    # PIL grows an outline inwards from the box it is given; SVG straddles the
    # path with it. Every box below is therefore let out by half a stroke, or
    # the glyph would sit a stroke-width smaller than the SVG's.
    x, y, w, h, r = FRAME
    x0, y0 = at(x, y)
    x1, y1 = at(x + w, y + h)
    d.rounded_rectangle([x0 - half, y0 - half, x1 + half, y1 + half],
                        radius=r * unit + half, outline=MARK, width=round(sw))

    cx, cy, rad = SUN
    ccx, ccy = at(cx, cy)
    d.ellipse([ccx - rad * unit - half, ccy - rad * unit - half,
               ccx + rad * unit + half, ccy + rad * unit + half],
              outline=MARK, width=round(sw))

    pts = [at(*p) for p in HILL]
    d.line(pts, fill=MARK, width=round(sw), joint="curve")
    for px, py in (pts[0], pts[-1]):    # joint="curve" rounds joins, not caps
        d.ellipse([px - half, py - half, px + half, py + half], fill=MARK)

    return img.resize((side, side), Image.LANCZOS)


here = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(here, "icon.svg"), "w") as fh:
    fh.write(svg())

art = render(max(SIZES) * 2)
for px in SIZES:
    name = "icon.png" if px == max(SIZES) else f"icon-{px}.png"
    art.resize((px, px), Image.LANCZOS).save(os.path.join(here, name))
print("wrote icon.svg, icon.png and "
      + "/".join(str(p) for p in SIZES[1:]) + " variants")
