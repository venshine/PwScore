"""PwScore：密码强度评分。规则见仓库 spec/SPEC.md。

Licensed under the Apache License, Version 2.0
"""

from enum import Enum

__all__ = ["Level", "score", "level"]
__version__ = "3.0.0"


class Level(str, Enum):
    """强度等级，按分值从低到高"""

    EASY = "EASY"
    MEDIUM = "MEDIUM"
    STRONG = "STRONG"
    VERY_STRONG = "VERY_STRONG"
    EXTREMELY_STRONG = "EXTREMELY_STRONG"


_DICTIONARY = ("password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
               "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd")

_SEQUENCES = (
    ("abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba"),
    ("01234567890", "09876543210"),
    ("qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz"),
)

_SEQUENCE_MIN = 4

# 每组序列所有长度为 4 的片段，按码元元组存放
_SEQUENCE_KEYS = tuple(
    frozenset(tuple(ord(c) for c in seq[i:i + _SEQUENCE_MIN])
              for seq in group for i in range(len(seq) - _SEQUENCE_MIN + 1))
    for group in _SEQUENCES)

_DICTIONARY_UNITS = tuple(tuple(ord(c) for c in w) for w in _DICTIONARY)

_DAYS_IN_MONTH = (31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

_NUM, _SMALL, _CAPITAL, _OTHER = range(4)


def _utf16_units(s):
    """把 str 转成 UTF-16 码元元组：大于 U+FFFF 的码点拆成代理对，单独的代理码点原样保留"""
    units = []
    for ch in s:
        cp = ord(ch)
        if cp > 0xFFFF:
            cp -= 0x10000
            units.append(0xD800 + (cp >> 10))
            units.append(0xDC00 + (cp & 0x3FF))
        else:
            units.append(cp)
    return tuple(units)


def _char_type(c):
    if 48 <= c <= 57:
        return _NUM
    if 97 <= c <= 122:
        return _SMALL
    if 65 <= c <= 90:
        return _CAPITAL
    return _OTHER


def _repeat_unit_length(s):
    n = len(s)
    prefix = [0] * n
    for i in range(1, n):
        k = prefix[i - 1]
        while k > 0 and s[i] != s[k]:
            k = prefix[k - 1]
        if s[i] == s[k]:
            k += 1
        prefix[i] = k
    period = n - prefix[n - 1]
    return period if n % period == 0 else n


def _is_same_char(s, start, end):
    first = s[start]
    return all(s[i] == first for i in range(start + 1, end))


def _contains(haystack, needle):
    """码元序列 haystack 是否包含连续子序列 needle"""
    m = len(needle)
    return any(haystack[i:i + m] == needle for i in range(len(haystack) - m + 1))


def _days_in_month(year, month):
    leap = year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)
    return 29 if month == 2 and leap else _DAYS_IN_MONTH[month - 1]


def _contains_date(s):
    run = 0
    for i, c in enumerate(s):
        run = run + 1 if _char_type(c) == _NUM else 0
        if run >= 8:
            d = [x - 48 for x in s[i - 7:i + 1]]
            year = d[0] * 1000 + d[1] * 100 + d[2] * 10 + d[3]
            month = d[4] * 10 + d[5]
            day = d[6] * 10 + d[7]
            if 1950 <= year <= 2049 and 1 <= month <= 12 and 1 <= day <= _days_in_month(year, month):
                return True
    return False


def score(password):
    """计算密码强度分值，0 到 14；长度按 UTF-16 码元计。

    :raises TypeError: password 不是 str（含 None）
    :raises ValueError: password 是空串
    """
    if not isinstance(password, str):
        raise TypeError("password is not a str")
    if not password:
        raise ValueError("password is empty")

    units = _utf16_units(password)
    # 整串由更短片段重复组成时，按重复单元评分
    s = units[:_repeat_unit_length(units)]
    n = len(s)

    count = [0, 0, 0, 0]
    for c in s:
        count[_char_type(c)] += 1
    num, small, capital, other = count
    classes = sum(1 for x in count if x > 0)

    level = 0
    # 加分
    if num > 0:
        level += 1
    if small > 0:
        level += 1
    if n > 4 and capital > 0:
        level += 1
    if n > 6 and other > 0:
        level += 1
    if n > 4 and classes >= 2:
        level += 1
    if n > 6 and classes >= 3:
        level += 1
    if n > 8 and classes == 4:
        level += 1
    if n > 6 and (num >= 3 and small >= 3 or num >= 3 and capital >= 3 or num >= 3 and other >= 2
                  or small >= 3 and capital >= 3 or small >= 3 and other >= 2 or capital >= 3 and other >= 2):
        level += 1
    if n > 8 and (num >= 2 and small >= 2 and capital >= 2 or num >= 2 and small >= 2 and other >= 2
                  or num >= 2 and capital >= 2 and other >= 2 or small >= 2 and capital >= 2 and other >= 2):
        level += 1
    if n > 10 and num >= 2 and small >= 2 and capital >= 2 and other >= 2:
        level += 1
    if other >= 3:
        level += 1
    if other >= 6:
        level += 1
    if n > 12:
        level += 1
    if n >= 16:
        level += 1

    # 减分；只把 A-Z 转小写
    lower = tuple(c + 32 if 65 <= c <= 90 else c for c in s)
    for keys in _SEQUENCE_KEYS:
        if any(lower[i:i + _SEQUENCE_MIN] in keys for i in range(n - _SEQUENCE_MIN + 1)):
            level -= 1
    if num == n or small == n or capital == n:
        level -= 1
    if n % 2 == 0 and _is_same_char(s, 0, n // 2) and _is_same_char(s, n // 2, n):
        level -= 1
    if _contains_date(s):
        level -= 1
    if any(_contains(lower, w) or _contains(w, lower) for w in _DICTIONARY_UNITS):
        level -= 1
    if n <= 6:
        level -= 1
    if n <= 4:
        level -= 1
    if n <= 3 or _is_same_char(s, 0, n):
        level = 0
    return max(level, 0)


def level(password):
    """计算密码强度等级，报错同 score"""
    value = score(password)
    if value <= 3:
        return Level.EASY
    if value <= 6:
        return Level.MEDIUM
    if value <= 9:
        return Level.STRONG
    if value <= 12:
        return Level.VERY_STRONG
    return Level.EXTREMELY_STRONG
