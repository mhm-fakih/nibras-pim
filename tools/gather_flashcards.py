#!/usr/bin/env python3
"""
Builds the flash-card bank bundled at mobile/app/src/main/assets/flashcards.jsonl.

The bank is a derived artefact, not source: it is extracted from the notes module of the Nibras
database. This script is kept beside it so the 21 MB asset is reproducible rather than a blob
nobody can account for.

    mysql db_nibras2 -N -B -r -e "
      SELECT JSON_OBJECT('id', c.id,
                         'd', DATE_FORMAT(COALESCE(c.written_on, c.date_created), '%Y-%m-%d'),
                         'title', COALESCE(c.short_description, ''),
                         'body', c.description)
      FROM index_card c
      LEFT JOIN writing_type t ON t.id = c.type_id
      WHERE c.description IS NOT NULL AND c.deleted_on IS NULL
        AND COALESCE(t.code,'') <> 'aya'
        AND (CHAR_LENGTH(TRIM(c.description)) - CHAR_LENGTH(REPLACE(TRIM(c.description),' ','')) + 1) >= 200
      ORDER BY COALESCE(c.written_on, c.date_created) DESC;" > notes.jsonl

    python3 build_flashcard_bank.py .          # reads notes.jsonl, writes flashcards.jsonl

JSON-per-line out of MySQL rather than TSV, deliberately: descriptions carry bare carriage returns,
and a TSV read with Python's universal newlines split 400 notes into 2,104 half-rows before this
was noticed. JSON escapes them.

Selection rules, from the request:
  * source notes have descriptions of >= 200 words
  * each passage is at most 100 words, cut at sentence boundaries
  * religious and psychological passages are FAVOURED — they lead the bank
  * everything else is imported too, behind them, rather than discarded
  * within each tier, recent first
  * notes of type 'aya' (writing_type.code) are excluded. Note the scale: there are 6,236 of them,
    but only 17 ever had a description of 200 words, so the >=200 filter had already left the other
    6,219 out — excluding the type removes 17 notes, not 6,000.
"""

import json, re, sys, hashlib, unicodedata

# argv is read lazily so this file can be IMPORTED for its scoring — see canonical_flashcards.py,
# which reuses score()/is_rtl() rather than keeping a second copy of the keyword lists. Two copies
# of a tuned heuristic is two things to retune, and the day they disagree the bank and the
# canonical pick are favouring different passages for reasons nobody can see.
SS = sys.argv[1] if len(sys.argv) > 1 else None

def clean(t):
    t = (t or '').replace('\r\n', '\n').replace('\r', '\n')
    t = re.sub(r'<br\s*/?>', '\n', t, flags=re.I)
    t = re.sub(r'</?(p|div|li|ul|ol|h[1-6]|span|strong|em|b|i|a|table|tr|td|blockquote)[^>]{0,200}>', ' ', t, flags=re.I)
    t = re.sub(r'<[^>]{1,200}>', ' ', t)
    t = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', t)     # markdown links -> label
    # Reference marks left over from the source: markdown footnotes and bracketed citation numbers.
    # They are noise on a card — "anxiety and psychological distress[^291]" reads as a typo when
    # the footnote it points at is nowhere on screen and never will be.
    t = re.sub(r'\[\^[^\]]{0,20}\]', '', t)
    t = re.sub(r'(?<!\w)\[\d{1,4}\]', '', t)
    t = t.replace('\\n', ' ')                          # a literal backslash-n that survived export
    t = re.sub(r'&nbsp;?|&amp;', lambda m: ' ' if 'nbsp' in m.group(0) else '&', t)
    t = re.sub(r'&[a-z]{2,8};', ' ', t)                # any other stray entity
    t = t.replace('==', '')                             # nibras highlight markers
    t = re.sub(r'^\s{0,3}#{1,6}\s*', '', t, flags=re.M) # headings
    t = re.sub(r'^\s{0,3}[-*+]\s+', '', t, flags=re.M)  # bullets
    t = re.sub(r'[*_`]{1,3}', '', t)
    t = re.sub(r'https?://\S+', ' ', t)
    t = re.sub(r'&nbsp;?', ' ', t)
    t = re.sub(r'[ \t ]+', ' ', t)
    t = re.sub(r'\n{2,}', '\n\n', t)
    return t.strip()

def words(t):
    return [w for w in re.split(r'\s+', t) if w]

SENT = re.compile(r'(?<=[.!?؟۔])\s+|\n+')

def chunks(text, max_words=100, min_words=35):
    out, cur = [], []
    def flush():
        if cur and len(words(' '.join(cur))) >= min_words:
            out.append(' '.join(cur))
    for s in SENT.split(text):
        s = s.strip()
        if not s:
            continue
        if len(words(s)) > max_words:
            flush(); cur.clear()
            out.append(' '.join(words(s)[:max_words]))
            continue
        if len(words(' '.join(cur + [s]))) > max_words:
            flush(); cur.clear(); cur.append(s)
        else:
            cur.append(s)
    flush()
    return out

REL_AR = ['الله','دعاء','الدعاء','النبي','الإمام','الأئمة','القرآن','الحديث','الصلاة','الإيمان',
          'التقوى','الآخرة','الرحمة','عباد','الشيطان','الجنة','النار','الذنب','التوبة','أهل البيت',
          'عليه السلام','صلى الله','سبحان','الرسول','الشريعة','الدين','الروح','الهداية','الزهد',
          'الصبر','الشكر','الاستغفار','الولاية','الوحي','الآيات','السنة','الفقه','الحلال','الحرام',
          'المؤمن','الكفر','الشرك','الأخلاق','الطاعة','المعصية','الجهاد','الشهادة','القيامة']
PSY_AR = ['علم النفس','النفس','القلق','الاكتئاب','الشعور','السلوك','العقل','الوعي','الذات','الغضب',
          'التربية','الشخصية','المشاعر','الانفعال','نفسي','الدوافع','العادة','الإرادة','التفكير',
          'الخوف','الحزن','الثقة','الضغط','الصدمة','العلاج','الطفل','الأبناء','العلاقة','الذاكرة',
          'الإدراك','الاضطراب','الكبت','اللاوعي','الحوار','الاحترام']

# Latin keywords come in two kinds, and conflating them is what put a Trump press release at the
# head of the bank as its most religious passage: a prefix-open "sin" matched SINGLE. Stems are
# meant to run on ("psycholog" -> psychological); whole words are not ("sin" is not "single").
REL_STEMS = ['religio', 'spiritual', 'revelat', 'scriptur', 'prophe', 'worship', 'repent',
             'divin', 'sanctif', 'theolog']
REL_WORDS = ['god', 'sin', 'soul', 'pray', 'prayer', 'faith', 'quran', 'koran', 'imam', 'islam',
             'islamic', 'muslim', 'mercy', 'hadith', 'verse', 'sacred', 'hereafter', 'blessing',
             'angel', 'heaven', 'hell', 'sunnah', 'ummah', 'shariah', 'piety', 'worshipper']
PSY_STEMS = ['psycho', 'unconscious', 'subconscious', 'anxiet', 'depress', 'emotion', 'behavio', 'cognitiv', 'therap',
             'traumat', 'motivat', 'conscious', 'disciplin', 'identit', 'relationship',
             'attachment', 'narciss', 'neuros', 'obsess']
PSY_WORDS = ['ego', 'mind', 'stress', 'habit', 'attention', 'memory', 'grief', 'anger', 'fear',
             'parent', 'child', 'children', 'mental', 'feeling', 'coping', 'trauma', 'esteem',
             'temperament', 'mood', 'shame', 'guilt', 'empathy']

def _stems(keys):
    return re.compile(r'(?<![a-z])(?:' + '|'.join(map(re.escape, keys)) + r')[a-z]*', re.I)

def _words(keys):
    # A short inflection tail only — enough for plurals and simple forms, not enough to swallow a
    # different word.
    return re.compile(r'(?<![a-z])(?:' + '|'.join(map(re.escape, keys)) +
                      r')(?:s|es|ed|ing|ly|al)?(?![a-z])', re.I)

REL_RE = [_stems(REL_STEMS), _words(REL_WORDS)]
PSY_RE = [_stems(PSY_STEMS), _words(PSY_WORDS)]

def score(t):
    r = sum(len(rx.findall(t)) for rx in REL_RE) + sum(t.count(k) for k in REL_AR)
    p = sum(len(rx.findall(t)) for rx in PSY_RE) + sum(t.count(k) for k in PSY_AR)
    return r, p

ARABIC = re.compile(r'[؀-ۿݐ-ݿﭐ-﷿ﹰ-﻿]')

def is_rtl(t):
    """First strong letter decides, as everywhere else in this codebase."""
    for ch in t:
        if ARABIC.match(ch):
            return True
        if unicodedata.bidirectional(ch) in ('L',):
            return False
    return False

def build():
    rows = [json.loads(l) for l in open(f'{SS}/notes.jsonl') if l.strip()]
    seen, cards = set(), []
    for i, row in enumerate(rows):
        body = clean(row['body'])
        for c in chunks(body):
            key = hashlib.md5(re.sub(r'\s+', '', c).encode()).hexdigest()
            if key in seen:
                continue
            seen.add(key)
            r, p = score(c)
            cards.append({
                'id': key[:12],
                'note': row['id'],
                'date': row['d'],
                'text': c,
                'rtl': is_rtl(c),
                'topic': 'r' if (r and r >= p) else ('p' if p else 'o'),
                '_tier': 0 if (r + p) >= 3 else (1 if (r + p) >= 1 else 2),
                '_rank': i,
                '_score': r + p,
            })

    # Two orderings at once, as asked: religious and psychological passages are FAVOURED, so they
    # form the front of the bank, and within them the order is recent-first. Everything else follows
    # behind on the same recency rule — imported, not discarded, but not standing between the reader
    # and the material they asked for.
    # Favouring is graded rather than binary. One incidental word does not make a passage religious:
    # a US presidential statement signing off "GOD BLESS AMERICA" scores 2 and would otherwise have
    # led a bank of 22,000 as its single most religious entry. Three or more topical words is the line
    # between a passage that is ABOUT this material and one that merely brushes past it.
    cards.sort(key=lambda c: (c['_tier'], c['_rank'], -c['_score']))
    for c in cards:
        c.pop('_rank'); c.pop('_score'); c.pop('_tier')

    # One card per line: the app reads the bank as a stream and materialises only the handful it is
    # showing, instead of holding 22,000 objects in memory on a budget phone.
    with open(f'{SS}/flashcards.jsonl', 'w') as f:
        # A header line first, carrying the bank's size. The reader needs the total to say how much is
        # held back, and without it here it would have to walk all 22,000 lines just to count them —
        # which is the whole cost the streaming format exists to avoid.
        f.write(json.dumps({'bank': 1, 'count': len(cards)}, separators=(',', ':')) + '\n')
        for c in cards:
            f.write(json.dumps(c, ensure_ascii=False, separators=(',', ':')) + '\n')
    import os
    print('cards          :', len(cards))
    print('  religious    :', sum(1 for c in cards if c['topic'] == 'r'))
    print('  psychological:', sum(1 for c in cards if c['topic'] == 'p'))
    print('  other        :', sum(1 for c in cards if c['topic'] == 'o'))
    print('  rtl          :', sum(1 for c in cards if c['rtl']))
    print('jsonl bytes    :', os.path.getsize(f'{SS}/flashcards.jsonl'))


if __name__ == '__main__':
    build()
