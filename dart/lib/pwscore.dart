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

// dart format off
const _dictionary = [
  'password',
  'passwd',
  'admin',
  'iloveyou',
  'sunshine',
  'adobe123',
  'abc123',
  '123123',
  '1314520',
  'a1b2c3',
  '123qwe',
  'aaa111',
  'qweasd',
  'welcome',
  'letmein',
  'login',
  'master',
  'monkey',
  'dragon',
  'shadow',
  'princess',
  'football',
  'baseball',
  'soccer',
  'superman',
  'batman',
  'starwars',
  'pokemon',
  'freedom',
  'whatever',
  'trustno1',
  'secret',
  'hello',
  'summer',
  'winter',
  'spring',
  'autumn',
  'flower',
  'angel',
  'lovely',
  'loveyou',
  'killer',
  'hunter',
  'cookie',
  'banana',
  'orange',
  'purple',
  'silver',
  'golden',
  'diamond',
  'chocolate',
  'computer',
  'internet',
  'google',
  'apple',
  'changeme',
  'default',
  'guest',
  'woaini',
  'iloveu',
  'zhang',
  'wang',
  'yang',
  'huang',
  'zhao',
  'xiaoming',
  'laopo',
  'laogong',
  'baobao',
  'nihao',
  'wodemima',
  'mima',
  'qq123',
  '5201314',
  '520520',
  '147258',
  '159753',
  '147852',
  '789456',
  '741852',
  '963852',
  '258369',
  '123321',
  '112233',
];

/// 序列分组，每组命中扣 1 分；每个序列的反向自动加入
const _sequences = [
  ['abcdefghijklmnopqrstuvwxyz'],
  ['01234567890'],
  [
    'qwertyuiop',
    'asdfghjkl',
    'zxcvbnm',
    '1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/',
    'qazwsxedcrfvtgbyhnujmikolp',
    '1q2w3e4r5t6y7u8i9o0p',
    'q1w2e3r4t5y6u7i8o9p0',
    'zaq1xsw2cde3vfr4bgt5nhy6mju7',
    r'!@#$%^&*()_+'
  ],
];
// dart format on

const _sequenceMin = 4;
const _maxBlock = 16;
const _leetFrom = r'@401!3$57';
const _leetTo = 'aaoiiesst';

/// 每组序列（含反向）所有长度为 4 的片段
final List<Set<String>> _sequenceKeys = [
  for (final group in _sequences)
    {
      for (final base in group)
        for (final seq in [base, String.fromCharCodes(base.codeUnits.reversed)])
          for (var i = 0; i + _sequenceMin <= seq.length; i++) seq.substring(i, i + _sequenceMin),
    },
];

const _daysInMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const _num = 0;
const _small = 1;
const _capital = 2;
const _intl = 3;
const _other = 4;

int _charType(int c) {
  if (c >= 48 && c <= 57) return _num;
  if (c >= 97 && c <= 122) return _small;
  if (c >= 65 && c <= 90) return _capital;
  if (c >= 128) return _intl;
  return _other;
}

bool _isPair(List<int> s, int i, int end) =>
    i + 1 < end && s[i] >= 0xD800 && s[i] <= 0xDBFF && s[i + 1] >= 0xDC00 && s[i + 1] <= 0xDFFF;

bool _regionEquals(List<int> s, int a, int b, int count) {
  for (var k = 0; k < count; k++) {
    if (s[a + k] != s[b + k]) return false;
  }
  return true;
}

int _characterCount(List<int> s, int from, int to) {
  var count = 0;
  for (var i = from; i < to; i += _isPair(s, i, to) ? 2 : 1) {
    count++;
  }
  return count;
}

/// 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
int _effectiveLength(List<int> s) {
  final n = s.length;
  var len = 0;
  var i = 0;
  while (i < n) {
    var block = 0;
    for (var m = 1; m <= _maxBlock && i + 2 * m <= n; m++) {
      if (s[i] == s[i + m] && _regionEquals(s, i, i + m, m)) {
        block = m;
        break;
      }
    }
    if (block > 0) {
      var r = 2;
      while (i + (r + 1) * block <= n && _regionEquals(s, i, i + r * block, block)) {
        r++;
      }
      len += block == 1 ? 2 : _characterCount(s, i, i + block);
      i += r * block;
    } else {
      i += _isPair(s, i, n) ? 2 : 1;
      len++;
    }
  }
  return len;
}

int _lengthPoints(int len) {
  if (len <= 4) return -2;
  if (len == 5) return -1;
  if (len < 8) return 0;
  final p = (len - 8) ~/ 4 + 1;
  return p < 5 ? p : 5;
}

bool _isSameChar(List<int> s, int from, int to) {
  for (var i = from + 1; i < to; i++) {
    if (s[i] != s[from]) return false;
  }
  return true;
}

int _digits(List<int> s, int start, int count) {
  var value = 0;
  for (var i = start; i < start + count; i++) {
    value = value * 10 + (s[i] - 48);
  }
  return value;
}

bool _isYear(int year) => year >= 1950 && year <= 2049;

bool _isValidDate(int year, int month, int day) {
  if (month < 1 || month > 12 || day < 1) return false;
  final leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
  return day <= (month == 2 && leap ? 29 : _daysInMonth[month - 1]);
}

bool _isDate8(List<int> s, int k) {
  final head = _digits(s, k, 4);
  if (_isYear(head) && _isValidDate(head, _digits(s, k + 4, 2), _digits(s, k + 6, 2))) return true;
  final tail = _digits(s, k + 4, 4);
  return _isYear(tail) &&
      (_isValidDate(tail, _digits(s, k + 2, 2), _digits(s, k, 2)) ||
          _isValidDate(tail, _digits(s, k, 2), _digits(s, k + 2, 2)));
}

/// 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
bool _containsDateOrYear(List<int> s) {
  var i = 0;
  while (i < s.length) {
    if (_charType(s[i]) != _num) {
      i++;
      continue;
    }
    final start = i;
    while (i < s.length && _charType(s[i]) == _num) {
      i++;
    }
    final run = i - start;
    for (var k = start; k + 8 <= i; k++) {
      if (_isDate8(s, k)) return true;
    }
    if (run == 6 && _isValidDate(2000 + _digits(s, start, 2), _digits(s, start + 2, 2), _digits(s, start + 4, 2))) {
      return true;
    }
    if (run == 4 && _isYear(_digits(s, start, 4))) return true;
  }
  return false;
}

/// 计算密码强度分值，0 到 14；空串抛 [ArgumentError]
int score(String password) {
  if (password.isEmpty) throw ArgumentError.value(password, 'password', 'password is empty');
  // 全角 ASCII 还原为半角
  final s = [for (final c in password.codeUnits) c >= 0xFF01 && c <= 0xFF5E ? c - 0xFEE0 : c];
  final n = s.length;
  final len = _effectiveLength(s);

  // 各类统计「不同字符」的个数
  final sets = List.generate(5, (_) => <int>{});
  for (final c in s) {
    sets[_charType(c)].add(c);
  }
  final num = sets[_num].length, small = sets[_small].length, capital = sets[_capital].length;
  final intl = sets[_intl].length, other = sets[_other].length;
  final classes = sets.where((x) => x.isNotEmpty).length;
  final distinct = s.toSet().length;

  var level = 0;
  // 加分
  if (num > 0) level++;
  if (small > 0) level++;
  if (intl > 0) level++;
  if (len > 4 && capital > 0) level++;
  if (len > 6 && other > 0) level++;
  if (len > 4 && classes >= 2) level++;
  if (len > 6 && classes >= 3) level++;
  if (len > 8 && classes >= 4) level++;
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
  level += _lengthPoints(len);

  // 减分；只把 A-Z 转小写，不用 toLowerCase()，避免 Unicode 小写改变长度或结果
  final lowerUnits = [for (final c in s) c >= 65 && c <= 90 ? c + 32 : c];
  final lower = String.fromCharCodes(lowerUnits);
  final covered = List<bool>.filled(n, false);
  for (final keys in _sequenceKeys) {
    var hit = false;
    for (var i = 0; i + _sequenceMin <= n; i++) {
      if (keys.contains(lower.substring(i, i + _sequenceMin))) {
        hit = true;
        covered.fillRange(i, i + _sequenceMin, true);
      }
    }
    if (hit) level--;
  }
  if (2 * covered.where((b) => b).length >= n) level--;
  if (len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)) level--;
  if (n % 2 == 0 && _isSameChar(s, 0, n ~/ 2) && _isSameChar(s, n ~/ 2, n)) level--;
  if (_containsDateOrYear(s)) level--;
  if (len >= 8 && distinct <= (len ~/ 3 > 4 ? len ~/ 3 : 4)) level--;
  final leet = String.fromCharCodes([
    for (final c in lowerUnits)
      _leetFrom.codeUnits.contains(c) ? _leetTo.codeUnitAt(_leetFrom.codeUnits.indexOf(c)) : c,
  ]);
  if (_dictionary.any((w) => lower.contains(w) || w.contains(lower) || leet.contains(w))) level -= 2;
  if (len <= 3 || _isSameChar(s, 0, n)) level = 0;
  return level.clamp(0, 14);
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
