#!/usr/bin/env python3

"""Generate icon.svg and icon*.png — the window icon, which is what Alt+Tab
shows.

The mark is the shared one: a flat #133155 navy disc with a solid white glyph,
details cut back in the same navy, no gradients or shadows — the same family
as the audio player and knotes next door. Only the glyph differs: a picture
here (sun and hills), a sound wave there, a note page in knotes.

Kept as a generator rather than a committed binary blob nobody can edit. The
SVG below is the one source: the PNGs are rendered from it with gdk-pixbuf
(the same loader the desktop uses), so neither can drift from the other.

Run: python3 make_icon.py
"""

import os

import gi

gi.require_version("GdkPixbuf", "2.0")
from gi.repository import GdkPixbuf  # noqa: E402

SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
  <!-- Shared app mark, flat: one navy (#133155) disc and a solid white glyph, details cut back in
       the same navy, no gradients or shadows. Same family as the audio player and knotes; only the
       glyph differs. Here: a picture (sun and hills), for the image annotator.

       Keep this comment INSIDE <svg>: gdk-pixbuf sniffs only the first bytes of the file for the
       "<svg" tag, so a long comment above it makes the loader reject the file outright, which
       silently drops the window icon. -->
  <circle cx="64" cy="64" r="60" fill="#133155"/>
  <rect x="28" y="34" width="72" height="60" rx="9" fill="#FFFFFF"/>
  <circle cx="49" cy="53" r="7" fill="#133155"/>
  <path d="M35,86 L58,62 L71,75 L80,66 L93,79 V82 a4,4 0 0 1 -4,4 Z" fill="#133155" stroke="#133155" stroke-width="2" stroke-linejoin="round"/>
</svg>
"""

SIZES = (256, 128, 64, 48, 32)

here = os.path.dirname(os.path.abspath(__file__))
svg_path = os.path.join(here, "icon.svg")
with open(svg_path, "w") as fh:
    fh.write(SVG)

for px in SIZES:
    name = "icon.png" if px == max(SIZES) else f"icon-{px}.png"
    GdkPixbuf.Pixbuf.new_from_file_at_size(svg_path, px, px).savev(os.path.join(here, name), "png", [], [])
print("wrote icon.svg, icon.png and "
      + "/".join(str(p) for p in SIZES[1:]) + " variants")
