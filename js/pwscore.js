// PwScore：密码强度评分。规则见仓库 spec/SPEC.md；JS 字符串本身是 UTF-16，与规则口径一致
// Licensed under the Apache License, Version 2.0

const DICTIONARY = [
  'password', 'passwd', 'admin', 'iloveyou', 'sunshine', 'adobe123', 'abc123', '123123', '1314520', 'a1b2c3', '123qwe',
  'aaa111', 'qweasd',
  'welcome', 'letmein', 'login', 'master', 'monkey', 'dragon', 'shadow', 'princess', 'football', 'baseball', 'soccer',
  'superman', 'batman', 'starwars', 'pokemon', 'freedom', 'whatever', 'trustno1', 'secret', 'hello', 'summer', 'winter',
  'spring', 'autumn', 'flower', 'angel', 'lovely', 'loveyou', 'killer', 'hunter', 'cookie', 'banana', 'orange', 'purple',
  'silver', 'golden', 'diamond', 'chocolate', 'computer', 'internet', 'google', 'apple', 'changeme', 'default', 'guest',
  'woaini', 'iloveu', 'zhang', 'wang', 'yang', 'huang', 'zhao', 'xiaoming', 'laopo', 'laogong', 'baobao', 'nihao',
  'wodemima', 'mima', 'qq123',
  '5201314', '520520', '147258', '159753', '147852', '789456', '741852', '963852', '258369', '123321', '112233',
];

// 序列分组，每组命中扣 1 分；每个序列的反向自动加入
const SEQUENCES = [
  ['abcdefghijklmnopqrstuvwxyz'],
  ['01234567890'],
  ['qwertyuiop', 'asdfghjkl', 'zxcvbnm', '1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/', 'qazwsxedcrfvtgbyhnujmikolp',
    '1q2w3e4r5t6y7u8i9o0p', 'q1w2e3r4t5y6u7i8o9p0', 'zaq1xsw2cde3vfr4bgt5nhy6mju7', '!@#$%^&*()_+'],
];

const SEQUENCE_MIN = 4;
const MAX_BLOCK = 16;

// 每组序列（含反向）所有长度为 4 的片段
const SEQUENCE_KEYS = SEQUENCES.map((group) => {
  const keys = new Set();
  for (const seq of group) {
    for (const s of [seq, [...seq].reverse().join('')]) {
      for (let i = 0; i + SEQUENCE_MIN <= s.length; i++) keys.add(s.slice(i, i + SEQUENCE_MIN));
    }
  }
  return keys;
});

const LEET_FROM = '@401!3$57';
const LEET_TO = 'aaoiiesst';
const DAYS_IN_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

/** 强度等级，按分值从低到高 */
export const Level = Object.freeze({
  EASY: 'EASY',
  MEDIUM: 'MEDIUM',
  STRONG: 'STRONG',
  VERY_STRONG: 'VERY_STRONG',
  EXTREMELY_STRONG: 'EXTREMELY_STRONG',
});

const NUM = 0;
const SMALL = 1;
const CAPITAL = 2;
const INTL = 3;
const OTHER = 4;

function charType(c) {
  if (c >= 48 && c <= 57) return NUM;
  if (c >= 97 && c <= 122) return SMALL;
  if (c >= 65 && c <= 90) return CAPITAL;
  if (c >= 128) return INTL;
  return OTHER;
}

const isHigh = (c) => c >= 0xd800 && c <= 0xdbff;
const isLow = (c) => c >= 0xdc00 && c <= 0xdfff;

function regionEquals(s, a, b, count) {
  for (let k = 0; k < count; k++) if (s[a + k] !== s[b + k]) return false;
  return true;
}

function characterCount(s, from, to) {
  let count = 0;
  for (let i = from; i < to; i++) {
    if (i + 1 < to && isHigh(s[i]) && isLow(s[i + 1])) i++;
    count++;
  }
  return count;
}

// 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
function effectiveLength(s) {
  const n = s.length;
  let len = 0;
  let i = 0;
  while (i < n) {
    let block = 0;
    for (let m = 1; m <= MAX_BLOCK && i + 2 * m <= n; m++) {
      if (s[i] === s[i + m] && regionEquals(s, i, i + m, m)) {
        block = m;
        break;
      }
    }
    if (block > 0) {
      let r = 2;
      while (i + (r + 1) * block <= n && regionEquals(s, i, i + r * block, block)) r++;
      len += block === 1 ? 2 : characterCount(s, i, i + block);
      i += r * block;
    } else {
      i += i + 1 < n && isHigh(s[i]) && isLow(s[i + 1]) ? 2 : 1;
      len++;
    }
  }
  return len;
}

function lengthPoints(len) {
  if (len <= 4) return -2;
  if (len === 5) return -1;
  if (len < 8) return 0;
  return Math.min(Math.floor((len - 8) / 4) + 1, 5);
}

function isSameChar(s, from, to) {
  for (let i = from + 1; i < to; i++) if (s[i] !== s[from]) return false;
  return true;
}

function digits(s, start, count) {
  let value = 0;
  for (let i = start; i < start + count; i++) value = value * 10 + (s[i] - 48);
  return value;
}

const isYear = (y) => y >= 1950 && y <= 2049;

function isValidDate(year, month, day) {
  if (month < 1 || month > 12 || day < 1) return false;
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  return day <= (month === 2 && leap ? 29 : DAYS_IN_MONTH[month - 1]);
}

function isDate8(s, k) {
  const head = digits(s, k, 4);
  if (isYear(head) && isValidDate(head, digits(s, k + 4, 2), digits(s, k + 6, 2))) return true;
  const tail = digits(s, k + 4, 4);
  return isYear(tail) && (isValidDate(tail, digits(s, k + 2, 2), digits(s, k, 2))
    || isValidDate(tail, digits(s, k, 2), digits(s, k + 2, 2)));
}

// 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
function containsDateOrYear(s) {
  let i = 0;
  while (i < s.length) {
    if (charType(s[i]) !== NUM) {
      i++;
      continue;
    }
    const start = i;
    while (i < s.length && charType(s[i]) === NUM) i++;
    const run = i - start;
    for (let k = start; k + 8 <= i; k++) if (isDate8(s, k)) return true;
    if (run === 6 && isValidDate(2000 + digits(s, start, 2), digits(s, start + 2, 2), digits(s, start + 4, 2))) return true;
    if (run === 4 && isYear(digits(s, start, 4))) return true;
  }
  return false;
}

/**
 * 计算密码强度分值，0 到 14
 * @param {string} password 密码，按 UTF-16 码元处理
 * @returns {number}
 * @throws {TypeError} password 不是字符串（含 null、undefined）
 * @throws {RangeError} password 是空串
 */
export function score(password) {
  if (typeof password !== 'string') throw new TypeError('password is not a string');
  if (password.length === 0) throw new RangeError('password is empty');

  // 码元数组；全角 ASCII 还原为半角
  const n = password.length;
  const s = new Array(n);
  for (let i = 0; i < n; i++) {
    const c = password.charCodeAt(i);
    s[i] = c >= 0xff01 && c <= 0xff5e ? c - 0xfee0 : c;
  }
  const len = effectiveLength(s);

  // 各类统计「不同字符」的个数
  const sets = [new Set(), new Set(), new Set(), new Set(), new Set()];
  for (const c of s) sets[charType(c)].add(c);
  const [num, small, capital, intl, other] = sets.map((x) => x.size);
  const classes = sets.filter((x) => x.size > 0).length;
  const distinct = new Set(s).size;

  let level = 0;
  // 加分
  if (num > 0) level++;
  if (small > 0) level++;
  if (intl > 0) level++;
  if (len > 4 && capital > 0) level++;
  if (len > 6 && other > 0) level++;
  if (len > 4 && classes >= 2) level++;
  if (len > 6 && classes >= 3) level++;
  if (len > 8 && classes >= 4) level++;
  if (len > 6 && ((num >= 3 && small >= 3) || (num >= 3 && capital >= 3) || (num >= 3 && other >= 2)
    || (small >= 3 && capital >= 3) || (small >= 3 && other >= 2) || (capital >= 3 && other >= 2))) level++;
  if (len > 8 && ((num >= 2 && small >= 2 && capital >= 2) || (num >= 2 && small >= 2 && other >= 2)
    || (num >= 2 && capital >= 2 && other >= 2) || (small >= 2 && capital >= 2 && other >= 2))) level++;
  if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++;
  if (other >= 3) level++;
  if (other >= 6) level++;
  level += lengthPoints(len);

  // 减分；只把 A-Z 转小写，不用 toLowerCase，避免 Unicode 小写改变长度或结果
  const lowerUnits = s.map((c) => (c >= 65 && c <= 90 ? c + 32 : c));
  const lower = unitsToString(lowerUnits);
  const covered = new Uint8Array(n);
  for (const keys of SEQUENCE_KEYS) {
    let hit = false;
    for (let i = 0; i + SEQUENCE_MIN <= n; i++) {
      if (keys.has(lower.slice(i, i + SEQUENCE_MIN))) {
        hit = true;
        covered.fill(1, i, i + SEQUENCE_MIN);
      }
    }
    if (hit) level--;
  }
  let coveredCount = 0;
  for (const b of covered) coveredCount += b;
  if (2 * coveredCount >= n) level--;
  if (len < 6 && classes === 1 && (num > 0 || small > 0 || capital > 0)) level--;
  if (n % 2 === 0 && isSameChar(s, 0, n / 2) && isSameChar(s, n / 2, n)) level--;
  if (containsDateOrYear(s)) level--;
  if (len >= 8 && distinct <= Math.max(4, Math.floor(len / 3))) level--;
  let leet = '';
  for (const ch of lower) {
    const k = LEET_FROM.indexOf(ch);
    leet += k >= 0 ? LEET_TO[k] : ch;
  }
  if (DICTIONARY.some((w) => lower.includes(w) || w.includes(lower) || leet.includes(w))) level -= 2;
  if (len <= 3 || isSameChar(s, 0, n)) level = 0;
  return Math.min(Math.max(level, 0), 14);
}

// 码元数组转字符串，分块避免超长输入时参数过多
function unitsToString(units) {
  let out = '';
  for (let i = 0; i < units.length; i += 8192) out += String.fromCharCode.apply(null, units.slice(i, i + 8192));
  return out;
}

/**
 * 计算密码强度等级
 * @param {string} password
 * @returns {'EASY'|'MEDIUM'|'STRONG'|'VERY_STRONG'|'EXTREMELY_STRONG'}
 * @throws 同 score
 */
export function level(password) {
  return levelOf(score(password));
}

function levelOf(value) {
  if (value <= 3) return Level.EASY;
  if (value <= 6) return Level.MEDIUM;
  if (value <= 9) return Level.STRONG;
  if (value <= 12) return Level.VERY_STRONG;
  return Level.EXTREMELY_STRONG;
}
