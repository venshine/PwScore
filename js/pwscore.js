// PwScore：密码强度评分。规则见仓库 spec/SPEC.md；JS 字符串本身是 UTF-16，与规则口径一致
// Licensed under the Apache License, Version 2.0

const DICTIONARY = ['password', 'abc123', 'iloveyou', 'adobe123', '123123', 'sunshine',
  '1314520', 'a1b2c3', '123qwe', 'aaa111', 'qweasd', 'admin', 'passwd'];

const SEQUENCES = [
  ['abcdefghijklmnopqrstuvwxyz', 'zyxwvutsrqponmlkjihgfedcba'],
  ['01234567890', '09876543210'],
  ['qwertyuiop', 'poiuytrewq', 'asdfghjkl', 'lkjhgfdsa', 'zxcvbnm', 'mnbvcxz'],
];

const SEQUENCE_MIN = 4;

// 每组序列所有长度为 4 的片段
const SEQUENCE_KEYS = SEQUENCES.map((group) => {
  const keys = new Set();
  for (const seq of group) {
    for (let i = 0; i + SEQUENCE_MIN <= seq.length; i++) keys.add(seq.slice(i, i + SEQUENCE_MIN));
  }
  return keys;
});

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
const OTHER = 3;

function charType(c) {
  if (c >= 48 && c <= 57) return NUM;
  if (c >= 97 && c <= 122) return SMALL;
  if (c >= 65 && c <= 90) return CAPITAL;
  return OTHER;
}

function repeatUnitLength(s) {
  const n = s.length;
  const prefix = new Int32Array(n);
  for (let i = 1; i < n; i++) {
    let k = prefix[i - 1];
    while (k > 0 && s.charCodeAt(i) !== s.charCodeAt(k)) k = prefix[k - 1];
    if (s.charCodeAt(i) === s.charCodeAt(k)) k++;
    prefix[i] = k;
  }
  const period = n - prefix[n - 1];
  return n % period === 0 ? period : n;
}

function isSameChar(s, from, to) {
  for (let i = from + 1; i < to; i++) {
    if (s.charCodeAt(i) !== s.charCodeAt(from)) return false;
  }
  return true;
}

function digits(s, start, count) {
  let value = 0;
  for (let i = start; i < start + count; i++) value = value * 10 + (s.charCodeAt(i) - 48);
  return value;
}

function daysInMonth(year, month) {
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  return month === 2 && leap ? 29 : DAYS_IN_MONTH[month - 1];
}

function containsDate(s) {
  let run = 0;
  for (let i = 0; i < s.length; i++) {
    run = charType(s.charCodeAt(i)) === NUM ? run + 1 : 0;
    if (run >= 8) {
      const start = i - 7;
      const year = digits(s, start, 4);
      const month = digits(s, start + 4, 2);
      const day = digits(s, start + 6, 2);
      if (year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1
        && day <= daysInMonth(year, month)) return true;
    }
  }
  return false;
}

/**
 * 计算密码强度分值，0 到 14
 * @param {string} password 密码，长度按 UTF-16 码元计
 * @returns {number}
 * @throws {TypeError} password 不是字符串（含 null、undefined）
 * @throws {RangeError} password 是空串
 */
export function score(password) {
  if (typeof password !== 'string') throw new TypeError('password is not a string');
  if (password.length === 0) throw new RangeError('password is empty');

  // 整串由更短片段重复组成时，按重复单元评分
  const s = password.slice(0, repeatUnitLength(password));
  const len = s.length;

  const count = [0, 0, 0, 0];
  for (let i = 0; i < len; i++) count[charType(s.charCodeAt(i))]++;
  const [num, small, capital, other] = count;
  const classes = count.filter((c) => c > 0).length;

  let level = 0;
  // 加分
  if (num > 0) level++;
  if (small > 0) level++;
  if (len > 4 && capital > 0) level++;
  if (len > 6 && other > 0) level++;
  if (len > 4 && classes >= 2) level++;
  if (len > 6 && classes >= 3) level++;
  if (len > 8 && classes === 4) level++;
  if (len > 6 && ((num >= 3 && small >= 3) || (num >= 3 && capital >= 3) || (num >= 3 && other >= 2)
    || (small >= 3 && capital >= 3) || (small >= 3 && other >= 2) || (capital >= 3 && other >= 2))) level++;
  if (len > 8 && ((num >= 2 && small >= 2 && capital >= 2) || (num >= 2 && small >= 2 && other >= 2)
    || (num >= 2 && capital >= 2 && other >= 2) || (small >= 2 && capital >= 2 && other >= 2))) level++;
  if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++;
  if (other >= 3) level++;
  if (other >= 6) level++;
  if (len > 12) level++;
  if (len >= 16) level++;

  // 减分；只把 A-Z 转小写，不用 toLowerCase，避免 Unicode 小写改变长度或结果
  const lower = s.replace(/[A-Z]/g, (c) => String.fromCharCode(c.charCodeAt(0) + 32));
  for (const keys of SEQUENCE_KEYS) {
    for (let i = 0; i + SEQUENCE_MIN <= len; i++) {
      if (keys.has(lower.slice(i, i + SEQUENCE_MIN))) {
        level--;
        break;
      }
    }
  }
  if (num === len || small === len || capital === len) level--;
  if (len % 2 === 0 && isSameChar(s, 0, len / 2) && isSameChar(s, len / 2, len)) level--;
  if (containsDate(s)) level--;
  if (DICTIONARY.some((w) => lower.includes(w) || w.includes(lower))) level--;
  if (len <= 6) level--;
  if (len <= 4) level--;
  if (len <= 3 || isSameChar(s, 0, len)) level = 0;
  return Math.max(level, 0);
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
