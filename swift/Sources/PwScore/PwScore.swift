// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。按 UTF-16 码元处理。
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
    ].map { Array($0.utf16) }

    /// 序列分组，每组命中扣 1 分；每个序列的反向自动加入
    private static let sequences: [[String]] = [
        ["abcdefghijklmnopqrstuvwxyz", "acegikmoqsuwy", "bdfhjlnprtvxz"],
        ["01234567890", "02468", "13579"],
        ["qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/", "qazwsxedcrfvtgbyhnujmikolp",
         "1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0", "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+"],
    ]

    private static let sequenceMin = 4
    private static let maxBlock = 16
    private static let leet: [UInt16: UInt16] = Dictionary(
        uniqueKeysWithValues: zip(Array("@401!3$57".utf16), Array("aaoiiesst".utf16)))

    /// 每组序列（含反向）所有长度为 4 的片段，按每码元 16 位打包成 UInt64
    private static let sequenceKeys: [Set<UInt64>] = sequences.map { group in
        var keys = Set<UInt64>()
        for base in group {
            for seq in [Array(base.utf16), Array(base.utf16.reversed())] {
                for i in 0...(seq.count - sequenceMin) {
                    keys.insert(pack(seq, i))
                }
            }
        }
        return keys
    }

    private static let daysInMonthTable = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

    private static let num = 0, small = 1, capital = 2, intl = 3, other = 4

    /// 计算密码强度分值，0 到 14；空串抛 `PwScoreError.emptyPassword`
    public static func score(_ password: String) throws -> Int {
        if password.isEmpty { throw PwScoreError.emptyPassword }
        // 全角 ASCII 还原为半角
        let s = password.utf16.map { $0 >= 0xFF01 && $0 <= 0xFF5E ? $0 - 0xFEE0 : $0 }
        let n = s.count
        let len = effectiveLength(s)

        // 各类统计「不同字符」的个数
        var sets = [Set<UInt16>](repeating: [], count: 5)
        for c in s { sets[charType(c)].insert(c) }
        let (nm, sm, cap, it, ot) = (sets[num].count, sets[small].count, sets[capital].count,
                                     sets[intl].count, sets[other].count)
        let classes = sets.filter { !$0.isEmpty }.count
        let distinct = Set(s).count

        // 加分
        let gains: [Bool] = [
            nm > 0,
            sm > 0,
            it > 0,
            len > 4 && cap > 0,
            len > 6 && ot > 0,
            len > 4 && classes >= 2,
            len > 6 && classes >= 3,
            len > 8 && classes >= 4,
            len > 6 && (nm >= 3 && sm >= 3 || nm >= 3 && cap >= 3 || nm >= 3 && ot >= 2
                || sm >= 3 && cap >= 3 || sm >= 3 && ot >= 2 || cap >= 3 && ot >= 2),
            len > 8 && (nm >= 2 && sm >= 2 && cap >= 2 || nm >= 2 && sm >= 2 && ot >= 2
                || nm >= 2 && cap >= 2 && ot >= 2 || sm >= 2 && cap >= 2 && ot >= 2),
            len > 10 && nm >= 2 && sm >= 2 && cap >= 2 && ot >= 2,
            ot >= 3,
            ot >= 6,
        ]
        var level = gains.filter { $0 }.count + lengthPoints(len)

        // 减分；只把 A-Z 转小写
        let lower = s.map { $0 >= 65 && $0 <= 90 ? $0 + 32 : $0 }
        var covered = [Bool](repeating: false, count: n)
        for keys in sequenceKeys {
            var hit = false
            var i = 0
            while i + sequenceMin <= n {
                if keys.contains(pack(lower, i)) {
                    hit = true
                    for j in i..<(i + sequenceMin) { covered[j] = true }
                }
                i += 1
            }
            if hit { level -= 1 }
        }
        if 2 * covered.filter({ $0 }).count >= n { level -= 1 }
        if len < 6 && classes == 1 && (nm > 0 || sm > 0 || cap > 0) { level -= 1 }
        if n % 2 == 0 && isSameChar(s, 0, n / 2) && isSameChar(s, n / 2, n) { level -= 1 }
        if containsDateOrYear(s) { level -= 1 }
        if len >= 8 && distinct <= max(4, len / 3) { level -= 1 }
        let leetUnits = lower.map { leet[$0] ?? $0 }
        if dictionary.contains(where: { contains(lower, $0) || contains($0, lower) || contains(leetUnits, $0) }) {
            level -= 2
        }
        if len <= 3 || isSameChar(s, 0, n) { level = 0 }
        return min(max(level, 0), 14)
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

    private static func pack(_ s: [UInt16], _ start: Int) -> UInt64 {
        var key: UInt64 = 0
        for i in start..<(start + sequenceMin) { key = key << 16 | UInt64(s[i]) }
        return key
    }

    private static func charType(_ c: UInt16) -> Int {
        switch c {
        case 48...57: return num
        case 97...122: return small
        case 65...90: return capital
        case 128...: return intl
        default: return other
        }
    }

    private static func isPair(_ s: [UInt16], _ i: Int, _ end: Int) -> Bool {
        i + 1 < end && (0xD800...0xDBFF).contains(s[i]) && (0xDC00...0xDFFF).contains(s[i + 1])
    }

    private static func regionEquals(_ s: [UInt16], _ a: Int, _ b: Int, _ count: Int) -> Bool {
        var k = 0
        while k < count {
            if s[a + k] != s[b + k] { return false }
            k += 1
        }
        return true
    }

    private static func characterCount(_ s: [UInt16], _ from: Int, _ to: Int) -> Int {
        var i = from, count = 0
        while i < to {
            i += isPair(s, i, to) ? 2 : 1
            count += 1
        }
        return count
    }

    /// 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
    private static func effectiveLength(_ s: [UInt16]) -> Int {
        let n = s.count
        var len = 0, i = 0
        while i < n {
            var block = 0
            var m = 1
            while m <= maxBlock && i + 2 * m <= n {
                if s[i] == s[i + m] && regionEquals(s, i, i + m, m) {
                    block = m
                    break
                }
                m += 1
            }
            if block > 0 {
                var r = 2
                while i + (r + 1) * block <= n && regionEquals(s, i, i + r * block, block) { r += 1 }
                len += block == 1 ? 2 : characterCount(s, i, i + block)
                i += r * block
            } else {
                i += isPair(s, i, n) ? 2 : 1
                len += 1
            }
        }
        return len
    }

    private static func lengthPoints(_ len: Int) -> Int {
        if len <= 4 { return -2 }
        if len == 5 { return -1 }
        if len < 8 { return 0 }
        return min((len - 8) / 4 + 1, 5)
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
        guard m > 0, m <= haystack.count else { return m == 0 }
        let first = needle[0]
        var i = 0
        while i + m <= haystack.count {
            // 先比首码元，绝大多数位置在这里就排除
            if haystack[i] == first && regionEqualsAt(haystack, i, needle) { return true }
            i += 1
        }
        return false
    }

    private static func regionEqualsAt(_ haystack: [UInt16], _ i: Int, _ needle: [UInt16]) -> Bool {
        var j = 0
        while j < needle.count {
            if haystack[i + j] != needle[j] { return false }
            j += 1
        }
        return true
    }

    private static func digits(_ s: [UInt16], _ start: Int, _ count: Int) -> Int {
        var value = 0
        for i in start..<(start + count) { value = value * 10 + Int(s[i] - 48) }
        return value
    }

    private static func isYear(_ year: Int) -> Bool { (1950...2049).contains(year) }

    private static func isValidDate(_ year: Int, _ month: Int, _ day: Int) -> Bool {
        if !(1...12).contains(month) || day < 1 { return false }
        let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        return day <= (month == 2 && leap ? 29 : daysInMonthTable[month - 1])
    }

    private static func isDate8(_ s: [UInt16], _ k: Int) -> Bool {
        let head = digits(s, k, 4)
        if isYear(head) && isValidDate(head, digits(s, k + 4, 2), digits(s, k + 6, 2)) { return true }
        let tail = digits(s, k + 4, 4)
        return isYear(tail) && (isValidDate(tail, digits(s, k + 2, 2), digits(s, k, 2))
            || isValidDate(tail, digits(s, k, 2), digits(s, k + 2, 2)))
    }

    /// 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
    private static func containsDateOrYear(_ s: [UInt16]) -> Bool {
        var i = 0
        while i < s.count {
            if charType(s[i]) != num {
                i += 1
                continue
            }
            let start = i
            while i < s.count && charType(s[i]) == num { i += 1 }
            let run = i - start
            var k = start
            while k + 8 <= i {
                if isDate8(s, k) { return true }
                k += 1
            }
            if run == 6 && isValidDate(2000 + digits(s, start, 2), digits(s, start + 2, 2), digits(s, start + 4, 2)) {
                return true
            }
            if run == 4 && isYear(digits(s, start, 4)) { return true }
        }
        return false
    }
}
