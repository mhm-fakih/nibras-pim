#!/usr/bin/env python3

"""Tests for the filename half of scr-annotator.

This is the part that can quietly ruin a folder. Renames autosave with no
confirmation, so a label that appends instead of replacing turns
"shot.png" into "shot a a b a b c.png" over four visits, and nobody notices
until the names are unrecoverable. The origins log exists to prevent exactly
that, and these tests are what say it works.

Run: python3 test_naming.py
"""

import os
import shutil
import sys
import tempfile

from scr_annotator import (LOG_NAME, ORIGINALS_DIR, move_file, read_origins,
                           relabel, sanitize, split_label, unique_path)

ok = True


def check(name, got, want):
    global ok
    if got != want:
        print(f"  FAIL {name}\n       got  {got!r}\n       want {want!r}")
        ok = False
    else:
        print(f"  ok   {name}")


def touch(folder, name):
    open(os.path.join(folder, name), "w").close()


with tempfile.TemporaryDirectory() as d:
    print("labels replace, they do not accumulate")
    touch(d, "2025-04-22_09-45-54 sch.png")
    origins = {}
    n = relabel(d, "2025-04-22_09-45-54 sch.png", "fixing vcn", origins)
    check("first label appends",
          n, "2025-04-22_09-45-54 sch fixing vcn.png")

    n = relabel(d, n, "ollama testing", origins)
    check("second label replaces the first",
          n, "2025-04-22_09-45-54 sch ollama testing.png")

    n = relabel(d, n, "", origins)
    check("clearing the label restores the original name",
          n, "2025-04-22_09-45-54 sch.png")

    print("\nthe mapping survives a restart (log replay)")
    n = relabel(d, n, "third pass", origins)
    reread = read_origins(d)
    stem, label, ext = split_label(n, reread)
    check("stem recovered from the log", stem, "2025-04-22_09-45-54 sch")
    check("label recovered from the log", label, "third pass")
    check("extension recovered", ext, ".png")

    n2 = relabel(d, n, "fourth", reread)
    check("relabel after restart still replaces",
          n2, "2025-04-22_09-45-54 sch fourth.png")

with tempfile.TemporaryDirectory() as d:
    print("\nnames that would break things")
    check("a slash cannot become a path separator",
          sanitize("logs/2025 report"), "logs 2025 report")
    check("newlines and tabs collapse", sanitize("a\n\tb   c"), "a b c")
    check("length is capped", len(sanitize("x" * 400)), 150)

    touch(d, "a.png")
    touch(d, "a note.png")
    origins = {}
    n = relabel(d, "a.png", "note", origins)
    check("a colliding target does not overwrite", n, "a note (2).png")
    check("the file it would have clobbered still exists",
          os.path.exists(os.path.join(d, "a note.png")), True)

with tempfile.TemporaryDirectory() as d:
    print("\nfiles this app has never seen")
    touch(d, "holiday photo 2019.jpg")
    origins = read_origins(d)
    stem, label, ext = split_label("holiday photo 2019.jpg", origins)
    check("whole name is the stem, no label invented",
          (stem, label, ext), ("holiday photo 2019", "", ".jpg"))
    n = relabel(d, "holiday photo 2019.jpg", "beach", origins)
    check("label appends to an arbitrary name",
          n, "holiday photo 2019 beach.jpg")
    n = relabel(d, n, "", origins)
    check("and clearing it gives the arbitrary name back",
          n, "holiday photo 2019.jpg")

    print("\nno-op relabel does not touch the disk or the log")
    before = sorted(os.listdir(d))
    n2 = relabel(d, n, "", origins)
    check("same name returned", n2, n)
    check("directory unchanged", sorted(os.listdir(d)), before)

with tempfile.TemporaryDirectory() as d:
    print("\nmoving to a folder keeps the label recoverable")
    src, dest = os.path.join(d, "src"), os.path.join(d, "keep")
    os.makedirs(src)
    os.makedirs(dest)
    touch(src, "shot.png")
    origins = {}
    n = relabel(src, "shot.png", "good one", origins)
    os.makedirs(os.path.join(src, ORIGINALS_DIR))
    touch(os.path.join(src, ORIGINALS_DIR), "shot.png")

    moved = move_file(src, n, dest, origins)
    check("name is kept", moved, "shot good one.png")
    check("file left the source",
          os.path.exists(os.path.join(src, n)), False)
    check("file is in the destination",
          os.path.exists(os.path.join(dest, moved)), True)
    check("source origins forget it", n in origins, False)
    stem, label, ext = split_label(moved, read_origins(dest))
    check("destination recovers the stem and label",
          (stem, label), ("shot", "good one"))
    check("pristine copy followed",
          os.path.exists(os.path.join(dest, ORIGINALS_DIR, "shot.png")), True)
    check("and left the source",
          os.path.exists(os.path.join(src, ORIGINALS_DIR, "shot.png")), False)

    n2 = relabel(dest, moved, "", read_origins(dest))
    check("clearing the label there restores the original", n2, "shot.png")

    print("\nmoving a file the app never touched writes no log")
    log = os.path.join(dest, LOG_NAME)
    rows_before = open(log).read().splitlines()
    touch(src, "plain.jpg")
    moved = move_file(src, "plain.jpg", dest, {})
    check("moved as is", moved, "plain.jpg")
    check("destination log unchanged",
          open(log).read().splitlines(), rows_before)

    print("\na collision in the destination does not overwrite")
    touch(src, "plain.jpg")
    moved = move_file(src, "plain.jpg", dest, {})
    check("renamed aside", moved, "plain (2).jpg")
    check("both exist",
          sorted(f for f in os.listdir(dest) if f.startswith("plain")),
          ["plain (2).jpg", "plain.jpg"])

print("\nPASS" if ok else "\nFAIL")
if __name__ == "__main__":
    sys.exit(0 if ok else 1)
