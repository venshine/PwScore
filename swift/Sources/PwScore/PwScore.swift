// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。长度按 UTF-16 码元计。
//
// Licensed under the Apache License, Version 2.0

/// 强度等级，按分值从低到高；rawValue 与其他语言的等级名一致
public enum Level: String, CaseIterable, Sendable {
    case easy = "EASY"
    case medium = "MEDIUM"
    case strong = "STRONG"
    case veryStrong = "VERY_STRONG"
    case extremelyStrong = "EXTREMELY_STRONG"
}

/// 评分错误
public enum PwScoreError: Error, Equatable {
    case emptyPassword
}

/// 密码强度评分
public enum PwScore {
    private static let dictionary: [[UInt16]] = [
        "password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
        "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd",
    ].map { Array($0.utf16) }

    private static let sequences: [[String]] = [
        ["abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba"],
        ["01234567890", "09876543210"],
        ["qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz"],
    ]

    private static let sequenceMin = 4

    /// 每组序列所有长度为 4 的片段，按每码元 16 位打包成 UInt64
    private static let sequenceKeys: [Set<UInt64>] = sequences.map { group in
        var keys = Set<UInt64>()
        for seq in group {
            let u = Array(seq.utf16)
            for i in 0...(u.count - sequenceMin) {
                keys.insert(pack(u, i))
            }
        }
        return keys
    }

    private static func pack(_ s: [UInt16], _ start: Int) -> UInt64 {
        var key: UInt64 = 0
        for i in start..<(start + sequenceMin) { key = key << 16 | UInt64(s[i]) }
        return key
    }

    private static let daysInMonthTable = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

    private static let num = 0, small = 1, capital = 2, other = 3

    /// 计算密码强度分值，0 到 14；空串抛 `PwScoreError.emptyPassword`
    public static func score(_ password: String) throws -> Int {
        if password.isEmpty { throw PwScoreError.emptyPassword }
        let units = Array(password.utf16)
        // 整串由更短片段重复组成时，按重复单元评分
        let s = Array(units[0..<repeatUnitLength(units)])
        let len = s.count

        var count = [0, 0, 0, 0]
        for c in s { count[charType(c)] += 1 }
        let (n, sm, cap, ot) = (count[num], count[small], count[capital], count[other])
        let classes = count.filter { $0 > 0 }.count

        // 加分
        let gains: [Bool] = [
            n > 0,
            sm > 0,
            len > 4 && cap > 0,
            len > 6 && ot > 0,
            len > 4 && classes >= 2,
            len > 6 && classes >= 3,
            len > 8 && classes == 4,
            len > 6 && (n >= 3 && sm >= 3 || n >= 3 && cap >= 3 || n >= 3 && ot >= 2
                || sm >= 3 && cap >= 3 || sm >= 3 && ot >= 2 || cap >= 3 && ot >= 2),
            len > 8 && (n >= 2 && sm >= 2 && cap >= 2 || n >= 2 && sm >= 2 && ot >= 2
                || n >= 2 && cap >= 2 && ot >= 2 || sm >= 2 && cap >= 2 && ot >= 2),
            len > 10 && n >= 2 && sm >= 2 && cap >= 2 && ot >= 2,
            ot >= 3,
            ot >= 6,
            len > 12,
            len >= 16,
        ]
        var level = gains.filter { $0 }.count

        // 减分；只把 A-Z 转小写
        let lower = s.map { $0 >= 65 && $0 <= 90 ? $0 + 32 : $0 }
        for keys in sequenceKeys where len >= sequenceMin {
            if (0...(len - sequenceMin)).contains(where: { keys.contains(pack(lower, $0)) }) {
                level -= 1
            }
        }
        if n == len || sm == len || cap == len { level -= 1 }
        if len % 2 == 0 && isSameChar(s, 0, len / 2) && isSameChar(s, len / 2, len) { level -= 1 }
        if containsDate(s) { level -= 1 }
        if dictionary.contains(where: { contains(lower, $0) || contains($0, lower) }) { level -= 1 }
        if len <= 6 { level -= 1 }
        if len <= 4 { level -= 1 }
        if len <= 3 || isSameChar(s, 0, len) { level = 0 }
        return max(level, 0)
    }

    /// 计算密码强度等级；空串抛 `PwScoreError.emptyPassword`
    public static func level(_ password: String) throws -> Level {
        switch try score(password) {
        case ...3: return .easy
        case ...6: return .medium
        case ...9: return .strong
        case ...12: return .veryStrong
        default: return .extremelyStrong
        }
    }

    private static func charType(_ c: UInt16) -> Int {
        switch c {
        case 48...57: return num
        case 97...122: return small
        case 65...90: return capital
        default: return other
        }
    }

    private static func repeatUnitLength(_ s: [UInt16]) -> Int {
        let n = s.count
        var prefix = [Int](repeating: 0, count: n)
        for i in stride(from: 1, to: n, by: 1) {
            var k = prefix[i - 1]
            while k > 0 && s[i] != s[k] { k = prefix[k - 1] }
            if s[i] == s[k] { k += 1 }
            prefix[i] = k
        }
        let period = n - prefix[n - 1]
        return n % period == 0 ? period : n
    }

    private static func isSameChar(_ s: [UInt16], _ from: Int, _ to: Int) -> Bool {
        var i = from + 1
        while i < to {
            if s[i] != s[from] { return false }
            i += 1
        }
        return true
    }

    private static func contains(_ haystack: [UInt16], _ needle: [UInt16]) -> Bool {
        let m = needle.count
        if m > haystack.count { return false }
        var i = 0
        while i + m <= haystack.count {
            var j = 0
            while j < m && haystack[i + j] == needle[j] { j += 1 }
            if j == m { return true }
            i += 1
        }
        return false
    }

    private static func digits(_ s: [UInt16], _ start: Int, _ count: Int) -> Int {
        var value = 0
        for i in start..<(start + count) { value = value * 10 + Int(s[i] - 48) }
        return value
    }

    private static func daysInMonth(_ year: Int, _ month: Int) -> Int {
        let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return month == 2 && leap ? 29 : daysInMonthTable[month - 1]
    }

    private static func containsDate(_ s: [UInt16]) -> Bool {
        var run = 0
        for i in s.indices {
            run = charType(s[i]) == num ? run + 1 : 0
            if run >= 8 {
                let start = i - 7
                let year = digits(s, start, 4), month = digits(s, start + 4, 2), day = digits(s, start + 6, 2)
                if (1950...2049).contains(year) && (1...12).contains(month) && day >= 1
                    && day <= daysInMonth(year, month) {
                    return true
                }
            }
        }
        return false
    }
}
