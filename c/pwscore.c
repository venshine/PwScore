/*
 * PwScore：密码强度评分，规则见仓库 spec/SPEC.md。
 *
 * Licensed under the Apache License, Version 2.0
 */
#include "pwscore.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

enum { NUM = 0, SMALL = 1, CAPITAL = 2, OTHER = 3 };

#define SEQUENCE_MIN 4

static const char *const DICTIONARY[] = {"password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
                                         "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd"};

/* 每组以 NULL 结尾：字母表、数字（含 9 到 0 的回绕）、键盘三行，正反向都列出 */
static const char *const SEQ_LETTERS[] = {"abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba", NULL};
static const char *const SEQ_DIGITS[] = {"01234567890", "09876543210", NULL};
static const char *const SEQ_KEYBOARD[] = {"qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa",
                                           "zxcvbnm",    "mnbvcxz",    NULL};
static const char *const *const SEQUENCES[] = {SEQ_LETTERS, SEQ_DIGITS, SEQ_KEYBOARD};

static const int DAYS_IN_MONTH[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

/* UTF-8 解码为 UTF-16 码元，返回码元数；非法序列的每个字节按 U+FFFD。out 至少要有 len 个元素 */
static size_t utf8_to_utf16(const unsigned char *in, size_t len, uint16_t *out) {
    size_t i = 0, n = 0;
    while (i < len) {
        unsigned char b = in[i];
        uint32_t cp;
        size_t need;
        uint32_t min;
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
        {
            size_t k;
            int ok = 1;
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
    return OTHER;
}

/* 用 KMP 前缀函数求最小重复单元长度，prefix 至少要有 n 个元素 */
static size_t repeat_unit_length(const uint16_t *s, size_t n, size_t *prefix) {
    size_t i, period;
    prefix[0] = 0;
    for (i = 1; i < n; i++) {
        size_t k = prefix[i - 1];
        while (k > 0 && s[i] != s[k]) k = prefix[k - 1];
        if (s[i] == s[k]) k++;
        prefix[i] = k;
    }
    period = n - prefix[n - 1];
    return n % period == 0 ? period : n;
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

/* ASCII 串 hay 是否包含码元串 needle */
static int ascii_contains_units(const char *hay, size_t hay_len, const uint16_t *needle, size_t needle_len) {
    size_t i, j;
    if (needle_len > hay_len) return 0;
    for (i = 0; i + needle_len <= hay_len; i++) {
        for (j = 0; j < needle_len && (unsigned char)hay[i + j] == needle[j]; j++) {
        }
        if (j == needle_len) return 1;
    }
    return 0;
}

static int contains_sequence(const uint16_t *lower, size_t n, const char *const *group) {
    size_t i;
    for (i = 0; i + SEQUENCE_MIN <= n; i++) {
        const char *const *seq;
        for (seq = group; *seq != NULL; seq++) {
            if (ascii_contains_units(*seq, strlen(*seq), lower + i, SEQUENCE_MIN)) {
                return 1;
            }
        }
    }
    return 0;
}

static int digits(const uint16_t *s, size_t start, size_t count) {
    int v = 0;
    size_t i;
    for (i = start; i < start + count; i++) v = v * 10 + (s[i] - '0');
    return v;
}

static int days_in_month(int year, int month) {
    int leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    return month == 2 && leap ? 29 : DAYS_IN_MONTH[month - 1];
}

static int contains_date(const uint16_t *s, size_t n) {
    size_t i, run = 0;
    for (i = 0; i < n; i++) {
        run = char_type(s[i]) == NUM ? run + 1 : 0;
        if (run >= 8) {
            size_t start = i - 7;
            int year = digits(s, start, 4), month = digits(s, start + 4, 2), day = digits(s, start + 6, 2);
            if (year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1 &&
                day <= days_in_month(year, month)) {
                return 1;
            }
        }
    }
    return 0;
}

static int score_units(uint16_t *s, size_t n, size_t *prefix) {
    size_t count[4] = {0, 0, 0, 0};
    size_t num, small, capital, other, i, g, w;
    int classes, level = 0;

    /* 整串由更短片段重复组成时，按重复单元评分 */
    n = repeat_unit_length(s, n, prefix);

    for (i = 0; i < n; i++) count[char_type(s[i])]++;
    num = count[NUM];
    small = count[SMALL];
    capital = count[CAPITAL];
    other = count[OTHER];
    classes = (num > 0) + (small > 0) + (capital > 0) + (other > 0);

    /* 加分 */
    level += num > 0;
    level += small > 0;
    level += n > 4 && capital > 0;
    level += n > 6 && other > 0;
    level += n > 4 && classes >= 2;
    level += n > 6 && classes >= 3;
    level += n > 8 && classes == 4;
    level += n > 6 && ((num >= 3 && small >= 3) || (num >= 3 && capital >= 3) || (num >= 3 && other >= 2) ||
                       (small >= 3 && capital >= 3) || (small >= 3 && other >= 2) || (capital >= 3 && other >= 2));
    level += n > 8 && ((num >= 2 && small >= 2 && capital >= 2) || (num >= 2 && small >= 2 && other >= 2) ||
                       (num >= 2 && capital >= 2 && other >= 2) || (small >= 2 && capital >= 2 && other >= 2));
    level += n > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2;
    level += other >= 3;
    level += other >= 6;
    level += n > 12;
    level += n >= 16;

    /* 减分：先算依赖原文的条件，再把 s 原地转为小写（只转 A-Z） */
    {
        int halves_same = n % 2 == 0 && is_same_char(s, 0, n / 2) && is_same_char(s, n / 2, n);
        int all_same = is_same_char(s, 0, n);
        int has_date = contains_date(s, n);
        uint16_t *lower = s;

        for (i = 0; i < n; i++) {
            if (lower[i] >= 'A' && lower[i] <= 'Z') lower[i] = (uint16_t)(lower[i] + 32);
        }
        for (g = 0; g < sizeof(SEQUENCES) / sizeof(SEQUENCES[0]); g++) {
            if (contains_sequence(lower, n, SEQUENCES[g])) level--;
        }
        if (num == n || small == n || capital == n) level--;
        if (halves_same) level--;
        if (has_date) level--;
        for (w = 0; w < sizeof(DICTIONARY) / sizeof(DICTIONARY[0]); w++) {
            size_t wl = strlen(DICTIONARY[w]);
            if (units_contain_ascii(lower, n, DICTIONARY[w], wl) || ascii_contains_units(DICTIONARY[w], wl, lower, n)) {
                level--;
                break;
            }
        }
        if (n <= 6) level--;
        if (n <= 4) level--;
        if (n <= 3 || all_same) level = 0;
    }
    return level < 0 ? 0 : level;
}

int pwscore_score_n(const char *password, size_t len) {
    uint16_t *units;
    size_t *prefix;
    size_t n;
    int result;
    if (password == NULL) return PWSCORE_ERR_NULL;
    if (len == 0) return PWSCORE_ERR_EMPTY;
    /* UTF-16 码元数不超过 UTF-8 字节数 */
    units = (uint16_t *)malloc(len * sizeof(uint16_t));
    prefix = (size_t *)malloc(len * sizeof(size_t));
    if (units == NULL || prefix == NULL) {
        free(units);
        free(prefix);
        return PWSCORE_ERR_NOMEM;
    }
    n = utf8_to_utf16((const unsigned char *)password, len, units);
    result = score_units(units, n, prefix);
    free(units);
    free(prefix);
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
