// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。.NET 的 string 是 UTF-16，与规则口径一致。
//
// Licensed under the Apache License, Version 2.0
using System;
using System.Collections.Generic;

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
            "password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
            "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd",
        };

        private static readonly string[][] Sequences =
        {
            new[] { "abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba" },
            new[] { "01234567890", "09876543210" },
            new[] { "qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz" },
        };

        private const int SequenceMin = 4;

        /// <summary>每组序列所有长度为 4 的片段</summary>
        private static readonly HashSet<string>[] SequenceKeys = BuildSequenceKeys();

        private static readonly int[] DaysInMonthTable = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };

        private const int Num = 0, Small = 1, Capital = 2, Other = 3;

        /// <summary>计算密码强度分值，0 到 14</summary>
        /// <exception cref="ArgumentNullException">password 为 null</exception>
        /// <exception cref="ArgumentException">password 为空串</exception>
        public static int Score(string password)
        {
            if (password == null) throw new ArgumentNullException(nameof(password), "password is null");
            if (password.Length == 0) throw new ArgumentException("password is empty", nameof(password));

            // 整串由更短片段重复组成时，按重复单元评分
            string s = password.Substring(0, RepeatUnitLength(password));
            int len = s.Length;

            var count = new int[4];
            foreach (char c in s) count[CharType(c)]++;
            int num = count[Num], small = count[Small], capital = count[Capital], other = count[Other];
            int classes = (num > 0 ? 1 : 0) + (small > 0 ? 1 : 0) + (capital > 0 ? 1 : 0) + (other > 0 ? 1 : 0);

            int level = 0;
            // 加分
            if (num > 0) level++;
            if (small > 0) level++;
            if (len > 4 && capital > 0) level++;
            if (len > 6 && other > 0) level++;
            if (len > 4 && classes >= 2) level++;
            if (len > 6 && classes >= 3) level++;
            if (len > 8 && classes == 4) level++;
            if (len > 6 && (num >= 3 && small >= 3 || num >= 3 && capital >= 3 || num >= 3 && other >= 2
                            || small >= 3 && capital >= 3 || small >= 3 && other >= 2 || capital >= 3 && other >= 2)) level++;
            if (len > 8 && (num >= 2 && small >= 2 && capital >= 2 || num >= 2 && small >= 2 && other >= 2
                            || num >= 2 && capital >= 2 && other >= 2 || small >= 2 && capital >= 2 && other >= 2)) level++;
            if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) level++;
            if (other >= 3) level++;
            if (other >= 6) level++;
            if (len > 12) level++;
            if (len >= 16) level++;

            // 减分；只把 A-Z 转小写，不用 ToLower，避免受区域设置和 Unicode 小写表影响
            var chars = s.ToCharArray();
            for (int i = 0; i < chars.Length; i++)
            {
                if (chars[i] >= 'A' && chars[i] <= 'Z') chars[i] = (char)(chars[i] + 32);
            }
            string lower = new string(chars);
            foreach (var keys in SequenceKeys)
            {
                for (int i = 0; i + SequenceMin <= len; i++)
                {
                    if (keys.Contains(lower.Substring(i, SequenceMin)))
                    {
                        level--;
                        break;
                    }
                }
            }
            if (num == len || small == len || capital == len) level--;
            if (len % 2 == 0 && IsSameChar(s, 0, len / 2) && IsSameChar(s, len / 2, len)) level--;
            if (ContainsDate(s)) level--;
            foreach (string word in Dictionary)
            {
                // Ordinal：按码元比较，不受区域设置影响
                if (lower.IndexOf(word, StringComparison.Ordinal) >= 0 || word.IndexOf(lower, StringComparison.Ordinal) >= 0)
                {
                    level--;
                    break;
                }
            }
            if (len <= 6) level--;
            if (len <= 4) level--;
            if (len <= 3 || IsSameChar(s, 0, len)) level = 0;
            return Math.Max(level, 0);
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
                foreach (string seq in Sequences[g])
                {
                    for (int i = 0; i + SequenceMin <= seq.Length; i++) result[g].Add(seq.Substring(i, SequenceMin));
                }
            }
            return result;
        }

        private static int CharType(char c)
        {
            if (c >= '0' && c <= '9') return Num;
            if (c >= 'a' && c <= 'z') return Small;
            if (c >= 'A' && c <= 'Z') return Capital;
            return Other;
        }

        private static int RepeatUnitLength(string s)
        {
            int n = s.Length;
            var prefix = new int[n];
            for (int i = 1; i < n; i++)
            {
                int k = prefix[i - 1];
                while (k > 0 && s[i] != s[k]) k = prefix[k - 1];
                if (s[i] == s[k]) k++;
                prefix[i] = k;
            }
            int period = n - prefix[n - 1];
            return n % period == 0 ? period : n;
        }

        private static bool IsSameChar(string s, int from, int to)
        {
            for (int i = from + 1; i < to; i++)
            {
                if (s[i] != s[from]) return false;
            }
            return true;
        }

        private static int Digits(string s, int start, int count)
        {
            int value = 0;
            for (int i = start; i < start + count; i++) value = value * 10 + (s[i] - '0');
            return value;
        }

        private static int DaysInMonth(int year, int month)
        {
            bool leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
            return month == 2 && leap ? 29 : DaysInMonthTable[month - 1];
        }

        private static bool ContainsDate(string s)
        {
            int run = 0;
            for (int i = 0; i < s.Length; i++)
            {
                run = CharType(s[i]) == Num ? run + 1 : 0;
                if (run < 8) continue;
                int start = i - 7;
                int year = Digits(s, start, 4), month = Digits(s, start + 4, 2), day = Digits(s, start + 6, 2);
                if (year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1 && day <= DaysInMonth(year, month))
                {
                    return true;
                }
            }
            return false;
        }
    }
}
