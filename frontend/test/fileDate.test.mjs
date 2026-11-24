import { dateFromFileName } from '../src/util/fileDate';

/** The File processor's date guess from a file name. */
export default function run() {
  let fail = 0;
  const check = (name, got, want) => {
    if (JSON.stringify(got) === JSON.stringify(want)) console.log('  ok  ', name);
    else {
      fail++;
      console.log('  FAIL', name, '\n        got: ', JSON.stringify(got), '\n        want:', JSON.stringify(want));
    }
  };
  check('14-digit scanner stamp', dateFromFileName('scan 20261114074614.pdf'), '2026-11-14');
  check('camera IMG_ stamp', dateFromFileName('IMG_20261114_074614.jpg'), '2026-11-14');
  check('dashed stamp', dateFromFileName('VID-20261114-WA0001.mp4'), '2026-11-14');
  check('audio stamp with dashes', dateFromFileName('audio_2026-11-14_07-46-14.ogg'), '2026-11-14');
  check('screenshot', dateFromFileName('Screenshot 2026-11-14 at 07.46.14.png'), '2026-11-14');
  check('bare date', dateFromFileName('minutes 20260301.md'), '2026-03-01');
  check('no date', dateFromFileName('report final.pdf'), null);
  check('not a real day', dateFromFileName('x 20260431.txt'), null);
  check('a long number is not a date', dateFromFileName('isbn 9780306406157.pdf'), null);
  return fail;
}
