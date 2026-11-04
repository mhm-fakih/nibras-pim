"""Sentence highlighting in the side pane's reading mode — run with ``python3 -m unittest test_highlights``.

The same cases as the phone's ClauseHighlightTest and the web's highlights.test.mjs, because a
highlight is stored as text: if the clients disagree on where a sentence ends or how a marker
pairs up, the same click saves different text — or leaves a stray "=" in the note.
"""
from __future__ import annotations

import random
import unittest

from notes import (apply_highlights, sentence_bounds, sentence_spans, split_highlights,
                   toggle_highlight)


def strip(text: str) -> str:
    return split_highlights(text)[0]


def texts(plain: str) -> list[str]:
    return [plain[s:e] for s, e in sentence_spans(plain)]


def tap(text: str, phrase: str) -> str:
    plain = strip(text)
    at = plain.index(phrase)
    s, e = sentence_bounds(plain, at)
    out = toggle_highlight(text, s, e)
    return text if out is None else out


def runs(plain: str, flags: list[bool]) -> int:
    n, i, count = len(plain), 0, 0
    while i < n:
        if not flags[i]:
            i += 1
            continue
        j = i
        while j < n and flags[j]:
            j += 1
        if plain[i:j].strip():
            count += 1
        i = j
    return count


class Sentences(unittest.TestCase):
    def test_terminators_including_arabic(self):
        self.assertEqual(texts("One. Two! ثلاثة؟ أربعة؛ Five…"), ["One.", "Two!", "ثلاثة؟", "أربعة؛", "Five…"])

    def test_commas_do_not_split(self):
        self.assertEqual(texts("First part, second part; and the rest. Next."),
                         ["First part, second part; and the rest.", "Next."])

    def test_dot_inside_a_word_or_number(self):
        self.assertEqual(texts("Pi is 3.14 and notes.md lives at example.com/a.b today. Done."),
                         ["Pi is 3.14 and notes.md lives at example.com/a.b today.", "Done."])

    def test_run_of_terminators(self):
        self.assertEqual(texts("Really?! Wait... Go."), ["Really?!", "Wait...", "Go."])

    def test_line_break(self):
        self.assertEqual(texts("First line\nSecond line"), ["First line", "Second line"])

    def test_click_past_the_end_of_a_line_aims_at_it(self):
        plain = "Short one.   \n\nNext para."
        self.assertEqual(sentence_bounds(plain, 12), (0, 10))

    def test_click_on_a_blank_line_aims_at_nothing(self):
        plain = "Short one.\n\nNext para."
        self.assertEqual(sentence_bounds(plain, 11), (0, 0))


class Markers(unittest.TestCase):
    def test_tap_then_tap_again(self):
        original = "Alpha, beta, gamma. Next one."
        on = tap(original, "beta")
        self.assertEqual(on, "==Alpha, beta, gamma.== Next one.")
        self.assertEqual(tap(on, "beta"), original)

    def test_earlier_markers_do_not_shift_later_ones(self):
        s = tap(tap("One. Two. Three. Two.", "One"), "Three")
        self.assertEqual(s, "==One.== Two. ==Three.== Two.")

    def test_old_clause_highlight_is_absorbed_not_nested(self):
        on = tap("Alpha, ==beta,== gamma. Next.", "gamma")
        self.assertEqual(on, "==Alpha, beta, gamma.== Next.")
        self.assertEqual(tap(on, "Alpha"), "Alpha, beta, gamma. Next.")

    def test_two_old_clauses_covering_a_sentence_count_as_on(self):
        self.assertEqual(tap("==Alpha,== ==beta.== Next.", "beta"), "Alpha, beta. Next.")

    def test_old_highlight_over_two_sentences_splits_cleanly(self):
        self.assertEqual(tap("==One. Two.== Three.", "Two"), "==One.== Two. Three.")

    def test_adjacent_sentences_merge_and_unmerge(self):
        s = tap(tap("Yes?No. End.", "Yes"), "No")
        self.assertEqual(s, "==Yes?No.== End.")
        self.assertEqual(tap(s, "Yes"), "Yes?==No.== End.")

    def test_arabic(self):
        original = "قال الشيخ، رحمه الله، كلاما طويلا. ثم سكت."
        on = tap(original, "رحمه")
        self.assertEqual(on, "==قال الشيخ، رحمه الله، كلاما طويلا.== ثم سكت.")
        self.assertEqual(tap(on, "كلاما"), original)

    def test_equals_in_the_text_is_never_corrupted(self):
        for original in ("x = y. Next.", "a == b is true. Next.", "Ends with =\nNext.", "=start. Next.",
                         "==lonely. Next."):
            plain, s = strip(original), original
            for _ in range(3):
                s = tap(s, plain[0])
                self.assertEqual(strip(s), plain, original)

    def test_bad_ranges_are_refused(self):
        self.assertIsNone(toggle_highlight("Alpha.", 3, 3))
        self.assertIsNone(toggle_highlight("Alpha.", 2, 99))

    def test_random_taps_never_leave_a_stray_equals(self):
        words = ["alpha", "beta", "قال", "الله", "x=1", "a == b", "3.14", "notes.md", "=", "==", "end", "?"]
        seps = [" ", ", ", "، ", ". ", "? ", "! ", "؟ ", "\n", "\n\n", "... ", " — ", ""]
        rnd = random.Random(20261103)
        taps = 0
        for _ in range(500):
            s = ""
            for _ in range(rnd.randint(1, 13)):
                w = rnd.choice(words)
                s += f"=={w}==" if rnd.randrange(4) == 0 and "=" not in w else w
                s += rnd.choice(seps)
            plain = strip(s)
            if not plain:
                continue
            for _ in range(15):
                at = rnd.randrange(len(plain))
                start, end = sentence_bounds(plain, at)
                if start >= end:
                    continue
                r1 = toggle_highlight(s, start, end)
                once = s if r1 is None else r1
                r2 = toggle_highlight(once, start, end)
                twice = once if r2 is None else r2
                taps += 2
                self.assertEqual(strip(once), plain, s)
                self.assertEqual(strip(twice), plain, once)
                if "=" not in plain:
                    self.assertIsNotNone(r1, s)
                    self.assertIsNotNone(r2, once)
                if r1 is not None and r2 is not None:
                    f1, f2 = split_highlights(once)[1], split_highlights(twice)[1]
                    for i in range(start, end):
                        if not plain[i].isspace():
                            self.assertFalse(f1[i] and f2[i], twice)
                if r1 is not None:
                    p, f = split_highlights(once)
                    self.assertEqual(once.count("=") - p.count("=") - 4 * runs(p, f), 0, once)
                    self.assertEqual(apply_highlights(p, f), once)
                s = rnd.choice([once, twice])
        self.assertGreater(taps, 10_000)


if __name__ == "__main__":
    unittest.main()
