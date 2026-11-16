/// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。Dart 的 String 是 UTF-16，与规则口径一致。
///
/// Licensed under the Apache License, Version 2.0
library;

/// 强度等级，按分值从低到高
enum Level {
  easy('EASY'),
  medium('MEDIUM'),
  strong('STRONG'),
  veryStrong('VERY_STRONG'),
  extremelyStrong('EXTREMELY_STRONG');

  const Level(this.code);

  /// 与其他语言一致的等级名，如 "VERY_STRONG"
  final String code;
}

const _dictionary = [
  'password', 'abc123', 'iloveyou', 'adobe123', '123123', 'sunshine', //
  '1314520', 'a1b2c3', '123qwe', 'aaa111', 'qweasd', 'admin', 'passwd',
];

const _sequences = [
  ['abcdefghijklmnopqrstuvwxyz', 'zyxwvutsrqponmlkjihgfedcba'],
  ['01234567890', '09876543210'],
  ['qwertyuiop', 'poiuytrewq', 'asdfghjkl', 'lkjhgfdsa', 'zxcvbnm', 'mnbvcxz'],
];

const _sequenceMin = 4;

/// 每组序列所有长度为 4 的片段
final List<Set<String>> _sequenceKeys = [
  for (final group in _sequences)
    {
      for (final seq in group)
        for (var i = 0; i + _sequenceMin <= seq.length; i++) seq.substring(i, i + _sequenceMin),
    },
];

const _daysInMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const _num = 0;
const _small = 1;
const _capital = 2;
const _other = 3;

int _charType(int c) {
  if (c >= 48 && c <= 57) return _num;
  if (c >= 97 && c <= 122) return _small;
  if (c >= 65 && c <= 90) return _capital;
  return _other;
}

int _repeatUnitLength(String s) {
  final n = s.length;
  final prefix = List<int>.filled(n, 0);
  for (var i = 1; i < n; i++) {
    var k = prefix[i - 1];
    while (k > 0 && s.codeUnitAt(i) != s.codeUnitAt(k)) {
      k = prefix[k - 1];
    }
    if (s.codeUnitAt(i) == s.codeUnitAt(k)) k++;
    prefix[i] = k;
  }
  final period = n - prefix[n - 1];
  return n % period == 0 ? period : n;
}

bool _isSameChar(String s, int from, int to) {
  for (var i = from + 1; i < to; i++) {
    if (s.codeUnitAt(i) != s.codeUnitAt(from)) return false;
  }
  return true;
}

int _digits(String s, int start, int count) {
  var value = 0;
  for (var i = start; i < start + count; i++) {
    value = value * 10 + (s.codeUnitAt(i) - 48);
  }
  return value;
}

int _daysIn(int year, int month) {
  final leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
  return month == 2 && leap ? 29 : _daysInMonth[month - 1];
}

bool _containsDate(String s) {
  var run = 0;
  for (var i = 0; i < s.length; i++) {
    run = _charType(s.codeUnitAt(i)) == _num ? run + 1 : 0;
    if (run >= 8) {
      final start = i - 7;
      final year = _digits(s, start, 4);
      final month = _digits(s, start + 4, 2);
      final day = _digits(s, start + 6, 2);
      if (year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1 && day <= _daysIn(year, month)) {
        return true;
      }
    }
  }
  return false;
}

/// 计算密码强度分值，0 到 14；空串抛 [ArgumentError]
int score(String password) {
  if (password.isEmpty) throw ArgumentError.value(password, 'password', 'password is empty');
  // 整串由更短片段重复组成时，按重复单元评分
  final s = password.substring(0, _repeatUnitLength(password));
  final len = s.length;

  final count = [0, 0, 0, 0];
  for (final c in s.codeUnits) {
    count[_charType(c)]++;
  }
  final num = count[_num], small = count[_small], capital = count[_capital], other = count[_other];
  final classes = count.where((c) => c > 0).length;

  var level = 0;
  // 加分
  if (num > 0) level++;
  if (small > 0) level++;
  if (len > 4 && capital > 0) level++;
  if (len > 6 && other > 0) level++;
  if (len > 4 && classes >= 2) level++;
  if (len > 6 && classes >= 3) level++;
  if (len > 8 && classes == 4) level++;
  if (len > 6 &&
      (num >= 3 && small >= 3 ||
          num >= 3 && capital >= 3 ||
          num >= 3 && other >= 2 ||
          small >= 3 && capital >= 3 ||
          small >= 3 && other >= 2 ||
          capital >= 3 && other >= 2)) {
    level++;
  }
  if (len > 8 &&
      (num >= 2 && small >= 2 && capital >= 2 ||
          num >= 2 && small >= 2 && other >= 2 ||
          num >= 2 && capital >= 2 && other >= 2 ||
          small >= 2 && capital >= 2 && other >= 2)) {
    level++;
  }
  if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++;
  if (other >= 3) level++;
  if (other >= 6) level++;
  if (len > 12) level++;
  if (len >= 16) level++;

  // 减分；只把 A-Z 转小写，不用 toLowerCase()，避免 Unicode 小写改变长度或结果
  final lower = String.fromCharCodes(s.codeUnits.map((c) => c >= 65 && c <= 90 ? c + 32 : c));
  for (final keys in _sequenceKeys) {
    for (var i = 0; i + _sequenceMin <= len; i++) {
      if (keys.contains(lower.substring(i, i + _sequenceMin))) {
        level--;
        break;
      }
    }
  }
  if (num == len || small == len || capital == len) level--;
  if (len % 2 == 0 && _isSameChar(s, 0, len ~/ 2) && _isSameChar(s, len ~/ 2, len)) level--;
  if (_containsDate(s)) level--;
  if (_dictionary.any((w) => lower.contains(w) || w.contains(lower))) level--;
  if (len <= 6) level--;
  if (len <= 4) level--;
  if (len <= 3 || _isSameChar(s, 0, len)) level = 0;
  return level < 0 ? 0 : level;
}

/// 计算密码强度等级；空串抛 [ArgumentError]
Level level(String password) {
  final value = score(password);
  if (value <= 3) return Level.easy;
  if (value <= 6) return Level.medium;
  if (value <= 9) return Level.strong;
  if (value <= 12) return Level.veryStrong;
  return Level.extremelyStrong;
}
