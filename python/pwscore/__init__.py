"""PwScore：密码强度评分。规则见仓库 spec/SPEC.md。

Licensed under the Apache License, Version 2.0
"""

from enum import Enum

__all__ = ["Level", "score", "level"]
__version__ = "3.1.0"


class Level(str, Enum):
    """强度等级，按分值从低到高"""

    EASY = "EASY"
    MEDIUM = "MEDIUM"
    STRONG = "STRONG"
    VERY_STRONG = "VERY_STRONG"
    EXTREMELY_STRONG = "EXTREMELY_STRONG"


_DICTIONARY = (
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
)

# 序列分组，每组命中扣 1 分；每个序列的反向自动加入
_SEQUENCES = (
    ("abcdefghijklmnopqrstuvwxyz", "acegikmoqsuwy", "bdfhjlnprtvxz"),
    ("01234567890", "02468", "13579"),
    ("qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/", "qazwsxedcrfvtgbyhnujmikolp",
     "1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0", "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+"),
)

_SEQUENCE_MIN = 4
_MAX_BLOCK = 16

# 每组序列（含反向）所有长度为 4 的片段
_SEQUENCE_KEYS = tuple(
    frozenset(seq[i:i + _SEQUENCE_MIN]
              for base in group for seq in (base, base[::-1])
              for i in range(len(seq) - _SEQUENCE_MIN + 1))
    for group in _SEQUENCES)

_LEET = str.maketrans("@401!3$57", "aaoiiesst")

_DAYS_IN_MONTH = (31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

_NUM, _SMALL, _CAPITAL, _INTL, _OTHER = range(5)


def _utf16_units(s):
    """把 str 转成 UTF-16 码元列表：大于 U+FFFF 的码点拆成代理对；全角 ASCII 还原为半角"""
    units = []
    for ch in s:
        cp = ord(ch)
        if cp > 0xFFFF:
            cp -= 0x10000
            units.append(0xD800 + (cp >> 10))
            units.append(0xDC00 + (cp & 0x3FF))
        elif 0xFF01 <= cp <= 0xFF5E:
            units.append(cp - 0xFEE0)
        else:
            units.append(cp)
    return units


def _char_type(c):
    if 48 <= c <= 57:
        return _NUM
    if 97 <= c <= 122:
        return _SMALL
    if 65 <= c <= 90:
        return _CAPITAL
    if c >= 128:
        return _INTL
    return _OTHER


def _is_pair(s, i, end):
    return i + 1 < end and 0xD800 <= s[i] <= 0xDBFF and 0xDC00 <= s[i + 1] <= 0xDFFF


def _character_count(s, start, end):
    count = 0
    i = start
    while i < end:
        i += 2 if _is_pair(s, i, end) else 1
        count += 1
    return count


def _effective_length(s):
    """有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1"""
    n = len(s)
    length = 0
    i = 0
    while i < n:
        block = 0
        for m in range(1, min(_MAX_BLOCK, (n - i) // 2) + 1):
            if s[i] == s[i + m] and s[i:i + m] == s[i + m:i + 2 * m]:
                block = m
                break
        if block:
            unit = s[i:i + block]
            r = 2
            while i + (r + 1) * block <= n and s[i + r * block:i + (r + 1) * block] == unit:
                r += 1
            length += 2 if block == 1 else _character_count(s, i, i + block)
            i += r * block
        else:
            i += 2 if _is_pair(s, i, n) else 1
            length += 1
    return length


def _length_points(length):
    if length <= 4:
        return -2
    if length == 5:
        return -1
    if length < 8:
        return 0
    return min((length - 8) // 4 + 1, 5)


def _is_same_char(s, start, end):
    first = s[start]
    return all(s[i] == first for i in range(start + 1, end))


def _is_year(year):
    return 1950 <= year <= 2049


def _is_valid_date(year, month, day):
    if not 1 <= month <= 12 or day < 1:
        return False
    leap = year % 4 == 0 and (year % 100 != 0 or year % 400 == 0)
    return day <= (29 if month == 2 and leap else _DAYS_IN_MONTH[month - 1])


def _is_date8(d):
    """d 是 8 个数字：yyyyMMdd、ddMMyyyy 或 MMddyyyy"""
    head, tail = int(d[:4]), int(d[4:])
    if _is_year(head) and _is_valid_date(head, int(d[4:6]), int(d[6:])):
        return True
    return _is_year(tail) and (_is_valid_date(tail, int(d[2:4]), int(d[:2]))
                               or _is_valid_date(tail, int(d[:2]), int(d[2:4])))


def _contains_date_or_year(s):
    """8 位连续数字为日期；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份"""
    i, n = 0, len(s)
    while i < n:
        if _char_type(s[i]) != _NUM:
            i += 1
            continue
        start = i
        while i < n and _char_type(s[i]) == _NUM:
            i += 1
        run = "".join(chr(c) for c in s[start:i])
        if any(_is_date8(run[k:k + 8]) for k in range(len(run) - 7)):
            return True
        if len(run) == 6 and _is_valid_date(2000 + int(run[:2]), int(run[2:4]), int(run[4:])):
            return True
        if len(run) == 4 and _is_year(int(run)):
            return True
    return False


def score(password):
    """计算密码强度分值，0 到 14；按 UTF-16 码元处理。

    :raises TypeError: password 不是 str（含 None）
    :raises ValueError: password 是空串
    """
    if not isinstance(password, str):
        raise TypeError("password is not a str")
    if not password:
        raise ValueError("password is empty")

    s = _utf16_units(password)
    n = len(s)
    length = _effective_length(s)

    # 各类统计「不同字符」的个数
    sets = [set(), set(), set(), set(), set()]
    for c in s:
        sets[_char_type(c)].add(c)
    num, small, capital, intl, other = (len(x) for x in sets)
    classes = sum(1 for x in sets if x)

    level = 0
    # 加分
    gains = (
        num > 0,
        small > 0,
        intl > 0,
        length > 4 and capital > 0,
        length > 6 and other > 0,
        length > 4 and classes >= 2,
        length > 6 and classes >= 3,
        length > 8 and classes >= 4,
        length > 6 and (num >= 3 and small >= 3 or num >= 3 and capital >= 3 or num >= 3 and other >= 2
                        or small >= 3 and capital >= 3 or small >= 3 and other >= 2 or capital >= 3 and other >= 2),
        length > 8 and (num >= 2 and small >= 2 and capital >= 2 or num >= 2 and small >= 2 and other >= 2
                        or num >= 2 and capital >= 2 and other >= 2 or small >= 2 and capital >= 2 and other >= 2),
        length > 10 and num >= 2 and small >= 2 and capital >= 2 and other >= 2,
        other >= 3,
        other >= 6,
    )
    level += sum(gains)
    level += _length_points(length)

    # 减分；只把 A-Z 转小写，码元按 chr 拼成串（单独的代理码元也能放进 str）
    lower = "".join(chr(c + 32) if 65 <= c <= 90 else chr(c) for c in s)
    covered = bytearray(n)
    for keys in _SEQUENCE_KEYS:
        hit = False
        for i in range(n - _SEQUENCE_MIN + 1):
            if lower[i:i + _SEQUENCE_MIN] in keys:
                hit = True
                covered[i:i + _SEQUENCE_MIN] = b"\x01" * _SEQUENCE_MIN
        if hit:
            level -= 1
    if 2 * sum(covered) >= n:
        level -= 1
    if length < 6 and classes == 1 and (num or small or capital):
        level -= 1
    if n % 2 == 0 and _is_same_char(s, 0, n // 2) and _is_same_char(s, n // 2, n):
        level -= 1
    if _contains_date_or_year(s):
        level -= 1
    if length >= 8 and len(set(s)) <= max(4, length // 3):
        level -= 1
    leet = lower.translate(_LEET)
    if any(w in lower or lower in w or w in leet for w in _DICTIONARY):
        level -= 2
    if length <= 3 or _is_same_char(s, 0, n):
        level = 0
    return min(max(level, 0), 14)


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
