/*
 * PwScore：密码强度评分，规则见仓库 spec/SPEC.md。
 *
 * Licensed under the Apache License, Version 2.0
 */
#include "pwscore.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

enum { NUM = 0, SMALL = 1, CAPITAL = 2, INTL = 3, OTHER = 4 };

#define SEQUENCE_MIN 4
#define MAX_BLOCK 16

static const char *const DICTIONARY[] = {
    "password", "passwd", "admin", "iloveyou", "sunshine", "adobe123", "abc123", "123123", "1314520", "a1b2c3",
    "123qwe", "aaa111", "qweasd",
    "welcome", "letmein", "login", "master", "monkey", "dragon", "shadow", "princess", "football", "baseball",
    "soccer", "superman", "batman", "starwars", "pokemon", "freedom", "whatever", "trustno1", "secret", "hello",
    "summer", "winter", "spring", "autumn", "flower", "angel", "lovely", "loveyou", "killer", "hunter", "cookie",
    "banana", "orange", "purple", "silver", "golden", "diamond", "chocolate", "computer", "internet", "google",
    "apple", "changeme", "default", "guest",
    "woaini", "iloveu", "zhang", "wang", "yang", "huang", "zhao", "xiaoming", "laopo", "laogong", "baobao",
    "nihao", "wodemima", "mima", "qq123",
    "5201314", "520520", "147258", "159753", "147852", "789456", "741852", "963852", "258369", "123321", "112233"};

/* 序列分组，每组以 NULL 结尾，每组命中扣 1 分；匹配时同时检查每个序列的反向 */
static const char *const SEQ_LETTERS[] = {"abcdefghijklmnopqrstuvwxyz", "acegikmoqsuwy", "bdfhjlnprtvxz", NULL};
static const char *const SEQ_DIGITS[] = {"01234567890", "02468", "13579", NULL};
static const char *const SEQ_KEYBOARD[] = {"qwertyuiop",
                                           "asdfghjkl",
                                           "zxcvbnm",
                                           "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/",
                                           "qazwsxedcrfvtgbyhnujmikolp",
                                           "1q2w3e4r5t6y7u8i9o0p",
                                           "q1w2e3r4t5y6u7i8o9p0",
                                           "zaq1xsw2cde3vfr4bgt5nhy6mju7",
                                           "!@#$%^&*()_+",
                                           NULL};
static const char *const *const SEQUENCES[] = {SEQ_LETTERS, SEQ_DIGITS, SEQ_KEYBOARD};

static const char LEET_FROM[] = "@401!3$57";
static const char LEET_TO[] = "aaoiiesst";

static const int DAYS_IN_MONTH[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

/* UTF-8 解码为 UTF-16 码元，返回码元数；非法序列的每个字节按 U+FFFD。out 至少要有 len 个元素 */
static size_t utf8_to_utf16(const unsigned char *in, size_t len, uint16_t *out) {
    size_t i = 0, n = 0;
    while (i < len) {
        unsigned char b = in[i];
        uint32_t cp, min;
        size_t need, k;
        int ok = 1;
        if (b < 0x80) {
            out[n++] = b;
            i++;
            continue;
        } else if (b >= 0xC2 && b <= 0xDF) {
            cp = b & 0x1F;
            need = 1;
            min = 0x80;
        } else if (b >= 0xE0 && b <= 0xEF) {
            cp = b & 0x0F;
            need = 2;
            min = 0x800;
        } else if (b >= 0xF0 && b <= 0xF4) {
            cp = b & 0x07;
            need = 3;
            min = 0x10000;
        } else {
            out[n++] = 0xFFFD;
            i++;
            continue;
        }
        if (i + need >= len) {
            /* 序列被截断 */
            out[n++] = 0xFFFD;
            i++;
            continue;
        }
        for (k = 1; k <= need; k++) {
            if ((in[i + k] & 0xC0) != 0x80) {
                ok = 0;
                break;
            }
            cp = (cp << 6) | (in[i + k] & 0x3F);
        }
        if (!ok || cp < min || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            out[n++] = 0xFFFD;
            i++;
            continue;
        }
        if (cp > 0xFFFF) {
            cp -= 0x10000;
            out[n++] = (uint16_t)(0xD800 + (cp >> 10));
            out[n++] = (uint16_t)(0xDC00 + (cp & 0x3FF));
        } else {
            out[n++] = (uint16_t)cp;
        }
        i += need + 1;
    }
    return n;
}

static int char_type(uint16_t c) {
    if (c >= '0' && c <= '9') return NUM;
    if (c >= 'a' && c <= 'z') return SMALL;
    if (c >= 'A' && c <= 'Z') return CAPITAL;
    if (c >= 128) return INTL;
    return OTHER;
}

static int is_pair(const uint16_t *s, size_t i, size_t end) {
    return i + 1 < end && s[i] >= 0xD800 && s[i] <= 0xDBFF && s[i + 1] >= 0xDC00 && s[i + 1] <= 0xDFFF;
}

static int region_equals(const uint16_t *s, size_t a, size_t b, size_t count) {
    size_t k;
    for (k = 0; k < count; k++) {
        if (s[a + k] != s[b + k]) return 0;
    }
    return 1;
}

static size_t character_count(const uint16_t *s, size_t from, size_t to) {
    size_t i = from, count = 0;
    while (i < to) {
        i += is_pair(s, i, to) ? 2 : 1;
        count++;
    }
    return count;
}

/* 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1 */
static size_t effective_length(const uint16_t *s, size_t n) {
    size_t i = 0, len = 0;
    while (i < n) {
        size_t m, block = 0;
        for (m = 1; m <= MAX_BLOCK && i + 2 * m <= n; m++) {
            if (s[i] == s[i + m] && region_equals(s, i, i + m, m)) {
                block = m;
                break;
            }
        }
        if (block > 0) {
            size_t r = 2;
            while (i + (r + 1) * block <= n && region_equals(s, i, i + r * block, block)) r++;
            len += block == 1 ? 2 : character_count(s, i, i + block);
            i += r * block;
        } else {
            i += is_pair(s, i, n) ? 2 : 1;
            len++;
        }
    }
    return len;
}

static int length_points(size_t len) {
    size_t p;
    if (len <= 4) return -2;
    if (len == 5) return -1;
    if (len < 8) return 0;
    p = (len - 8) / 4 + 1;
    return p < 5 ? (int)p : 5;
}

static int is_same_char(const uint16_t *s, size_t from, size_t to) {
    size_t i;
    for (i = from + 1; i < to; i++) {
        if (s[i] != s[from]) return 0;
    }
    return 1;
}

/* 码元串 hay 是否包含 ASCII 串 needle */
static int units_contain_ascii(const uint16_t *hay, size_t hay_len, const char *needle, size_t needle_len) {
    size_t i, j;
    if (needle_len > hay_len) return 0;
    for (i = 0; i + needle_len <= hay_len; i++) {
        for (j = 0; j < needle_len && hay[i + j] == (unsigned char)needle[j]; j++) {
        }
        if (j == needle_len) return 1;
    }
    return 0;
}

/* ASCII 串 hay（reverse 为真时按反向读）是否包含码元串 needle */
static int ascii_contains_units(const char *hay, size_t hay_len, int reverse, const uint16_t *needle,
                                size_t needle_len) {
    size_t i, j;
    if (needle_len > hay_len) return 0;
    for (i = 0; i + needle_len <= hay_len; i++) {
        for (j = 0; j < needle_len; j++) {
            unsigned char c = (unsigned char)(reverse ? hay[hay_len - 1 - (i + j)] : hay[i + j]);
            if (c != needle[j]) break;
        }
        if (j == needle_len) return 1;
    }
    return 0;
}

/* 这组序列是否命中；命中的位置记入 covered */
static int mark_sequences(const uint16_t *lower, size_t n, const char *const *group, unsigned char *covered) {
    size_t i;
    int hit = 0;
    for (i = 0; i + SEQUENCE_MIN <= n; i++) {
        const char *const *seq;
        for (seq = group; *seq != NULL; seq++) {
            size_t sl = strlen(*seq);
            if (ascii_contains_units(*seq, sl, 0, lower + i, SEQUENCE_MIN) ||
                ascii_contains_units(*seq, sl, 1, lower + i, SEQUENCE_MIN)) {
                hit = 1;
                memset(covered + i, 1, SEQUENCE_MIN);
                break;
            }
        }
    }
    return hit;
}

static int digits(const uint16_t *s, size_t start, size_t count) {
    int v = 0;
    size_t i;
    for (i = start; i < start + count; i++) v = v * 10 + (s[i] - '0');
    return v;
}

static int is_year(int year) { return year >= 1950 && year <= 2049; }

static int is_valid_date(int year, int month, int day) {
    int leap;
    if (month < 1 || month > 12 || day < 1) return 0;
    leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    return day <= (month == 2 && leap ? 29 : DAYS_IN_MONTH[month - 1]);
}

static int is_date8(const uint16_t *s, size_t k) {
    int head = digits(s, k, 4), tail = digits(s, k + 4, 4);
    if (is_year(head) && is_valid_date(head, digits(s, k + 4, 2), digits(s, k + 6, 2))) return 1;
    return is_year(tail) &&
           (is_valid_date(tail, digits(s, k + 2, 2), digits(s, k, 2)) ||
            is_valid_date(tail, digits(s, k, 2), digits(s, k + 2, 2)));
}

/* 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份 */
static int contains_date_or_year(const uint16_t *s, size_t n) {
    size_t i = 0;
    while (i < n) {
        size_t start, run, k;
        if (char_type(s[i]) != NUM) {
            i++;
            continue;
        }
        start = i;
        while (i < n && char_type(s[i]) == NUM) i++;
        run = i - start;
        for (k = start; k + 8 <= i; k++) {
            if (is_date8(s, k)) return 1;
        }
        if (run == 6 &&
            is_valid_date(2000 + digits(s, start, 2), digits(s, start + 2, 2), digits(s, start + 4, 2))) {
            return 1;
        }
        if (run == 4 && is_year(digits(s, start, 4))) return 1;
    }
    return 0;
}

/* s 为已折叠全角的码元，lower、covered 为同长度的工作区，seen 为 65536 位的位图（已清零） */
static int score_units(const uint16_t *s, size_t n, uint16_t *lower, uint16_t *leet, unsigned char *covered,
                       unsigned char *seen) {
    size_t count[5] = {0, 0, 0, 0, 0};
    size_t num, small, capital, intl, other, distinct = 0, covered_count = 0, len, i, g, w;
    int classes, level = 0;

    len = effective_length(s, n);

    /* 各类统计「不同字符」的个数 */
    for (i = 0; i < n; i++) {
        if (!(seen[s[i] >> 3] & (1u << (s[i] & 7)))) {
            seen[s[i] >> 3] |= (unsigned char)(1u << (s[i] & 7));
            count[char_type(s[i])]++;
            distinct++;
        }
    }
    num = count[NUM];
    small = count[SMALL];
    capital = count[CAPITAL];
    intl = count[INTL];
    other = count[OTHER];
    classes = (num > 0) + (small > 0) + (capital > 0) + (intl > 0) + (other > 0);

    /* 加分 */
    level += num > 0;
    level += small > 0;
    level += intl > 0;
    level += len > 4 && capital > 0;
    level += len > 6 && other > 0;
    level += len > 4 && classes >= 2;
    level += len > 6 && classes >= 3;
    level += len > 8 && classes >= 4;
    level += len > 6 && ((num >= 3 && small >= 3) || (num >= 3 && capital >= 3) || (num >= 3 && other >= 2) ||
                         (small >= 3 && capital >= 3) || (small >= 3 && other >= 2) || (capital >= 3 && other >= 2));
    level += len > 8 && ((num >= 2 && small >= 2 && capital >= 2) || (num >= 2 && small >= 2 && other >= 2) ||
                         (num >= 2 && capital >= 2 && other >= 2) || (small >= 2 && capital >= 2 && other >= 2));
    level += len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2;
    level += other >= 3;
    level += other >= 6;
    level += length_points(len);

    /* 减分；只把 A-Z 转小写 */
    for (i = 0; i < n; i++) {
        const char *p;
        lower[i] = s[i] >= 'A' && s[i] <= 'Z' ? (uint16_t)(s[i] + 32) : s[i];
        p = lower[i] > 0 && lower[i] < 128 ? strchr(LEET_FROM, (int)lower[i]) : NULL;
        leet[i] = p != NULL ? (uint16_t)(unsigned char)LEET_TO[p - LEET_FROM] : lower[i];
    }
    memset(covered, 0, n);
    for (g = 0; g < sizeof(SEQUENCES) / sizeof(SEQUENCES[0]); g++) {
        if (mark_sequences(lower, n, SEQUENCES[g], covered)) level--;
    }
    for (i = 0; i < n; i++) covered_count += covered[i];
    if (2 * covered_count >= n) level--;
    if (len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)) level--;
    if (n % 2 == 0 && is_same_char(s, 0, n / 2) && is_same_char(s, n / 2, n)) level--;
    if (contains_date_or_year(s, n)) level--;
    if (len >= 8 && distinct <= (len / 3 > 4 ? len / 3 : 4)) level--;
    for (w = 0; w < sizeof(DICTIONARY) / sizeof(DICTIONARY[0]); w++) {
        size_t wl = strlen(DICTIONARY[w]);
        if (units_contain_ascii(lower, n, DICTIONARY[w], wl) ||
            ascii_contains_units(DICTIONARY[w], wl, 0, lower, n) || units_contain_ascii(leet, n, DICTIONARY[w], wl)) {
            level -= 2;
            break;
        }
    }
    if (len <= 3 || is_same_char(s, 0, n)) level = 0;
    if (level < 0) level = 0;
    return level > 14 ? 14 : level;
}

int pwscore_score_n(const char *password, size_t len) {
    uint16_t *units, *lower, *leet;
    unsigned char *covered, *seen;
    size_t n, i;
    int result;
    if (password == NULL) return PWSCORE_ERR_NULL;
    if (len == 0) return PWSCORE_ERR_EMPTY;
    /* UTF-16 码元数不超过 UTF-8 字节数 */
    units = (uint16_t *)malloc(len * sizeof(uint16_t));
    lower = (uint16_t *)malloc(len * sizeof(uint16_t));
    leet = (uint16_t *)malloc(len * sizeof(uint16_t));
    covered = (unsigned char *)malloc(len);
    seen = (unsigned char *)calloc(65536 / 8, 1);
    if (units == NULL || lower == NULL || leet == NULL || covered == NULL || seen == NULL) {
        result = PWSCORE_ERR_NOMEM;
    } else {
        n = utf8_to_utf16((const unsigned char *)password, len, units);
        /* 全角 ASCII 还原为半角 */
        for (i = 0; i < n; i++) {
            if (units[i] >= 0xFF01 && units[i] <= 0xFF5E) units[i] = (uint16_t)(units[i] - 0xFEE0);
        }
        result = score_units(units, n, lower, leet, covered, seen);
    }
    free(units);
    free(lower);
    free(leet);
    free(covered);
    free(seen);
    return result;
}

int pwscore_score(const char *password) {
    if (password == NULL) return PWSCORE_ERR_NULL;
    return pwscore_score_n(password, strlen(password));
}

pwscore_level_t pwscore_level_of(int score) {
    if (score <= 3) return PWSCORE_EASY;
    if (score <= 6) return PWSCORE_MEDIUM;
    if (score <= 9) return PWSCORE_STRONG;
    if (score <= 12) return PWSCORE_VERY_STRONG;
    return PWSCORE_EXTREMELY_STRONG;
}

int pwscore_level_n(const char *password, size_t len) {
    int score = pwscore_score_n(password, len);
    return score < 0 ? score : (int)pwscore_level_of(score);
}

int pwscore_level(const char *password) {
    if (password == NULL) return PWSCORE_ERR_NULL;
    return pwscore_level_n(password, strlen(password));
}

const char *pwscore_level_name(int level) {
    static const char *const NAMES[] = {"EASY", "MEDIUM", "STRONG", "VERY_STRONG", "EXTREMELY_STRONG"};
    return level >= 0 && level <= 4 ? NAMES[level] : NULL;
}
