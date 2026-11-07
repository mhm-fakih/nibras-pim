#!/usr/bin/env python3
"""
Reduce the flash-card bank to ONE canonical card per note.

The bank was extracted as every 100-word passage of every long note, which is why 1,576 notes
produced 22,385 cards — an average of 14 each, and 257 for the worst of them. Reviewing that is not
a task anyone finishes. This picks the single passage that best carries each note's point and
retires the rest.

    python3 tools/canonical_flashcards.py db_nibras2 > /dev/null   # writes dist/*.sql

WHAT IS NEVER TOUCHED: any card you have already decided on — KEPT, REPEAT or REVIEWED. "Keep the
kept ones first" is the rule, and it is applied in the emitted SQL rather than baked in here,
because THIS database's decisions are not the production box's. A note that already has a decided
card is represented by that card and its remaining NEW cards go; a note with no decision keeps its
crux passage.

WHY THE SQL IS PORTABLE: `card_key` is the passage's content hash, so the same passage has the same
key in every database the bank was loaded into. The emitted file names keys, never ids, and asks
the target database itself which notes have decisions — so it is correct on the production box
without this script ever seeing it.

NOTHING IS HARD-DELETED. Retired cards get `deleted_on`, which every query already filters on, so
the whole reduction is one UPDATE away from being undone.
"""

import json, re, subprocess, sys, os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gather_flashcards import score, is_rtl          # noqa: E402  (shared, not re-implemented)
from load_flashcards import esc                      # noqa: E402  (one escaper, not two)

DB = sys.argv[1] if len(sys.argv) > 1 else 'db_nibras2'
# --restore emits a loadable copy of the bank AS IT NOW STANDS, for a database that has no bank yet.
RESTORE = '--restore' in sys.argv
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'dist')

# A passage that states the point, rather than one that happens to sit in the middle of the argument.
# These are the phrases a writer reaches for when summing up — in both languages the notes use.
CRUX_AR = ['الخلاصة', 'خلاصة', 'بمعنى', 'أي أن', 'أي أنه', 'لذلك', 'من هنا', 'القاعدة', 'الأصل',
           'المهم', 'الفكرة', 'جوهر', 'يعني أن', 'وبالتالي', 'إذن', 'النتيجة', 'المقصود']
CRUX_EN = ['in short', 'in other words', 'the point is', 'that is to say', 'therefore', 'thus',
           'in essence', 'essentially', 'the key', 'the principle', 'fundamentally', 'in conclusion',
           'what matters', 'the upshot', 'boils down']
CRUX_RE = re.compile('|'.join(re.escape(k) for k in CRUX_EN), re.I)


def crux_score(passage, topic_hits, words, position_in_note):
    """
    How well this passage stands alone as the note's point.

    Four signals, in the order they matter:
      * summing-up phrases — the strongest evidence a sentence is the conclusion and not a step
      * topical weight, on the SAME keyword lists the bank was favoured by, so the canonical card
        of a religious note is still a religious passage
      * fullness — a passage near the 100-word cap is a complete thought; a 35-word stub is
        whatever was left at the end of a note
      * position, as a tie-break ONLY: with everything else equal the earlier passage usually
        states the thesis and the later ones elaborate it
    """
    markers = len(CRUX_RE.findall(passage)) + sum(passage.count(k) for k in CRUX_AR)
    fullness = min(words, 100) / 100.0
    return (3.0 * markers) + topic_hits + (2.0 * fullness) - (0.01 * position_in_note)


def restore():
    """
    Dump the live bank as INSERT IGNORE, for loading onto a database that has NO bank yet.

    Not the same job as flashcards-canonical.sql, and the difference matters:

      * a database that already carries the 22,385-card bank (and your decisions on it) wants
        **flashcards-canonical.sql** — the reduction, which asks that database which notes you have
        decided about and keeps those cards
      * a database with nothing wants **this** — the 1,576 canonical cards, ready to load

    Running this one against a database that already has the bank does nothing at all: `card_key`
    is unique and every key here is already there. It cannot reduce an existing bank, and it will
    not quietly reset a card you have reviewed.
    """
    q = ("SELECT JSON_OBJECT('k', card_key, 'n', note_id, "
         "'d', DATE_FORMAT(written_on, '%Y-%m-%d'), 'p', passage, "
         "'r', CAST(rtl AS UNSIGNED), 't', topic, 'pos', position) "
         "FROM flash_card WHERE deleted_on IS NULL ORDER BY position")
    out = subprocess.run(['mysql', DB, '-N', '-B', '-r', '-e', q],
                         capture_output=True, text=True, check=True).stdout
    cards = [json.loads(l) for l in out.splitlines() if l.strip()]

    cols = ("card_key, note_id, written_on, passage, rtl, topic, position, "
            "state, date_created, last_updated")
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f'nibras-flashcards-canonical-{len(cards)}.sql')
    with open(path, 'w') as f:
        f.write('-- The canonical flash-card bank: ONE card per note.\n')
        f.write('-- Generated by tools/canonical_flashcards.py --restore. Safe to re-run: card_key\n')
        f.write('-- is unique, so this adds nothing twice and never resets a decided card.\n')
        f.write('-- For a database that ALREADY has the full bank, run flashcards-canonical.sql\n')
        f.write('-- instead — that one reduces in place and keeps your decisions.\n')
        f.write('SET NAMES utf8mb4;\n')
        BATCH = 500
        for i in range(0, len(cards), BATCH):
            values = ',\n'.join(
                f"({esc(c['k'])},{c['n']},{esc(c['d'])},{esc(c['p'])},{c['r']},"
                f"{esc(c['t'])},{c['pos']},'NEW',NOW(),NOW())"
                for c in cards[i:i + BATCH])
            f.write(f'INSERT IGNORE INTO flash_card ({cols}) VALUES\n{values};\n')
    print(f'wrote {path}  ({len(cards):,} cards)', file=sys.stderr)
    return path


def rows():
    """Cards straight from MySQL, JSON per line — descriptions carry bare \\r and TSV cannot hold them."""
    q = ("SELECT JSON_OBJECT('k', card_key, 'n', note_id, 'p', passage, 'pos', position, "
         "'state', state) FROM flash_card WHERE deleted_on IS NULL")
    out = subprocess.run(['mysql', DB, '-N', '-B', '-r', '-e', q],
                         capture_output=True, text=True, check=True).stdout
    return [json.loads(l) for l in out.splitlines() if l.strip()]


def main():
    if RESTORE:
        print(restore())
        return
    cards = rows()
    by_note = {}
    for c in cards:
        by_note.setdefault(c['n'], []).append(c)

    keep, seen_order = [], []
    for note, group in by_note.items():
        ranked = []
        for i, c in enumerate(sorted(group, key=lambda x: x['pos'])):
            r, p = score(c['p'])
            words = len(c['p'].split())
            ranked.append((crux_score(c['p'], r + p, words, i), -words, c))
        # Highest crux score; a fuller passage breaks a tie, and the content hash breaks that, so
        # the choice is deterministic and re-running the script cannot pick differently.
        ranked.sort(key=lambda t: (-t[0], t[1], t[2]['k']))
        best = ranked[0][2]
        keep.append(best['k'])
        seen_order.append((note, best))

    decided = sum(1 for c in cards if c['state'] != 'NEW')
    print(f'cards            : {len(cards):,}', file=sys.stderr)
    print(f'  already decided: {decided:,}  (never touched)', file=sys.stderr)
    print(f'notes            : {len(by_note):,}', file=sys.stderr)
    print(f'canonical cards  : {len(keep):,}', file=sys.stderr)
    print(f'to retire        : {len(cards) - decided - len(keep):,}', file=sys.stderr)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, 'flashcards-canonical.sql')
    with open(path, 'w') as f:
        f.write(HEADER)
        # Shaped from the column itself rather than declared: a temp table declared as plain
        # VARCHAR(32) takes the SESSION's collation, and joining utf8mb4_unicode_ci against the
        # table's utf8mb4_0900_ai_ci is an "illegal mix of collations", not a silent mismatch.
        # CREATE ... AS SELECT inherits type and collation exactly, on whichever database this runs.
        f.write('CREATE TEMPORARY TABLE fc_keep AS SELECT card_key FROM flash_card WHERE 1=0;\n')
        f.write('ALTER TABLE fc_keep ADD PRIMARY KEY (card_key);\n')
        for i in range(0, len(keep), 500):
            batch = ','.join("('%s')" % k for k in keep[i:i + 500])
            f.write(f'INSERT INTO fc_keep VALUES {batch};\n')
        f.write(BODY)
    print(f'\nwrote {path}', file=sys.stderr)
    print(path)


HEADER = """-- One canonical flash card per note.
--
-- Generated by tools/canonical_flashcards.py. Safe to run on any database the bank was loaded
-- into: it names cards by `card_key` (the passage's content hash, identical everywhere) and asks
-- THIS database which notes already have decisions, so your kept/repeat/reviewed cards survive
-- whatever they are here.
--
-- Nothing is hard-deleted. To undo the whole thing:
--     UPDATE flash_card SET deleted_on = NULL WHERE deleted_on IS NOT NULL AND state = 'NEW';

START TRANSACTION;

-- Notes you have already made a decision about somewhere. Their remaining NEW cards go: the note
-- is already represented by the card you acted on, and that is the one card it should have.
CREATE TEMPORARY TABLE fc_decided AS
  SELECT DISTINCT note_id FROM flash_card WHERE deleted_on IS NULL AND state <> 'NEW';
ALTER TABLE fc_decided ADD PRIMARY KEY (note_id);

"""

BODY = """
-- Retire every NEW card that is neither the note's canonical passage nor attached to a note whose
-- decision already speaks for it.
UPDATE flash_card f
  LEFT JOIN fc_keep k ON k.card_key = f.card_key
  LEFT JOIN fc_decided d ON d.note_id = f.note_id
SET f.deleted_on = NOW(), f.last_updated = NOW()
WHERE f.deleted_on IS NULL
  AND f.state = 'NEW'
  AND (k.card_key IS NULL OR d.note_id IS NOT NULL);

-- Renumber what is left. `position` is the queue order, and after retiring 90% of the bank the
-- surviving positions are sparse numbers up in the twenty-thousands; the app hands out a fixed
-- working set by this column, so it has to be dense and it has to keep the favoured ordering:
-- religious and psychological passages first, recent first within each.
SET @p := 0;
UPDATE flash_card
SET position = (@p := @p + 1)
WHERE deleted_on IS NULL
ORDER BY FIELD(topic, 'r', 'p', 'o'), written_on DESC, id;

SELECT state, COUNT(*) AS cards, COUNT(DISTINCT note_id) AS notes
  FROM flash_card WHERE deleted_on IS NULL GROUP BY state;

COMMIT;
"""

if __name__ == '__main__':
    main()
