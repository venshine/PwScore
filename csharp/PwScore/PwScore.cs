// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。.NET 的 string 是 UTF-16，与规则口径一致。
//
// Licensed under the Apache License, Version 2.0
using System;
using System.Collections.Generic;
using System.Linq;

namespace Venshine
{
    /// <summary>强度等级，按分值从低到高</summary>
    public enum PwLevel
    {
        Easy,
        Medium,
        Strong,
        VeryStrong,
        ExtremelyStrong,
    }

    /// <summary>密码强度评分</summary>
    public static class PwScore
    {
        private static readonly string[] Dictionary =
        {
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
        };

        /// <summary>序列分组，每组命中扣 1 分；每个序列的反向自动加入</summary>
        private static readonly string[][] Sequences =
        {
            new[] { "abcdefghijklmnopqrstuvwxyz", "acegikmoqsuwy", "bdfhjlnprtvxz" },
            new[] { "01234567890", "02468", "13579" },
            new[]
            {
                "qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/",
                "qazwsxedcrfvtgbyhnujmikolp", "1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0",
                "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+",
            },
        };

        private const int SequenceMin = 4;
        private const int MaxBlock = 16;
        private const string LeetFrom = "@401!3$57";
        private const string LeetTo = "aaoiiesst";

        /// <summary>每组序列（含反向）所有长度为 4 的片段</summary>
        private static readonly HashSet<string>[] SequenceKeys = BuildSequenceKeys();

        private static readonly int[] DaysInMonthTable = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };

        private const int Num = 0, Small = 1, Capital = 2, Intl = 3, Other = 4;

        /// <summary>计算密码强度分值，0 到 14</summary>
        /// <exception cref="ArgumentNullException">password 为 null</exception>
        /// <exception cref="ArgumentException">password 为空串</exception>
        public static int Score(string password)
        {
            if (password == null) throw new ArgumentNullException(nameof(password), "password is null");
            if (password.Length == 0) throw new ArgumentException("password is empty", nameof(password));

            // 全角 ASCII 还原为半角
            char[] s = password.ToCharArray();
            for (int i = 0; i < s.Length; i++)
            {
                if (s[i] >= '！' && s[i] <= '～') s[i] = (char)(s[i] - 0xFEE0);
            }
            int n = s.Length;
            int len = EffectiveLength(s);

            // 各类统计「不同字符」的个数
            var sets = new HashSet<char>[5];
            for (int k = 0; k < sets.Length; k++) sets[k] = new HashSet<char>();
            foreach (char c in s) sets[CharType(c)].Add(c);
            int num = sets[Num].Count, small = sets[Small].Count, capital = sets[Capital].Count;
            int intl = sets[Intl].Count, other = sets[Other].Count;
            int classes = sets.Count(set => set.Count > 0);
            int distinct = new HashSet<char>(s).Count;

            int level = 0;
            // 加分
            if (num > 0) level++;
            if (small > 0) level++;
            if (intl > 0) level++;
            if (len > 4 && capital > 0) level++;
            if (len > 6 && other > 0) level++;
            if (len > 4 && classes >= 2) level++;
            if (len > 6 && classes >= 3) level++;
            if (len > 8 && classes >= 4) level++;
            if (len > 6 && (num >= 3 && small >= 3 || num >= 3 && capital >= 3 || num >= 3 && other >= 2
                            || small >= 3 && capital >= 3 || small >= 3 && other >= 2 || capital >= 3 && other >= 2)) level++;
            if (len > 8 && (num >= 2 && small >= 2 && capital >= 2 || num >= 2 && small >= 2 && other >= 2
                            || num >= 2 && capital >= 2 && other >= 2 || small >= 2 && capital >= 2 && other >= 2)) level++;
            if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++;
            if (other >= 3) level++;
            if (other >= 6) level++;
            level += LengthPoints(len);

            // 减分；只把 A-Z 转小写，不用 ToLower，避免受区域设置和 Unicode 小写表影响
            var lowerChars = new char[n];
            var leetChars = new char[n];
            for (int i = 0; i < n; i++)
            {
                char c = s[i] >= 'A' && s[i] <= 'Z' ? (char)(s[i] + 32) : s[i];
                lowerChars[i] = c;
                int k = LeetFrom.IndexOf(c);
                leetChars[i] = k >= 0 ? LeetTo[k] : c;
            }
            string lower = new string(lowerChars);
            string leet = new string(leetChars);
            var covered = new bool[n];
            foreach (var keys in SequenceKeys)
            {
                bool hit = false;
                for (int i = 0; i + SequenceMin <= n; i++)
                {
                    if (!keys.Contains(lower.Substring(i, SequenceMin))) continue;
                    hit = true;
                    for (int j = i; j < i + SequenceMin; j++) covered[j] = true;
                }
                if (hit) level--;
            }
            if (2 * covered.Count(b => b) >= n) level--;
            if (len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)) level--;
            if (n % 2 == 0 && IsSameChar(s, 0, n / 2) && IsSameChar(s, n / 2, n)) level--;
            if (ContainsDateOrYear(s)) level--;
            if (len >= 8 && distinct <= Math.Max(4, len / 3)) level--;
            foreach (string word in Dictionary)
            {
                // Ordinal：按码元比较，不受区域设置影响
                if (lower.IndexOf(word, StringComparison.Ordinal) >= 0 || word.IndexOf(lower, StringComparison.Ordinal) >= 0
                    || leet.IndexOf(word, StringComparison.Ordinal) >= 0)
                {
                    level -= 2;
                    break;
                }
            }
            if (len <= 3 || IsSameChar(s, 0, n)) level = 0;
            return Math.Min(Math.Max(level, 0), 14);
        }

        /// <summary>计算密码强度等级，异常同 <see cref="Score"/></summary>
        public static PwLevel Level(string password)
        {
            int score = Score(password);
            if (score <= 3) return PwLevel.Easy;
            if (score <= 6) return PwLevel.Medium;
            if (score <= 9) return PwLevel.Strong;
            if (score <= 12) return PwLevel.VeryStrong;
            return PwLevel.ExtremelyStrong;
        }

        /// <summary>与其他语言一致的等级名，如 "VERY_STRONG"</summary>
        public static string Name(PwLevel level)
        {
            switch (level)
            {
                case PwLevel.Easy: return "EASY";
                case PwLevel.Medium: return "MEDIUM";
                case PwLevel.Strong: return "STRONG";
                case PwLevel.VeryStrong: return "VERY_STRONG";
                default: return "EXTREMELY_STRONG";
            }
        }

        private static HashSet<string>[] BuildSequenceKeys()
        {
            var result = new HashSet<string>[Sequences.Length];
            for (int g = 0; g < Sequences.Length; g++)
            {
                result[g] = new HashSet<string>(StringComparer.Ordinal);
                foreach (string baseSeq in Sequences[g])
                {
                    var chars = baseSeq.ToCharArray();
                    Array.Reverse(chars);
                    foreach (string seq in new[] { baseSeq, new string(chars) })
                    {
                        for (int i = 0; i + SequenceMin <= seq.Length; i++) result[g].Add(seq.Substring(i, SequenceMin));
                    }
                }
            }
            return result;
        }

        private static int CharType(char c)
        {
            if (c >= '0' && c <= '9') return Num;
            if (c >= 'a' && c <= 'z') return Small;
            if (c >= 'A' && c <= 'Z') return Capital;
            if (c >= 128) return Intl;
            return Other;
        }

        private static bool IsPair(char[] s, int i, int end)
        {
            return i + 1 < end && char.IsHighSurrogate(s[i]) && char.IsLowSurrogate(s[i + 1]);
        }

        private static bool RegionEquals(char[] s, int a, int b, int count)
        {
            for (int k = 0; k < count; k++)
            {
                if (s[a + k] != s[b + k]) return false;
            }
            return true;
        }

        private static int CharacterCount(char[] s, int from, int to)
        {
            int count = 0;
            for (int i = from; i < to; i += IsPair(s, i, to) ? 2 : 1) count++;
            return count;
        }

        /// <summary>有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1</summary>
        private static int EffectiveLength(char[] s)
        {
            int n = s.Length, len = 0, i = 0;
            while (i < n)
            {
                int block = 0;
                for (int m = 1; m <= MaxBlock && i + 2 * m <= n; m++)
                {
                    if (s[i] == s[i + m] && RegionEquals(s, i, i + m, m))
                    {
                        block = m;
                        break;
                    }
                }
                if (block > 0)
                {
                    int r = 2;
                    while (i + (r + 1) * block <= n && RegionEquals(s, i, i + r * block, block)) r++;
                    len += block == 1 ? 2 : CharacterCount(s, i, i + block);
                    i += r * block;
                }
                else
                {
                    i += IsPair(s, i, n) ? 2 : 1;
                    len++;
                }
            }
            return len;
        }

        private static int LengthPoints(int len)
        {
            if (len <= 4) return -2;
            if (len == 5) return -1;
            if (len < 8) return 0;
            return Math.Min((len - 8) / 4 + 1, 5);
        }

        private static bool IsSameChar(char[] s, int from, int to)
        {
            for (int i = from + 1; i < to; i++)
            {
                if (s[i] != s[from]) return false;
            }
            return true;
        }

        private static int Digits(char[] s, int start, int count)
        {
            int value = 0;
            for (int i = start; i < start + count; i++) value = value * 10 + (s[i] - '0');
            return value;
        }

        private static bool IsYear(int year)
        {
            return year >= 1950 && year <= 2049;
        }

        private static bool IsValidDate(int year, int month, int day)
        {
            if (month < 1 || month > 12 || day < 1) return false;
            bool leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
            return day <= (month == 2 && leap ? 29 : DaysInMonthTable[month - 1]);
        }

        private static bool IsDate8(char[] s, int k)
        {
            int head = Digits(s, k, 4);
            if (IsYear(head) && IsValidDate(head, Digits(s, k + 4, 2), Digits(s, k + 6, 2))) return true;
            int tail = Digits(s, k + 4, 4);
            return IsYear(tail) && (IsValidDate(tail, Digits(s, k + 2, 2), Digits(s, k, 2))
                                    || IsValidDate(tail, Digits(s, k, 2), Digits(s, k + 2, 2)));
        }

        /// <summary>8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份</summary>
        private static bool ContainsDateOrYear(char[] s)
        {
            int i = 0;
            while (i < s.Length)
            {
                if (CharType(s[i]) != Num)
                {
                    i++;
                    continue;
                }
                int start = i;
                while (i < s.Length && CharType(s[i]) == Num) i++;
                int run = i - start;
                for (int k = start; k + 8 <= i; k++)
                {
                    if (IsDate8(s, k)) return true;
                }
                if (run == 6 && IsValidDate(2000 + Digits(s, start, 2), Digits(s, start + 2, 2), Digits(s, start + 4, 2)))
                {
                    return true;
                }
                if (run == 4 && IsYear(Digits(s, start, 4))) return true;
            }
            return false;
        }
    }
}
