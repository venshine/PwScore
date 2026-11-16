# PwScore scoring specification

This file is the single source of truth for the scoring rules. Every implementation in this repository follows it, and every implementation must reproduce [`score-snapshot.tsv`](score-snapshot.tsv) exactly. The Java implementation (`java/`) is the reference implementation that generates the snapshot.

## Input

- The password is a sequence of **UTF-16 code units**. Lengths, indexes and comparisons below work on code units, except for the effective length, which counts a surrogate pair as one character. Languages whose strings are not UTF-16 convert first:
  - Code points above U+FFFF become a surrogate pair.
  - Byte input (C, C++, Go) is decoded as UTF-8, and every byte of an invalid sequence becomes U+FFFD.
- A missing password (null, nil, undef) and an empty password are errors. How the error is reported follows each language's conventions. Every other input, including whitespace only, gets a score.

## Definitions

- `s`: the password after **fullwidth folding**: every code unit from U+FF01 to U+FF5E is replaced by that unit minus 0xFEE0 (so `Ａ` becomes `A` and `１` becomes `1`). `n`: the number of code units in `s`.
- `len`: the **effective length** of `s`. Scan from `i = 0` while `i < n`:
  - Try `m = 1, 2, ..., 16` with `i + 2m <= n`, and take the first `m` for which `s[i, i+m) == s[i+m, i+2m)`. Let `r >= 2` be the largest count such that the block `s[i, i+m)` repeats `r` times back to back from `i`. Add 2 if `m == 1`, otherwise add the number of characters in the block (a surrogate pair inside the block counts as 1). Then `i += r * m`.
  - If no `m` matches: when `s[i]` is a high surrogate (U+D800-U+DBFF) followed by a low surrogate (U+DC00-U+DFFF), add 1 and `i += 2`; otherwise add 1 and `i += 1`.
  - So `aaaa` counts as 2, `Ab1!Ab1!Ab1!x` as 5, and an emoji as 1.
- Character class of a code unit `c`:
  - `num`: `'0' <= c <= '9'`
  - `small`: `'a' <= c <= 'z'`
  - `capital`: `'A' <= c <= 'Z'`
  - `intl`: `c >= 128` (non-ASCII letters, digits and symbols, each surrogate)
  - `other`: everything else (ASCII spaces, symbols and control characters)
- `num`, `small`, `capital`, `intl`, `other`: the number of **different** code units of each class in `s`. `classes`: how many of the five counts are greater than 0.
- `lower`: `s` with only `A-Z` mapped to `a-z`; every other code unit is unchanged.
- `same(a, b)`: every code unit in `s[a, b)` equals `s[a]`.
- `distinct`: the number of different code units in `s`.
- `leet`: `lower` with these code units replaced one for one: `@`→`a`, `4`→`a`, `0`→`o`, `1`→`i`, `!`→`i`, `3`→`e`, `$`→`s`, `5`→`s`, `7`→`t`.

## Algorithm

1. Start with `level = 0` and add 1 for each condition that holds:
   1. `num > 0`
   2. `small > 0`
   3. `intl > 0`
   4. `len > 4 && capital > 0`
   5. `len > 6 && other > 0`
   6. `len > 4 && classes >= 2`
   7. `len > 6 && classes >= 3`
   8. `len > 8 && classes >= 4`
   9. `len > 6 && (any two different classes where each count meets its threshold)`. The classes and thresholds are `num >= 3`, `small >= 3`, `capital >= 3`, `other >= 2`.
   10. `len > 8 && (any three different classes among num, small, capital, other, each with count >= 2)`
   11. `len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2`
   12. `other >= 3`
   13. `other >= 6`
2. **Length.** Add the points for `len`:

   | `len` | <= 4 | 5 | 6-7 | 8-11 | 12-15 | 16-19 | 20-23 | >= 24 |
   |---|---|---|---|---|---|---|---|---|
   | points | -2 | -1 | 0 | +1 | +2 | +3 | +4 | +5 |

3. Subtract for each condition that holds:
   1. **Sequences.** Subtract 1 for each group in which some 4 consecutive code units of `lower` equal a substring of one of the group's strings or of its reverse. Every position covered by such a window, in any group, is marked as covered.
      - letters: `abcdefghijklmnopqrstuvwxyz`, `acegikmoqsuwy`, `bdfhjlnprtvxz`
      - digits: `01234567890`, `02468`, `13579`
      - keyboard: `qwertyuiop`, `asdfghjkl`, `zxcvbnm`, `1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/`, `qazwsxedcrfvtgbyhnujmikolp`, `1q2w3e4r5t6y7u8i9o0p`, `q1w2e3r4t5y6u7i8o9p0`, `zaq1xsw2cde3vfr4bgt5nhy6mju7`, `!@#$%^&*()_+`
   2. **Mostly sequences.** At least half of the code units are covered (`2 * covered >= n`): subtract 1.
   3. `len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)`: subtract 1.
   4. `n % 2 == 0 && same(0, n/2) && same(n/2, n)`: subtract 1.
   5. **Date or year**, at most once: subtract 1 if any of these holds, where a digit run is a maximal run of `num` code units and a valid date has `1 <= MM <= 12` and `1 <= dd <= days in that month` (February has 29 days when `yyyy % 4 == 0 && (yyyy % 100 != 0 || yyyy % 400 == 0)`):
      - some 8 consecutive digits read as `yyyyMMdd`, `ddMMyyyy` or `MMddyyyy` with `1950 <= yyyy <= 2049` and a valid date;
      - a digit run of exactly 6 reads as `yyMMdd` with a valid date in year `2000 + yy`;
      - a digit run of exactly 4 reads as a year from 1950 to 2049.
   6. **Few distinct characters.** `len >= 8 && distinct <= max(4, floor(len / 3))`: subtract 1.
   7. **Dictionary**, at most once. For some word `w` in the list below, `lower` contains `w`, `w` contains `lower`, or `leet` contains `w`: subtract 2.
4. If `len <= 3` or `same(0, n)`, set `level = 0`.
5. The score is `min(max(level, 0), 14)`, an integer from 0 to 14.

Implementations must run in linear time in `n`.

### Dictionary

```
password passwd admin iloveyou sunshine adobe123 abc123 123123 1314520 a1b2c3 123qwe aaa111 qweasd
welcome letmein login master monkey dragon shadow princess football baseball soccer superman batman
starwars pokemon freedom whatever trustno1 secret hello summer winter spring autumn flower angel lovely
loveyou killer hunter cookie banana orange purple silver golden diamond chocolate computer internet
google apple changeme default guest
woaini iloveu zhang wang yang huang zhao xiaoming laopo laogong baobao nihao wodemima mima qq123
5201314 520520 147258 159753 147852 789456 741852 963852 258369 123321 112233
```

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
