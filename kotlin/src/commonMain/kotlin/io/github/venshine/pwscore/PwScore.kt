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

    /** 序列分组，每组命中扣 1 分；每个序列的反向自动加入 */
    private val SEQUENCES = listOf(
        listOf("abcdefghijklmnopqrstuvwxyz", "acegikmoqsuwy", "bdfhjlnprtvxz"),
        listOf("01234567890", "02468", "13579"),
        listOf(
            "qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/",
            "qazwsxedcrfvtgbyhnujmikolp", "1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0",
            "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+",
        ),
    )

    private const val SEQUENCE_MIN = 4
    private const val MAX_BLOCK = 16
    private const val LEET_FROM = "@401!3$57"
    private const val LEET_TO = "aaoiiesst"

    /** 每组序列（含反向）所有长度为 4 的片段 */
    private val SEQUENCE_KEYS: List<Set<String>> = SEQUENCES.map { group ->
        group.flatMap { listOf(it, it.reversed()) }.flatMap { it.windowed(SEQUENCE_MIN) }.toSet()
    }

    private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private const val NUM = 0
    private const val SMALL = 1
    private const val CAPITAL = 2
    private const val INTL = 3
    private const val OTHER = 4

    /**
     * 计算密码强度分值，0 到 14
     *
     * @throws IllegalArgumentException 密码为空串
     */
    fun score(password: String): Int {
        require(password.isNotEmpty()) { "password is empty" }
        // 全角 ASCII 还原为半角
        val s = CharArray(password.length) { val c = password[it]; if (c in '！'..'～') c - 0xFEE0 else c }
        val n = s.size
        val len = effectiveLength(s)

        // 各类统计「不同字符」的个数
        val sets = List(5) { HashSet<Char>() }
        for (c in s) sets[charType(c)].add(c)
        val num = sets[NUM].size
        val small = sets[SMALL].size
        val capital = sets[CAPITAL].size
        val intl = sets[INTL].size
        val other = sets[OTHER].size
        val classes = sets.count { it.isNotEmpty() }
        val distinct = s.toSet().size

        var level = 0
        // 加分
        if (num > 0) level++
        if (small > 0) level++
        if (intl > 0) level++
        if (len > 4 && capital > 0) level++
        if (len > 6 && other > 0) level++
        if (len > 4 && classes >= 2) level++
        if (len > 6 && classes >= 3) level++
        if (len > 8 && classes >= 4) level++
        if (len > 6 && (num >= 3 && small >= 3 || num >= 3 && capital >= 3 || num >= 3 && other >= 2 ||
                small >= 3 && capital >= 3 || small >= 3 && other >= 2 || capital >= 3 && other >= 2)
        ) level++
        if (len > 8 && (num >= 2 && small >= 2 && capital >= 2 || num >= 2 && small >= 2 && other >= 2 ||
                num >= 2 && capital >= 2 && other >= 2 || small >= 2 && capital >= 2 && other >= 2)
        ) level++
        if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++
        if (other >= 3) level++
        if (other >= 6) level++
        level += lengthPoints(len)

        // 减分；只把 A-Z 转小写，不用 lowercase()，避免 Unicode 小写改变长度或结果
        val lower = buildString(n) { for (c in s) append(if (c in 'A'..'Z') c + 32 else c) }
        val covered = BooleanArray(n)
        for (keys in SEQUENCE_KEYS) {
            var hit = false
            for (i in 0..n - SEQUENCE_MIN) {
                if (lower.substring(i, i + SEQUENCE_MIN) in keys) {
                    hit = true
                    covered.fill(true, i, i + SEQUENCE_MIN)
                }
            }
            if (hit) level--
        }
        if (2 * covered.count { it } >= n) level--
        if (len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)) level--
        if (n % 2 == 0 && isSameChar(s, 0, n / 2) && isSameChar(s, n / 2, n)) level--
        if (containsDateOrYear(s)) level--
        if (len >= 8 && distinct <= maxOf(4, len / 3)) level--
        val leet = buildString(n) { for (c in lower) LEET_FROM.indexOf(c).let { append(if (it >= 0) LEET_TO[it] else c) } }
        if (DICTIONARY.any { lower.contains(it) || it.contains(lower) || leet.contains(it) }) level -= 2
        if (len <= 3 || isSameChar(s, 0, n)) level = 0
        return level.coerceIn(0, 14)
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

    private fun charType(c: Char): Int = when {
        c in '0'..'9' -> NUM
        c in 'a'..'z' -> SMALL
        c in 'A'..'Z' -> CAPITAL
        c.code >= 128 -> INTL
        else -> OTHER
    }

    private fun isPair(s: CharArray, i: Int, end: Int): Boolean =
        i + 1 < end && s[i].isHighSurrogate() && s[i + 1].isLowSurrogate()

    private fun regionEquals(s: CharArray, a: Int, b: Int, count: Int): Boolean =
        (0 until count).all { s[a + it] == s[b + it] }

    private fun characterCount(s: CharArray, from: Int, to: Int): Int {
        var i = from
        var count = 0
        while (i < to) {
            i += if (isPair(s, i, to)) 2 else 1
            count++
        }
        return count
    }

    /** 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1 */
    private fun effectiveLength(s: CharArray): Int {
        val n = s.size
        var len = 0
        var i = 0
        while (i < n) {
            var block = 0
            var m = 1
            while (m <= MAX_BLOCK && i + 2 * m <= n) {
                if (s[i] == s[i + m] && regionEquals(s, i, i + m, m)) {
                    block = m
                    break
                }
                m++
            }
            if (block > 0) {
                var r = 2
                while (i + (r + 1) * block <= n && regionEquals(s, i, i + r * block, block)) r++
                len += if (block == 1) 2 else characterCount(s, i, i + block)
                i += r * block
            } else {
                i += if (isPair(s, i, n)) 2 else 1
                len++
            }
        }
        return len
    }

    private fun lengthPoints(len: Int): Int = when {
        len <= 4 -> -2
        len == 5 -> -1
        len < 8 -> 0
        else -> minOf((len - 8) / 4 + 1, 5)
    }

    private fun isSameChar(s: CharArray, from: Int, to: Int): Boolean =
        (from + 1 until to).all { s[it] == s[from] }

    private fun digits(s: CharArray, start: Int, count: Int): Int {
        var value = 0
        for (i in start until start + count) value = value * 10 + (s[i] - '0')
        return value
    }

    private fun isYear(year: Int) = year in 1950..2049

    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (month !in 1..12 || day < 1) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return day <= if (month == 2 && leap) 29 else DAYS_IN_MONTH[month - 1]
    }

    private fun isDate8(s: CharArray, k: Int): Boolean {
        val head = digits(s, k, 4)
        if (isYear(head) && isValidDate(head, digits(s, k + 4, 2), digits(s, k + 6, 2))) return true
        val tail = digits(s, k + 4, 4)
        return isYear(tail) && (isValidDate(tail, digits(s, k + 2, 2), digits(s, k, 2)) ||
            isValidDate(tail, digits(s, k, 2), digits(s, k + 2, 2)))
    }

    /** 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份 */
    private fun containsDateOrYear(s: CharArray): Boolean {
        var i = 0
        while (i < s.size) {
            if (charType(s[i]) != NUM) {
                i++
                continue
            }
            val start = i
            while (i < s.size && charType(s[i]) == NUM) i++
            val run = i - start
            for (k in start..i - 8) if (isDate8(s, k)) return true
            if (run == 6 && isValidDate(2000 + digits(s, start, 2), digits(s, start + 2, 2), digits(s, start + 4, 2))) {
                return true
            }
            if (run == 4 && isYear(digits(s, start, 4))) return true
        }
        return false
    }
}
