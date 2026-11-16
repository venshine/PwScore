/*
 * PwScore：密码强度评分，规则见仓库 spec/SPEC.md。C99，无第三方依赖。
 * 输入按 UTF-8 解码（非法字节按 U+FFFD），长度按 UTF-16 码元计。
 *
 * Licensed under the Apache License, Version 2.0
 */
#ifndef PWSCORE_H
#define PWSCORE_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* 强度等级，按分值从低到高 */
typedef enum {
    PWSCORE_EASY = 0,
    PWSCORE_MEDIUM = 1,
    PWSCORE_STRONG = 2,
    PWSCORE_VERY_STRONG = 3,
    PWSCORE_EXTREMELY_STRONG = 4
} pwscore_level_t;

/* 错误码，均为负数 */
#define PWSCORE_ERR_NULL (-1)   /* password 为 NULL */
#define PWSCORE_ERR_EMPTY (-2)  /* password 为空串 */
#define PWSCORE_ERR_NOMEM (-3)  /* 内存分配失败 */

/* 计算 NUL 结尾的 UTF-8 密码的分值：成功返回 0 到 14，失败返回负的错误码 */
int pwscore_score(const char *password);

/* 同上，按长度读取 len 个字节，可包含 NUL */
int pwscore_score_n(const char *password, size_t len);

/* 计算等级：成功返回 pwscore_level_t，失败返回负的错误码 */
int pwscore_level(const char *password);
int pwscore_level_n(const char *password, size_t len);

/* 分值对应的等级；等级名与其他语言一致，如 "VERY_STRONG"，未知取值返回 NULL */
pwscore_level_t pwscore_level_of(int score);
const char *pwscore_level_name(int level);

#ifdef __cplusplus
}
#endif

#endif /* PWSCORE_H */
