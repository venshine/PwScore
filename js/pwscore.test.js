// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { score, level, Level } from './pwscore.js';

const SNAPSHOT = new URL('../spec/score-snapshot.tsv', import.meta.url);

test('matches score-snapshot.tsv', () => {
  let checked = 0;
  for (const line of readFileSync(SNAPSHOT, 'utf8').split('\n')) {
    if (line === '' || line.startsWith('#')) continue;
    const first = line.indexOf('\t');
    const second = line.indexOf('\t', first + 1);
    const password = line.slice(second + 1);
    assert.equal(`${score(password)}\t${level(password)}`, line.slice(0, second), `[${password}]`);
    checked++;
  }
  assert.ok(checked > 0);
});

test('rejects missing and empty passwords', () => {
  assert.throws(() => score(null), TypeError);
  assert.throws(() => score(undefined), TypeError);
  assert.throws(() => score(''), RangeError);
  assert.equal(level('2hAj5#mne-ix.86H'), Level.EXTREMELY_STRONG);
});

test('runs in linear time on nearly periodic input', () => {
  const start = performance.now();
  score('a'.repeat(200000) + 'b');
  assert.ok(performance.now() - start < 2000);
});
