// PwScore：密码强度评分的 C++17 header-only 包装，算法在 C 核心 c/pwscore.c 中（需一同编译）。
// 输入按 UTF-8 解码，长度按 UTF-16 码元计，规则见仓库 spec/SPEC.md。
//
// Licensed under the Apache License, Version 2.0
#ifndef PWSCORE_HPP
#define PWSCORE_HPP

#include <new>
#include <stdexcept>
#include <string_view>

#include "pwscore.h"

namespace pwscore {

// 强度等级，按分值从低到高
enum class Level { Easy = PWSCORE_EASY, Medium, Strong, VeryStrong, ExtremelyStrong };

namespace detail {
inline int check(int result) {
    if (result == PWSCORE_ERR_EMPTY) throw std::invalid_argument("password is empty");
    if (result == PWSCORE_ERR_NOMEM) throw std::bad_alloc();
    return result;
}
}  // namespace detail

// 计算密码强度分值，0 到 14；空串抛 std::invalid_argument
inline int score(std::string_view password) {
    return detail::check(pwscore_score_n(password.data() == nullptr ? "" : password.data(), password.size()));
}

// 计算密码强度等级；空串抛 std::invalid_argument
inline Level level(std::string_view password) {
    return static_cast<Level>(pwscore_level_of(score(password)));
}

// 与其他语言一致的等级名，如 "VERY_STRONG"
inline std::string_view name(Level level) { return pwscore_level_name(static_cast<int>(level)); }

}  // namespace pwscore

#endif  // PWSCORE_HPP
