//! PwScore：密码强度评分，分值 0 到 14，规则见仓库 spec/SPEC.md。按 UTF-16 码元处理。
//!
//! ```
//! assert_eq!(pwscore::level("2hAj5#mne-ix.86H"), Ok(pwscore::Level::ExtremelyStrong));
//! ```
//!
//! Licensed under the Apache License, Version 2.0

use std::collections::HashSet;
use std::fmt;

/// 强度等级，按分值从低到高
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Level {
    Easy,
    Medium,
    Strong,
    VeryStrong,
    ExtremelyStrong,
}

impl Level {
    /// 与其他语言一致的等级名，如 "VERY_STRONG"
    pub fn name(self) -> &'static str {
        match self {
            Level::Easy => "EASY",
            Level::Medium => "MEDIUM",
            Level::Strong => "STRONG",
            Level::VeryStrong => "VERY_STRONG",
            Level::ExtremelyStrong => "EXTREMELY_STRONG",
        }
    }
}

impl fmt::Display for Level {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.name())
    }
}

/// 密码为空串
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EmptyPassword;

impl fmt::Display for EmptyPassword {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("password is empty")
    }
}

impl std::error::Error for EmptyPassword {}

#[rustfmt::skip]
const DICTIONARY: [&str; 84] = [
    "password", "passwd", "admin", "iloveyou", "sunshine", "adobe123", "abc123", "123123", "1314520", "a1b2c3",
    "123qwe", "aaa111", "qweasd",
    "welcome", "letmein", "login", "master", "monkey", "dragon", "shadow", "princess", "football", "baseball",
    "soccer", "superman", "batman", "starwars", "pokemon", "freedom", "whatever", "trustno1", "secret", "hello",
    "summer", "winter", "spring", "autumn", "flower", "angel", "lovely", "loveyou", "killer", "hunter", "cookie",
    "banana", "orange", "purple", "silver", "golden", "diamond", "chocolate", "computer", "internet", "google",
    "apple", "changeme", "default", "guest",
    "woaini", "iloveu", "zhang", "wang", "yang", "huang", "zhao", "xiaoming", "laopo", "laogong", "baobao",
    "nihao", "wodemima", "mima", "qq123",
    "5201314", "520520", "147258", "159753", "147852", "789456", "741852", "963852", "258369", "123321", "112233",
];

/// 序列分组，每组命中扣 1 分；每个序列的反向自动加入
#[rustfmt::skip]
const SEQUENCES: [&[&str]; 3] = [
    &["abcdefghijklmnopqrstuvwxyz"],
    &["01234567890"],
    &["qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/", "qazwsxedcrfvtgbyhnujmikolp",
      "1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0", "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+"],
];

const SEQUENCE_MIN: usize = 4;
const MAX_BLOCK: usize = 16;
const LEET_FROM: &[u8] = b"@401!3$57";
const LEET_TO: &[u8] = b"aaoiiesst";

const DAYS_IN_MONTH: [u32; 12] = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const NUM: usize = 0;
const SMALL: usize = 1;
const CAPITAL: usize = 2;
const INTL: usize = 3;
const OTHER: usize = 4;

fn char_type(c: u16) -> usize {
    match c {
        0x30..=0x39 => NUM,
        0x61..=0x7A => SMALL,
        0x41..=0x5A => CAPITAL,
        0x80.. => INTL,
        _ => OTHER,
    }
}

fn is_pair(s: &[u16], i: usize, end: usize) -> bool {
    i + 1 < end && (0xD800..=0xDBFF).contains(&s[i]) && (0xDC00..=0xDFFF).contains(&s[i + 1])
}

fn character_count(s: &[u16], from: usize, to: usize) -> usize {
    let (mut i, mut count) = (from, 0);
    while i < to {
        i += if is_pair(s, i, to) { 2 } else { 1 };
        count += 1;
    }
    count
}

/// 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
fn effective_length(s: &[u16]) -> usize {
    let n = s.len();
    let (mut i, mut len) = (0, 0);
    while i < n {
        let block = (1..=MAX_BLOCK)
            .take_while(|&m| i + 2 * m <= n)
            .find(|&m| s[i] == s[i + m] && s[i..i + m] == s[i + m..i + 2 * m]);
        match block {
            Some(m) => {
                let mut r = 2;
                while i + (r + 1) * m <= n && s[i + r * m..i + (r + 1) * m] == s[i..i + m] {
                    r += 1;
                }
                len += if m == 1 {
                    2
                } else {
                    character_count(s, i, i + m)
                };
                i += r * m;
            }
            None => {
                i += if is_pair(s, i, n) { 2 } else { 1 };
                len += 1;
            }
        }
    }
    len
}

fn length_points(len: usize) -> i32 {
    match len {
        0..=4 => -2,
        5 => -1,
        6 | 7 => 0,
        _ => (((len - 8) / 4 + 1) as i32).min(5),
    }
}

fn is_same_char(s: &[u16]) -> bool {
    s.iter().all(|&c| c == s[0])
}

fn contains(haystack: &[u16], needle: &[u16]) -> bool {
    needle.len() <= haystack.len() && haystack.windows(needle.len()).any(|w| w == needle)
}

fn digits(s: &[u16], start: usize, count: usize) -> u32 {
    s[start..start + count]
        .iter()
        .fold(0, |v, &c| v * 10 + u32::from(c - 0x30))
}

fn is_year(year: u32) -> bool {
    (1950..=2049).contains(&year)
}

fn is_valid_date(year: u32, month: u32, day: u32) -> bool {
    if !(1..=12).contains(&month) || day < 1 {
        return false;
    }
    let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    day <= if month == 2 && leap {
        29
    } else {
        DAYS_IN_MONTH[(month - 1) as usize]
    }
}

fn is_date8(s: &[u16], k: usize) -> bool {
    let head = digits(s, k, 4);
    if is_year(head) && is_valid_date(head, digits(s, k + 4, 2), digits(s, k + 6, 2)) {
        return true;
    }
    let tail = digits(s, k + 4, 4);
    is_year(tail)
        && (is_valid_date(tail, digits(s, k + 2, 2), digits(s, k, 2))
            || is_valid_date(tail, digits(s, k, 2), digits(s, k + 2, 2)))
}

/// 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
fn contains_date_or_year(s: &[u16]) -> bool {
    let mut i = 0;
    while i < s.len() {
        if char_type(s[i]) != NUM {
            i += 1;
            continue;
        }
        let start = i;
        while i < s.len() && char_type(s[i]) == NUM {
            i += 1;
        }
        let run = i - start;
        if (start..i).any(|k| k + 8 <= i && is_date8(s, k)) {
            return true;
        }
        if run == 6
            && is_valid_date(
                2000 + digits(s, start, 2),
                digits(s, start + 2, 2),
                digits(s, start + 4, 2),
            )
        {
            return true;
        }
        if run == 4 && is_year(digits(s, start, 4)) {
            return true;
        }
    }
    false
}

fn ascii_units(s: &str) -> Vec<u16> {
    s.bytes().map(u16::from).collect()
}

/// 计算密码强度分值，0 到 14；空串返回 `Err(EmptyPassword)`
// 组合条件保持与 SPEC.md 逐条对应，不按 clippy 建议化简
#[allow(clippy::nonminimal_bool)]
pub fn score(password: &str) -> Result<u8, EmptyPassword> {
    if password.is_empty() {
        return Err(EmptyPassword);
    }
    // 全角 ASCII 还原为半角
    let s: Vec<u16> = password
        .encode_utf16()
        .map(|c| {
            if (0xFF01..=0xFF5E).contains(&c) {
                c - 0xFEE0
            } else {
                c
            }
        })
        .collect();
    let n = s.len();
    let len = effective_length(&s);

    // 各类统计「不同字符」的个数
    let mut sets: [HashSet<u16>; 5] = Default::default();
    for &c in &s {
        sets[char_type(c)].insert(c);
    }
    let (num, small, capital, intl, other) = (
        sets[NUM].len(),
        sets[SMALL].len(),
        sets[CAPITAL].len(),
        sets[INTL].len(),
        sets[OTHER].len(),
    );
    let classes = sets.iter().filter(|set| !set.is_empty()).count();
    let distinct = s.iter().collect::<HashSet<_>>().len();

    let gains = [
        num > 0,
        small > 0,
        intl > 0,
        len > 4 && capital > 0,
        len > 6 && other > 0,
        len > 4 && classes >= 2,
        len > 6 && classes >= 3,
        len > 8 && classes >= 4,
        len > 6
            && (num >= 3 && small >= 3
                || num >= 3 && capital >= 3
                || num >= 3 && other >= 2
                || small >= 3 && capital >= 3
                || small >= 3 && other >= 2
                || capital >= 3 && other >= 2),
        len > 8
            && (num >= 2 && small >= 2 && capital >= 2
                || num >= 2 && small >= 2 && other >= 2
                || num >= 2 && capital >= 2 && other >= 2
                || small >= 2 && capital >= 2 && other >= 2),
        len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2,
        other >= 3,
        other >= 6,
    ];
    let mut level = gains.iter().filter(|&&g| g).count() as i32 + length_points(len);

    // 减分；只把 A-Z 转小写
    let lower: Vec<u16> = s
        .iter()
        .map(|&c| {
            if (0x41..=0x5A).contains(&c) {
                c + 32
            } else {
                c
            }
        })
        .collect();
    let leet: Vec<u16> = lower
        .iter()
        .map(
            |&c| match LEET_FROM.iter().position(|&b| u16::from(b) == c) {
                Some(k) => u16::from(LEET_TO[k]),
                None => c,
            },
        )
        .collect();
    let mut covered = vec![false; n];
    for group in SEQUENCES.iter() {
        let keys: HashSet<Vec<u16>> = group
            .iter()
            .flat_map(|seq| {
                [
                    ascii_units(seq),
                    ascii_units(&seq.chars().rev().collect::<String>()),
                ]
            })
            .flat_map(|u| {
                u.windows(SEQUENCE_MIN)
                    .map(<[u16]>::to_vec)
                    .collect::<Vec<_>>()
            })
            .collect();
        let mut hit = false;
        for i in 0..n.saturating_sub(SEQUENCE_MIN - 1) {
            if keys.contains(&lower[i..i + SEQUENCE_MIN]) {
                hit = true;
                covered[i..i + SEQUENCE_MIN]
                    .iter_mut()
                    .for_each(|b| *b = true);
            }
        }
        if hit {
            level -= 1;
        }
    }
    if 2 * covered.iter().filter(|&&b| b).count() >= n {
        level -= 1;
    }
    if len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0) {
        level -= 1;
    }
    if n % 2 == 0 && is_same_char(&s[..n / 2]) && is_same_char(&s[n / 2..]) {
        level -= 1;
    }
    if contains_date_or_year(&s) {
        level -= 1;
    }
    if len >= 8 && distinct <= 4.max(len / 3) {
        level -= 1;
    }
    if DICTIONARY.iter().any(|w| {
        let w = ascii_units(w);
        contains(&lower, &w) || contains(&w, &lower) || contains(&leet, &w)
    }) {
        level -= 2;
    }
    if len <= 3 || is_same_char(&s) {
        level = 0;
    }
    Ok(level.clamp(0, 14) as u8)
}

/// 计算密码强度等级；空串返回 `Err(EmptyPassword)`
pub fn level(password: &str) -> Result<Level, EmptyPassword> {
    Ok(match score(password)? {
        0..=3 => Level::Easy,
        4..=6 => Level::Medium,
        7..=9 => Level::Strong,
        10..=12 => Level::VeryStrong,
        _ => Level::ExtremelyStrong,
    })
}
