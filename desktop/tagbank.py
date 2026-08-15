"""The tag bank: a canonical hashtag vocabulary, and matching against it.

The bank is a plain text file the user owns and edits (``tag-bank.md`` next to the program by
default). One tag per line: the canonical tag first, then any aliases, comma-separated. Lines
starting with ``#`` are comments and blank lines are skipped, so the file can carry its own
instructions and section headings without a parser mode for them.

An alias is a *synonym that resolves to the canonical tag*, never a tag in its own right, so
picking one inserts the canonical spelling. That is the entire reason the bank has two columns:
without it a vocabulary drifts into three spellings of the same idea within a month.

Kept apart from knotes.py because it is pure text handling with no GTK in it — the matching rules
are the part most likely to need adjusting, and they can be exercised without a display.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

# The file's own name, looked for next to the program unless Settings.tag_bank_path says otherwise.
BANK_FILENAME = "tag-bank.md"

# What a brand-new bank contains. Placeholder tags rather than a curated starter vocabulary: the
# bank is the user's own, and seeding it with someone else's taxonomy means every install begins by
# deleting tags it never wanted. Three obvious dummies make the completer demonstrably work and
# invite replacement — the file explains itself, so the format can be learned by editing it.
STARTER_BANK = """\
# Tag bank — your hashtag vocabulary, one tag per line.
#
# Type "#" in a note to get these as suggestions. Put the canonical spelling first; anything after a
# comma on the same line is an alias for it, so picking the alias inserts the canonical tag. Lines
# starting with "#" (like this one) are comments, and blank lines are ignored.
#
# Example with aliases:   work, job, office
#
# This file is yours: it is never synced or tracked. Replace these placeholders with your own tags.

tag1
tag2
tag3
"""


@dataclass(frozen=True)
class Tag:
    canonical: str
    aliases: tuple[str, ...] = ()

    def spellings(self) -> tuple[str, ...]:
        return (self.canonical,) + self.aliases


@dataclass(frozen=True)
class Match:
    """A bank entry that matched what's being typed. ``via`` is the alias that matched, or None
    when the canonical tag itself did — the UI shows it so an Arabic alias explains why a Latin
    canonical tag is in the list."""
    tag: Tag
    via: str | None
    rank: int

    @property
    def canonical(self) -> str:
        return self.tag.canonical


def parse_bank(text: str) -> list[Tag]:
    """Read the bank's text into tags, in file order.

    Duplicates are dropped rather than merged: two lines claiming the same canonical tag is an
    editing slip, and the first spelling of it is the one to keep. Comparison is case-folded, so a
    stray ``a.quran`` doesn't quietly become a second entry.
    """
    tags: list[Tag] = []
    seen: set[str] = set()
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split(",")]
        canonical = parts[0]
        if not canonical or canonical.casefold() in seen:
            continue
        seen.add(canonical.casefold())
        tags.append(Tag(canonical, tuple(p for p in parts[1:] if p)))
    return tags


def ensure_bank(path: str) -> bool:
    """Create a starter bank at ``path`` when nothing is there. True if one was written.

    A missing bank is not an error — the completer simply has nothing to offer — but it is also
    invisible: with no file and no suggestions, there is nothing to tell you the feature exists or
    where to add to it. Writing a self-describing starter turns that into something you can open
    and edit.

    Only ever writes when the file is absent. An existing bank, including one deliberately emptied,
    is left exactly as it is; and a path whose folder is missing or unwritable is not worth failing
    over, so it is simply left alone.
    """
    if os.path.exists(path):
        return False
    try:
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with open(path, "x", encoding="utf-8") as f:
            f.write(STARTER_BANK)
        return True
    except OSError:
        return False


def load_bank(path: str) -> list[Tag]:
    """The bank at ``path``, or an empty list if it isn't there or can't be read. A missing bank is
    not an error — it just means the completer has nothing to offer and stays out of the way."""
    try:
        with open(path, "r", encoding="utf-8") as f:
            return parse_bank(f.read())
    except OSError:
        return []


def default_bank_path(program_dir: str, configured: str = "") -> str:
    """Where to look for the bank: the configured path if there is one (``~`` expanded), else
    ``tag-bank.md`` beside the program."""
    if configured.strip():
        return os.path.expanduser(configured.strip())
    return os.path.join(program_dir, BANK_FILENAME)


def suggest(tags: list[Tag], typed: str, limit: int = 12) -> list[Match]:
    """Bank entries matching what's been typed after the ``#``, best first.

    Ranked in four bands, because where a match falls in a word says a lot about whether it's the
    one meant: the canonical tag starting with it, then an alias starting with it, then the
    canonical containing it, then an alias containing it. Within a band the bank's own order wins,
    which is why the file is worth keeping grouped by department.

    An empty ``typed`` offers the head of the bank rather than nothing — typing ``#`` alone is a
    reasonable way to ask "what is there?".

    Matching is case-folded and substring-based, so ``quran`` finds ``A.quransci`` and ``تفسير``
    finds ``A.tafsir`` through its Arabic alias. It is deliberately not fuzzy: a bank of a few
    hundred tags is small enough that substring matching stays predictable, and a completer that
    guesses at typos inserts the wrong tag with total confidence.
    """
    needle = typed.strip().casefold()
    out: list[Match] = []
    for tag in tags:
        best: tuple[int, str | None] | None = None
        for i, spelling in enumerate(tag.spellings()):
            folded = spelling.casefold()
            if not needle:
                band = 0
            elif folded.startswith(needle):
                band = 0 if i == 0 else 1
            elif needle in folded:
                band = 2 if i == 0 else 3
            else:
                continue
            via = None if i == 0 else spelling
            if best is None or band < best[0]:
                best = (band, via)
            if band == 0:
                break
        if best is not None:
            out.append(Match(tag, best[1], best[0]))
    out.sort(key=lambda m: m.rank)   # stable: ties keep the bank's own order
    return out[:limit]
