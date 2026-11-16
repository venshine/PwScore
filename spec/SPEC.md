# PwScore scoring specification

This file is the single source of truth for the scoring rules. Every implementation in this repository follows it, and every implementation must reproduce [`score-snapshot.tsv`](score-snapshot.tsv) exactly. The Java implementation (`java/`) is the reference implementation that generates the snapshot.

## Input

- The password is a sequence of **UTF-16 code units**. Every length, index and character comparison below works on code units, so a character outside the BMP (for example an emoji) counts as 2. Languages whose strings are not UTF-16 convert first:
  - Code points above U+FFFF become a surrogate pair.
  - Byte input (C, C++, Go) is decoded as UTF-8, and every byte of an invalid sequence becomes U+FFFD.
- A missing password (null, nil, undef) and an empty password are errors. How the error is reported follows each language's conventions. Every other input, including whitespace only, gets a score.

## Definitions

- `s`: the input after the repeat reduction below; `len`: its length.
- Character class of a code unit `c`:
  - `num`: `'0' <= c <= '9'`
  - `small`: `'a' <= c <= 'z'`
  - `capital`: `'A' <= c <= 'Z'`
  - `other`: everything else (spaces, symbols, non-ASCII letters and digits, each surrogate)
- `num`, `small`, `capital`, `other`: the number of code units of each class in `s`. `classes`: how many of the four counts are greater than 0.
- `lower`: `s` with only `A-Z` mapped to `a-z`; every other code unit is unchanged, so the length is unchanged.
- `same(a, b)`: every code unit in `s[a, b)` equals `s[a]`.

## Algorithm

1. **Repeat reduction.** If the password is a shorter unit repeated `k >= 2` times, `s` is that shortest unit; otherwise `s` is the password. The unit length is `n - p[n-1]` when `n % (n - p[n-1]) == 0`, where `p` is the KMP prefix function. Implementations must run in linear time.
2. Start with `level = 0` and add 1 for each condition that holds:
   1. `num > 0`
   2. `small > 0`
   3. `len > 4 && capital > 0`
   4. `len > 6 && other > 0`
   5. `len > 4 && classes >= 2`
   6. `len > 6 && classes >= 3`
   7. `len > 8 && classes == 4`
   8. `len > 6 && (any two different classes where each count meets its threshold)`. The thresholds are `num >= 3`, `small >= 3`, `capital >= 3`, `other >= 2`.
   9. `len > 8 && (any three different classes each with count >= 2)`
   10. `len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2`
   11. `other >= 3`
   12. `other >= 6`
   13. `len > 12`
   14. `len >= 16`
3. Subtract 1 for each condition that holds:
   1. **Sequences, once per group.** Subtract once for each group in which some 4 consecutive code units of `lower` equal a substring of one of the group's strings:
      - letters: `abcdefghijklmnopqrstuvwxyz`, `zyxwvutsrqponmlkjihgfedcba`
      - digits: `01234567890`, `09876543210`
      - keyboard: `qwertyuiop`, `poiuytrewq`, `asdfghjkl`, `lkjhgfdsa`, `zxcvbnm`, `mnbvcxz`
   2. `num == len || small == len || capital == len`
   3. `len % 2 == 0 && same(0, len/2) && same(len/2, len)`
   4. **Date.** Some 8 consecutive `num` code units read as `yyyyMMdd` with `1950 <= yyyy <= 2049`, `1 <= MM <= 12` and `1 <= dd <= days in that month`. February has 29 days when `yyyy % 4 == 0 && (yyyy % 100 != 0 || yyyy % 400 == 0)`.
   5. **Dictionary, at most once.** For some word `w`, `lower` contains `w`, or `w` contains `lower`. Words: `password`, `abc123`, `iloveyou`, `adobe123`, `123123`, `sunshine`, `1314520`, `a1b2c3`, `123qwe`, `aaa111`, `qweasd`, `admin`, `passwd`.
   6. `len <= 6`
   7. `len <= 4`
4. If `len <= 3` or `same(0, len)`, set `level = 0`.
5. The score is `max(level, 0)`, an integer from 0 to 14.

## Levels

| Score | Level |
|---|---|
| 0-3 | EASY |
| 4-6 | MEDIUM |
| 7-9 | STRONG |
| 10-12 | VERY_STRONG |
| 13-14 | EXTREMELY_STRONG |

Each language spells the level names in its own style (for example `Level.VERY_STRONG`, `Level.veryStrong`, `:very_strong`).

## Snapshot file

`score-snapshot.tsv` is UTF-8. Lines that are empty or start with `#` are comments. Every other line is `score<TAB>LEVEL<TAB>password`; split on the first two tabs only. To change a rule, update this file, change the Java implementation, run `java/test.sh --generate`, review every changed line in the diff, and then update all other implementations until their tests pass.
