#!/usr/bin/env python3
"""Merge the recorder's AI transcripts into one Knotes note, then archive the folder.

Run it from anywhere -- it finds FOLDER01 next to itself, so it keeps working
whatever path the recorder happens to be mounted at:

    python3 "/path/to/recorder/REC_FILE/merge-transcripts.py"            # do it
    python3 ".../merge-transcripts.py" --dry-run                               # just show the plan

What it does:
  1. Collects FOLDER01/<YYMMDD_HHMM[_NN]>.<ar|en>.ai.txt.
  2. Writes ONE new note to the Knotes folder, named <now>~N~p3~D.md:
       - first line: a title naming the first and last recording
       - one "## <n>. <timestamp>" heading per recording, in chronological order
         (261022_1253 before 261022_1253_01 before 261022_1254),
         the Arabic passage first, then the English one
       - every line trimmed, runs of blank lines collapsed to one
  3. Renames FOLDER01 to merge-<now> (same timestamp as the note), so the next run
     only sees new recordings, and leaves an empty FOLDER01 for the recorder.

Nothing is renamed unless the note was written. An existing note is never overwritten.
Options: --knotes DIR (default ~/Knotes, or $KNOTES_DIR),
         --type / --priority / --category for the note's name (default N, 3, D).
"""
import argparse
import os
import re
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "FOLDER01"
DEFAULT_KNOTES = os.environ.get("KNOTES_DIR", "~/Knotes")
LANGS = ("ar", "en")  # order within one recording
NAME_RE = re.compile(r"^(\d{6}_\d{4})(?:_(\d{2}))?\.(ar|en)\.ai\.txt$")


def collect(folder: Path) -> list[tuple[str, list[Path]]]:
    """[(timestamp, [ar file, en file]), ...] in chronological order."""
    groups: dict[tuple[str, int], dict[str, Path]] = {}
    for f in folder.iterdir():
        m = NAME_RE.match(f.name)
        if not m or not f.is_file():
            continue
        base, seq, lang = m.groups()
        groups.setdefault((base, int(seq) if seq else -1), {})[lang] = f
    out = []
    for (base, seq), by_lang in sorted(groups.items()):
        ts = base if seq < 0 else f"{base}_{seq:02d}"
        out.append((ts, [by_lang[l] for l in LANGS if l in by_lang]))
    return out


def tidy(text: str) -> list[str]:
    """Trim every line and collapse runs of blank lines; no leading/trailing blanks."""
    out: list[str] = []
    for line in text.splitlines():
        s = line.strip()
        if s == "" and (not out or out[-1] == ""):
            continue
        out.append(s)
    while out and out[-1] == "":
        out.pop()
    return out


def build_note(recordings: list[tuple[str, list[Path]]]) -> str:
    lines = [f"IC Recorder transcripts {recordings[0][0]} – {recordings[-1][0]}", ""]
    for n, (ts, files) in enumerate(recordings, 1):
        lines += [f"## {n}. {ts}", ""]
        passages = [tidy(f.read_text(encoding="utf-8", errors="replace")) for f in files]
        for i, p in enumerate(q for q in passages if q):
            if i:
                lines.append("")
            lines += p
        lines.append("")
    return "\n".join(lines).rstrip("\n") + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--knotes", default=DEFAULT_KNOTES)
    ap.add_argument("--type", default="N")
    ap.add_argument("--priority", type=int, default=3)
    ap.add_argument("--category", default="D")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if not SOURCE.is_dir():
        sys.exit(f"No {SOURCE} -- nothing to merge.")
    knotes = Path(args.knotes).expanduser()
    if not knotes.is_dir():
        sys.exit(f"Knotes folder {knotes} not found (VeraCrypt volume not mounted?). Nothing changed.")

    recordings = collect(SOURCE)
    if not recordings:
        sys.exit(f"No *.ar.ai.txt / *.en.ai.txt in {SOURCE}. Nothing changed.")

    stamp = datetime.now().strftime("%Y_%m_%d-%H%M%S")
    note = knotes / f"{stamp}~{args.type}~p{args.priority}~{args.category}.md"
    archive = HERE / f"merge-{stamp}"
    text = build_note(recordings)

    n_files = sum(len(f) for _, f in recordings)
    print(f"{n_files} transcript files, {len(recordings)} recordings "
          f"({recordings[0][0]} .. {recordings[-1][0]})")
    print(f"note:    {note}  ({len(text.encode())} bytes)")
    print(f"archive: {SOURCE.name} -> {archive.name}, then a fresh empty {SOURCE.name}")
    if args.dry_run:
        print("(dry run -- nothing written)")
        return 0

    if note.exists() or archive.exists():
        sys.exit("Target already exists -- run again in a second. Nothing changed.")
    with open(note, "x", encoding="utf-8") as fh:
        fh.write(text)
    SOURCE.rename(archive)
    SOURCE.mkdir()  # the recorder records into FOLDER01; don't leave it missing
    print("done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
