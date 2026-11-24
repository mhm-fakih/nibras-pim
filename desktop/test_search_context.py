"""Search excerpts on the board's cards — run with ``python3 -m unittest test_search_context``."""
from __future__ import annotations

import unittest

from notes import search_snippets


class SearchSnippetsTest(unittest.TestCase):

    def test_excerpt_around_a_match_deep_in_a_long_note(self):
        text = "Title\n\n" + "filler words here " * 40 + "the KEY point is\nthis one " + "tail " * 40
        snips, total = search_snippets(text, "key point")
        self.assertEqual(total, 1)
        before, match, after = snips[0]
        self.assertEqual(match, "KEY point")
        self.assertTrue(before.startswith("…") and before.endswith("the "))
        self.assertTrue(after.startswith(" is this one") and after.endswith("…"))

    def test_counts_every_match_lists_at_most_the_limit(self):
        text = " ".join(f"apple{i} " + "x" * 200 for i in range(6))
        snips, total = search_snippets(text, "apple", limit=3)
        self.assertEqual(total, 6)
        self.assertEqual(len(snips), 3)

    def test_nearby_matches_share_one_excerpt_and_markers_are_dropped(self):
        snips, total = search_snippets("a ==cat== and a cat", "cat")
        self.assertEqual(total, 2)
        self.assertEqual(snips, [("a ", "cat", " and a cat")])

    def test_no_query_no_excerpts(self):
        self.assertEqual(search_snippets("text", ""), ([], 0))


if __name__ == "__main__":
    unittest.main()
