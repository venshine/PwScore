// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时
import 'dart:io';

import 'package:pwscore/pwscore.dart' as pwscore;
import 'package:test/test.dart';

void main() {
  test('matches score-snapshot.tsv', () {
    final lines = File('../spec/score-snapshot.tsv').readAsStringSync().split('\n');
    var checked = 0;
    for (final line in lines) {
      if (line.isEmpty || line.startsWith('#')) continue;
      final first = line.indexOf('\t');
      final second = line.indexOf('\t', first + 1);
      final password = line.substring(second + 1);
      expect('${pwscore.score(password)}\t${pwscore.level(password).code}', line.substring(0, second),
          reason: '[$password]');
      checked++;
    }
    expect(checked, greaterThan(0));
  });

  test('rejects empty password', () {
    expect(() => pwscore.score(''), throwsArgumentError);
    expect(pwscore.level('2hAj5#mne-ix.86H'), pwscore.Level.extremelyStrong);
  });

  test('runs in linear time', () {
    final watch = Stopwatch()..start();
    pwscore.score('${'a' * 200000}b');
    expect(watch.elapsed, lessThan(const Duration(seconds: 2)));
  });
}
