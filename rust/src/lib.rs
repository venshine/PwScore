//! PwScore：密码强度评分，分值 0 到 14，规则见仓库 spec/SPEC.md。长度按 UTF-16 码元计。
//!
//! ```
//! assert_eq!(pwscore::level("2hAj5#mne-ix.86H"), Ok(pwscore::Level::ExtremelyStrong));
//! ```
//!
//! Licensed under the Apache License, Version 2.0

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

const DICTIONARY: [&str; 13] = [
    "password", "abc123", "iloveyou", "adobe123", "123123", "sunshine", "1314520", "a1b2c3",
    "123qwe", "aaa111", "qweasd", "admin", "passwd",
];

const SEQUENCES: [&[&str]; 3] = [
    &["abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba"],
    &["01234567890", "09876543210"],
    &[
        "qwertyuiop",
        "poiuytrewq",
        "asdfghjkl",
        "lkjhgfdsa",
        "zxcvbnm",
        "mnbvcxz",
    ],
];

const SEQUENCE_MIN: usize = 4;

const DAYS_IN_MONTH: [u32; 12] = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const NUM: usize = 0;
const SMALL: usize = 1;
const CAPITAL: usize = 2;
const OTHER: usize = 3;

fn char_type(c: u16) -> usize {
    match c {
        0x30..=0x39 => NUM,
        0x61..=0x7A => SMALL,
        0x41..=0x5A => CAPITAL,
        _ => OTHER,
    }
}

fn repeat_unit_length(s: &[u16]) -> usize {
    let n = s.len();
    let mut prefix = vec![0usize; n];
    for i in 1..n {
        let mut k = prefix[i - 1];
        while k > 0 && s[i] != s[k] {
            k = prefix[k - 1];
        }
        if s[i] == s[k] {
            k += 1;
        }
        prefix[i] = k;
    }
    let period = n - prefix[n - 1];
    if n % period == 0 {
        period
    } else {
        n
    }
}

fn is_same_char(s: &[u16]) -> bool {
    s.iter().all(|&c| c == s[0])
}

fn contains(haystack: &[u16], needle: &[u16]) -> bool {
    needle.len() <= haystack.len() && haystack.windows(needle.len()).any(|w| w == needle)
}

fn ascii_units(s: &str) -> Vec<u16> {
    s.bytes().map(u16::from).collect()
}

fn days_in_month(year: u32, month: u32) -> u32 {
    let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    if month == 2 && leap {
        29
    } else {
        DAYS_IN_MONTH[(month - 1) as usize]
    }
}

fn contains_date(s: &[u16]) -> bool {
    let mut run = 0;
    for i in 0..s.len() {
        run = if char_type(s[i]) == NUM { run + 1 } else { 0 };
        if run >= 8 {
            let d: Vec<u32> = s[i - 7..=i].iter().map(|&c| u32::from(c - 0x30)).collect();
            let year = d[0] * 1000 + d[1] * 100 + d[2] * 10 + d[3];
            let month = d[4] * 10 + d[5];
            let day = d[6] * 10 + d[7];
            if (1950..=2049).contains(&year)
                && (1..=12).contains(&month)
                && day >= 1
                && day <= days_in_month(year, month)
            {
                return true;
            }
        }
    }
    false
}

/// 计算密码强度分值，0 到 14；空串返回 `Err(EmptyPassword)`
// 组合条件保持与 SPEC.md 逐条对应，不按 clippy 建议化简
#[allow(clippy::nonminimal_bool)]
pub fn score(password: &str) -> Result<u8, EmptyPassword> {
    if password.is_empty() {
        return Err(EmptyPassword);
    }
    let units: Vec<u16> = password.encode_utf16().collect();
    // 整串由更短片段重复组成时，按重复单元评分
    let s = &units[..repeat_unit_length(&units)];
    let n = s.len();

    let mut count = [0usize; 4];
    for &c in s {
        count[char_type(c)] += 1;
    }
    let (num, small, capital, other) = (count[NUM], count[SMALL], count[CAPITAL], count[OTHER]);
    let classes = count.iter().filter(|&&c| c > 0).count();

    let mut level: i32 = 0;
    let gains = [
        num > 0,
        small > 0,
        n > 4 && capital > 0,
        n > 6 && other > 0,
        n > 4 && classes >= 2,
        n > 6 && classes >= 3,
        n > 8 && classes == 4,
        n > 6
            && (num >= 3 && small >= 3
                || num >= 3 && capital >= 3
                || num >= 3 && other >= 2
                || small >= 3 && capital >= 3
                || small >= 3 && other >= 2
                || capital >= 3 && other >= 2),
        n > 8
            && (num >= 2 && small >= 2 && capital >= 2
                || num >= 2 && small >= 2 && other >= 2
                || num >= 2 && capital >= 2 && other >= 2
                || small >= 2 && capital >= 2 && other >= 2),
        n > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2,
        other >= 3,
        other >= 6,
        n > 12,
        n >= 16,
    ];
    level += gains.iter().filter(|&&g| g).count() as i32;

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
    for group in SEQUENCES.iter() {
        let keys: Vec<Vec<u16>> = group
            .iter()
            .flat_map(|seq| {
                ascii_units(seq)
                    .windows(SEQUENCE_MIN)
                    .map(<[u16]>::to_vec)
                    .collect::<Vec<_>>()
            })
            .collect();
        if lower
            .windows(SEQUENCE_MIN)
            .any(|w| keys.iter().any(|k| k.as_slice() == w))
        {
            level -= 1;
        }
    }
    if num == n || small == n || capital == n {
        level -= 1;
    }
    if n % 2 == 0 && is_same_char(&s[..n / 2]) && is_same_char(&s[n / 2..]) {
        level -= 1;
    }
    if contains_date(s) {
        level -= 1;
    }
    if DICTIONARY.iter().any(|w| {
        let w = ascii_units(w);
        contains(&lower, &w) || contains(&w, &lower)
    }) {
        level -= 1;
    }
    if n <= 6 {
        level -= 1;
    }
    if n <= 4 {
        level -= 1;
    }
    if n <= 3 || is_same_char(s) {
        level = 0;
    }
    Ok(level.max(0) as u8)
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
