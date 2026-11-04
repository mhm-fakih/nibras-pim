import {
  appendHighlight,
  markedRuns,
  removeHighlight,
  sentenceSpans,
  splitMarks,
  stripMarks,
  toggleRange,
} from '../src/util/highlights';

/**
 * Sentence highlighting on the read page. The thing these exist for: no click — on a fresh
 * sentence, on one holding an older clause highlight, twice in a row, on text that itself
 * contains "=" — may ever leave a stray "=" in the record. Same cases as the phone's
 * ClauseHighlightTest and the desktop's test_highlights.py.
 */
export default function run() {
  let pass = 0;
  let fail = 0;
  const check = (name, got, want) => {
    const ok = typeof want === 'function' ? want(got) : JSON.stringify(got) === JSON.stringify(want);
    if (ok) {
      pass++;
      console.log('  ok  ', name);
    } else {
      fail++;
      console.log('  FAIL', name, '\n        got: ', JSON.stringify(got), '\n        want:', typeof want === 'function' ? '(predicate)' : JSON.stringify(want));
    }
  };
  const texts = (spans) => spans.map((s) => s.text);
  /** Click the sentence containing `phrase`. */
  const tap = (source, phrase) => {
    const plain = stripMarks(source);
    const at = plain.indexOf(phrase);
    const hit = sentenceSpans(plain).find((s) => at >= s.start && at < s.end);
    const r = hit && toggleRange(source, hit.start, hit.end);
    return r ? r.source : source;
  };

  // ── sentences
  check('terminators incl. Arabic', texts(sentenceSpans('One. Two! ثلاثة؟ أربعة؛ Five…')), ['One.', 'Two!', 'ثلاثة؟', 'أربعة؛', 'Five…']);
  check('commas do not split', texts(sentenceSpans('First part, second part; and the rest. Next.')), ['First part, second part; and the rest.', 'Next.']);
  check('dot inside a word/number', texts(sentenceSpans('Pi is 3.14 and notes.md lives at example.com/a.b today. Done.')), ['Pi is 3.14 and notes.md lives at example.com/a.b today.', 'Done.']);
  check('run of terminators', texts(sentenceSpans('Really?! Wait... Go.')), ['Really?!', 'Wait...', 'Go.']);
  check('line break ends a sentence', texts(sentenceSpans('First line\nSecond line')), ['First line', 'Second line']);
  {
    const plain = 'Alpha, beta.\nGamma; delta. قال، ثم سكت.';
    check('spans index back into the text', sentenceSpans(plain).every((s) => plain.slice(s.start, s.end) === s.text), true);
  }

  // ── markers
  check('tap highlights the whole sentence', tap('Alpha, beta, gamma. Next one.', 'beta'), '==Alpha, beta, gamma.== Next one.');
  check('second tap clears it', tap('==Alpha, beta, gamma.== Next one.', 'beta'), 'Alpha, beta, gamma. Next one.');
  check('strip', stripMarks('Alpha, ==beta,== gamma.'), 'Alpha, beta, gamma.');
  {
    let s = 'One. Two. Three. Two.';
    s = tap(s, 'One');
    s = tap(s, 'Three');
    check('earlier markers do not shift later ones', s, '==One.== Two. ==Three.== Two.');
  }
  check('old clause highlight absorbed, not nested', tap('Alpha, ==beta,== gamma. Next.', 'gamma'), '==Alpha, beta, gamma.== Next.');
  check('…and cleared by the next tap', tap('==Alpha, beta, gamma.== Next.', 'Alpha'), 'Alpha, beta, gamma. Next.');
  check('two old clauses covering a sentence count as on', tap('==Alpha,== ==beta.== Next.', 'beta'), 'Alpha, beta. Next.');
  check('old highlight over two sentences splits cleanly', tap('==One. Two.== Three.', 'Two'), '==One.== Two. Three.');
  {
    let s = 'Yes?No. End.';
    s = tap(s, 'Yes');
    s = tap(s, 'No');
    check('adjacent sentences merge', s, '==Yes?No.== End.');
    check('…and unmerge', tap(s, 'Yes'), 'Yes?==No.== End.');
  }
  {
    const original = 'قال الشيخ، رحمه الله، كلاما طويلا. ثم سكت.';
    const on = tap(original, 'رحمه');
    check('Arabic on', on, '==قال الشيخ، رحمه الله، كلاما طويلا.== ثم سكت.');
    check('Arabic off', tap(on, 'كلاما'), original);
  }
  for (const original of ['x = y. Next.', 'a == b is true. Next.', 'Ends with =\nNext.', '=start. Next.', '==lonely. Next.']) {
    const plain = stripMarks(original);
    let s = original;
    let ok = true;
    for (let k = 0; k < 3; k++) {
      s = tap(s, plain.slice(0, 1));
      if (stripMarks(s) !== plain) ok = false;
    }
    check(`"=" in the text survives: ${JSON.stringify(original)}`, ok, true);
  }
  check('empty range refused', toggleRange('Alpha.', 3, 3), null);
  check('out of range refused', toggleRange('Alpha.', 2, 99), null);

  // ── the highlights collector follows the markers
  {
    const r = toggleRange('Alpha, ==beta,== gamma. Next.', 0, 'Alpha, beta, gamma.'.length);
    check('collector diff on absorb', { removed: r.removed, added: r.added }, { removed: ['beta,'], added: ['Alpha, beta, gamma.'] });
    let col = appendHighlight('', 'beta,');
    for (const t of r.removed) col = removeHighlight(col, t);
    for (const t of r.added) col = appendHighlight(col, t);
    check('collector ends with only the sentence', col, 'Alpha, beta, gamma.');
  }

  // ── random taps: the reading text never changes, nothing nests, two taps turn a sentence off
  {
    const words = ['alpha', 'beta', 'قال', 'الله', 'x=1', 'a == b', '3.14', 'notes.md', '=', '==', 'end', '?'];
    const seps = [' ', ', ', '، ', '. ', '? ', '! ', '؟ ', '\n', '\n\n', '... ', ' — ', ''];
    let seed = 20261103;
    const rnd = (n) => {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      return seed % n;
    };
    let taps = 0;
    let problems = [];
    for (let doc = 0; doc < 500 && problems.length < 5; doc++) {
      let s = '';
      for (let w = 1 + rnd(13); w > 0; w--) {
        const word = words[rnd(words.length)];
        s += rnd(4) === 0 && !word.includes('=') ? `==${word}==` : word;
        s += seps[rnd(seps.length)];
      }
      const plain = stripMarks(s);
      if (!plain) continue;
      const spans = sentenceSpans(plain);
      for (let k = 0; k < 15; k++) {
        const at = rnd(plain.length);
        const hit =
          spans.find((x) => at >= x.start && at < x.end) ??
          spans.reduce((a, b) => (Math.min(Math.abs(at - a.start), Math.abs(at - a.end + 1)) <= Math.min(Math.abs(at - b.start), Math.abs(at - b.end + 1)) ? a : b));
        const r1 = toggleRange(s, hit.start, hit.end);
        const once = r1 ? r1.source : s;
        const r2 = toggleRange(once, hit.start, hit.end);
        const twice = r2 ? r2.source : once;
        taps += 2;
        if (stripMarks(once) !== plain || stripMarks(twice) !== plain) problems.push(`text changed: ${JSON.stringify(s)}`);
        if (!plain.includes('=') && (!r1 || !r2)) problems.push(`refused without "=": ${JSON.stringify(s)}`);
        if (r1 && r2) {
          const f1 = splitMarks(once).flags;
          const f2 = splitMarks(twice).flags;
          for (let i = hit.start; i < hit.end; i++) {
            if (!/\s/.test(plain[i]) && f1[i] && f2[i]) {
              problems.push(`still on after two taps: ${JSON.stringify(twice)}`);
              break;
            }
          }
        }
        if (r1) {
          const p = splitMarks(once);
          const extra = [...once].filter((c) => c === '=').length - [...p.plain].filter((c) => c === '=').length - 4 * markedRuns(p.plain, p.flags).length;
          if (extra !== 0) problems.push(`odd marker: ${JSON.stringify(once)}`);
        }
        s = rnd(2) ? once : twice;
      }
    }
    check(`random taps (${taps}) leave no stray "="`, problems, []);
  }

  console.log(`  ${pass} passed, ${fail} failed`);
  return fail;
}
