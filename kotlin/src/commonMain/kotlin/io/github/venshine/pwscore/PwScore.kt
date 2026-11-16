/*
 * Licensed under the Apache License, Version 2.0
 */
package io.github.venshine.pwscore

/** 强度等级，按分值从低到高 */
enum class Level { EASY, MEDIUM, STRONG, VERY_STRONG, EXTREMELY_STRONG }

/**
 * 密码强度评分，规则见仓库 spec/SPEC.md。Kotlin 的 String 在各平台都是 UTF-16，与规则口径一致。
 */
object PwScore {

    private val DICTIONARY = listOf(
        "password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
        "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd",
    )

    private val SEQUENCES = listOf(
        listOf("abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba"),
        listOf("01234567890", "09876543210"),
        listOf("qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz"),
    )

    private const val SEQUENCE_MIN = 4

    /** 每组序列所有长度为 4 的片段 */
    private val SEQUENCE_KEYS: List<Set<String>> = SEQUENCES.map { group ->
        group.flatMap { seq -> seq.windowed(SEQUENCE_MIN) }.toSet()
    }

    private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private const val NUM = 0
    private const val SMALL = 1
    private const val CAPITAL = 2
    private const val OTHER = 3

    /**
     * 计算密码强度分值，0 到 14
     *
     * @throws IllegalArgumentException 密码为空串
     */
    fun score(password: String): Int {
        require(password.isNotEmpty()) { "password is empty" }
        // 整串由更短片段重复组成时，按重复单元评分
        val s = password.substring(0, repeatUnitLength(password))
        val len = s.length

        val count = IntArray(4)
        for (c in s) count[charType(c)]++
        val num = count[NUM]
        val small = count[SMALL]
        val capital = count[CAPITAL]
        val other = count[OTHER]
        val classes = count.count { it > 0 }

        var level = 0
        // 加分
        if (num > 0) level++
        if (small > 0) level++
        if (len > 4 && capital > 0) level++
        if (len > 6 && other > 0) level++
        if (len > 4 && classes >= 2) level++
        if (len > 6 && classes >= 3) level++
        if (len > 8 && classes == 4) level++
        if (len > 6 && (num >= 3 && small >= 3 || num >= 3 && capital >= 3 || num >= 3 && other >= 2 ||
                small >= 3 && capital >= 3 || small >= 3 && other >= 2 || capital >= 3 && other >= 2)
        ) level++
        if (len > 8 && (num >= 2 && small >= 2 && capital >= 2 || num >= 2 && small >= 2 && other >= 2 ||
                num >= 2 && capital >= 2 && other >= 2 || small >= 2 && capital >= 2 && other >= 2)
        ) level++
        if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++
        if (other >= 3) level++
        if (other >= 6) level++
        if (len > 12) level++
        if (len >= 16) level++

        // 减分；只把 A-Z 转小写，不用 lowercase()，避免 Unicode 小写改变长度或结果
        val lower = buildString(len) { for (c in s) append(if (c in 'A'..'Z') c + 32 else c) }
        for (keys in SEQUENCE_KEYS) {
            if ((0..len - SEQUENCE_MIN).any { lower.substring(it, it + SEQUENCE_MIN) in keys }) level--
        }
        if (num == len || small == len || capital == len) level--
        if (len % 2 == 0 && isSameChar(s, 0, len / 2) && isSameChar(s, len / 2, len)) level--
        if (containsDate(s)) level--
        if (DICTIONARY.any { lower.contains(it) || it.contains(lower) }) level--
        if (len <= 6) level--
        if (len <= 4) level--
        if (len <= 3 || isSameChar(s, 0, len)) level = 0
        return maxOf(level, 0)
    }

    /**
     * 计算密码强度等级
     *
     * @throws IllegalArgumentException 密码为空串
     */
    fun level(password: String): Level = when (score(password)) {
        in 0..3 -> Level.EASY
        in 4..6 -> Level.MEDIUM
        in 7..9 -> Level.STRONG
        in 10..12 -> Level.VERY_STRONG
        else -> Level.EXTREMELY_STRONG
    }

    private fun charType(c: Char): Int = when (c) {
        in '0'..'9' -> NUM
        in 'a'..'z' -> SMALL
        in 'A'..'Z' -> CAPITAL
        else -> OTHER
    }

    private fun isSameChar(s: String, from: Int, to: Int): Boolean =
        (from + 1 until to).all { s[it] == s[from] }

    private fun repeatUnitLength(s: String): Int {
        val n = s.length
        val prefix = IntArray(n)
        for (i in 1 until n) {
            var k = prefix[i - 1]
            while (k > 0 && s[i] != s[k]) k = prefix[k - 1]
            if (s[i] == s[k]) k++
            prefix[i] = k
        }
        val period = n - prefix[n - 1]
        return if (n % period == 0) period else n
    }

    private fun containsDate(s: String): Boolean {
        var run = 0
        for (i in s.indices) {
            run = if (charType(s[i]) == NUM) run + 1 else 0
            if (run >= 8) {
                val start = i - 7
                val year = digits(s, start, 4)
                val month = digits(s, start + 4, 2)
                val day = digits(s, start + 6, 2)
                if (year in 1950..2049 && month in 1..12 && day >= 1 && day <= daysInMonth(year, month)) return true
            }
        }
        return false
    }

    private fun digits(s: String, start: Int, count: Int): Int {
        var value = 0
        for (i in start until start + count) value = value * 10 + (s[i] - '0')
        return value
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return if (month == 2 && leap) 29 else DAYS_IN_MONTH[month - 1]
    }
}
