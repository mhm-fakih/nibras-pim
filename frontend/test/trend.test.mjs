import { moveOf } from '../src/util/trend.ts';

export default function run() {
  let failed = 0;
  const check = (name, got, want) => {
    if (got === want) console.log('  ok  ', name);
    else { failed++; console.log('  FAIL', name, 'got', got, 'want', want); }
  };
  check('no previous', moveOf(5, null), 'none');
  check('no latest', moveOf(null, 5), 'none');
  check('same', moveOf(5, 5), 'same');
  check('+5% is up', moveOf(105, 100), 'up');
  check('+10% is up2', moveOf(110, 100), 'up2');
  check('-5% is down', moveOf(95, 100), 'down');
  check('-20% is down2', moveOf(80, 100), 'down2');
  check('from 0 up is up2', moveOf(3, 0), 'up2');
  check('from 0 down is down2', moveOf(-3, 0), 'down2');
  check('custom threshold', moveOf(120, 100, 25), 'up');
  check('negative base', moveOf(-12, -10), 'down2');
  return failed;
}
