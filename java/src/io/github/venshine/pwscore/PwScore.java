/*
 * Copyright (C) 2014 venshine.cn@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.venshine.pwscore;

import java.util.Arrays;

/**
 * 密码强度评分。规则口径以仓库 spec/SPEC.md 为准，这里是参考实现，
 * spec/score-snapshot.tsv 由本实现生成（见 PwScoreTest --generate）。
 *
 * @author venshine
 */
public final class PwScore {

    /**
     * 强度等级：0-3 EASY，4-6 MEDIUM，7-9 STRONG，10-12 VERY_STRONG，13-14 EXTREMELY_STRONG
     */
    public enum Level {
        EASY, MEDIUM, STRONG, VERY_STRONG, EXTREMELY_STRONG
    }

    private static final int NUM = 0;
    private static final int SMALL = 1;
    private static final int CAPITAL = 2;
    private static final int OTHER = 3;

    private static final String[] DICTIONARY = {"password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
            "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd"};

    /**
     * 连续序列，按类别分组，每类命中扣 1 分：字母表、数字（含 9 到 0 的回绕）、键盘三行，正反向都列出
     */
    private static final String[][] SEQUENCES = {
            {"abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba"},
            {"01234567890", "09876543210"},
            {"qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz"}};

    /**
     * 连续片段达到这个长度才算序列。片段按每字符 7 位打包成 int，因此不能超过 4
     */
    private static final int SEQUENCE_MIN = 4;

    /**
     * 每组序列里所有长度为 SEQUENCE_MIN 的片段的打包值，已排序，用于二分查找
     */
    private static final int[][] SEQUENCE_KEYS = sequenceKeys();

    private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private PwScore() {
    }

    /**
     * 计算密码强度分值
     *
     * @param password 密码，长度按 UTF-16 码元计
     * @return 0 到 14
     * @throws IllegalArgumentException password 为 null（"password is null"）或空串（"password is empty"）
     */
    public static int score(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password is null");
        }
        if (password.isEmpty()) {
            throw new IllegalArgumentException("password is empty");
        }
        // 整串由更短的片段重复组成（如 Ab1!Ab1!Ab1!）时，重复几乎不增加破解难度，按重复单元评分
        String s = password.substring(0, repeatUnitLength(password));
        int len = s.length();

        int[] count = new int[4];
        for (int i = 0; i < len; i++) {
            count[charType(s.charAt(i))]++;
        }
        int num = count[NUM];
        int small = count[SMALL];
        int capital = count[CAPITAL];
        int other = count[OTHER];
        int classes = (num > 0 ? 1 : 0) + (small > 0 ? 1 : 0) + (capital > 0 ? 1 : 0) + (other > 0 ? 1 : 0);

        int level = 0;

        // 加分
        if (num > 0) {
            level++;
        }
        if (small > 0) {
            level++;
        }
        if (len > 4 && capital > 0) {
            level++;
        }
        if (len > 6 && other > 0) {
            level++;
        }
        if (len > 4 && classes >= 2) {
            level++;
        }
        if (len > 6 && classes >= 3) {
            level++;
        }
        if (len > 8 && classes == 4) {
            level++;
        }
        if (len > 6 && (num >= 3 && small >= 3
                || num >= 3 && capital >= 3
                || num >= 3 && other >= 2
                || small >= 3 && capital >= 3
                || small >= 3 && other >= 2
                || capital >= 3 && other >= 2)) {
            level++;
        }
        if (len > 8 && (num >= 2 && small >= 2 && capital >= 2
                || num >= 2 && small >= 2 && other >= 2
                || num >= 2 && capital >= 2 && other >= 2
                || small >= 2 && capital >= 2 && other >= 2)) {
            level++;
        }
        if (len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2) {
            level++;
        }
        if (other >= 3) {
            level++;
        }
        if (other >= 6) {
            level++;
        }
        if (len > 12) {
            level++;
        }
        if (len >= 16) {
            level++;
        }

        // 减分
        String lower = asciiLower(s);
        for (int[] keys : SEQUENCE_KEYS) {
            if (containsSequence(lower, keys)) {
                level--;
            }
        }
        if (num == len || small == len || capital == len) {
            level--;
        }
        // 前后两半各自由同一字符组成，如 aaabbb
        if (len % 2 == 0 && isSameChar(s, 0, len / 2) && isSameChar(s, len / 2, len)) {
            level--;
        }
        if (containsDate(s)) {
            level--;
        }
        for (String word : DICTIONARY) {
            // 密码包含字典词，或密码本身是字典词的一部分
            if (lower.contains(word) || word.contains(lower)) {
                level--;
                break;
            }
        }
        if (len <= 6) {
            level--;
        }
        if (len <= 4) {
            level--;
        }
        if (len <= 3 || isSameChar(s, 0, len)) {
            level = 0;
        }
        return Math.max(level, 0);
    }

    /**
     * 计算密码强度等级
     *
     * @throws IllegalArgumentException 同 {@link #score(String)}
     */
    public static Level level(String password) {
        int score = score(password);
        if (score <= 3) {
            return Level.EASY;
        }
        if (score <= 6) {
            return Level.MEDIUM;
        }
        if (score <= 9) {
            return Level.STRONG;
        }
        if (score <= 12) {
            return Level.VERY_STRONG;
        }
        return Level.EXTREMELY_STRONG;
    }

    private static int charType(char c) {
        if (c >= '0' && c <= '9') {
            return NUM;
        }
        if (c >= 'a' && c <= 'z') {
            return SMALL;
        }
        if (c >= 'A' && c <= 'Z') {
            return CAPITAL;
        }
        return OTHER;
    }

    /**
     * 只把 A-Z 转成 a-z，其余码元不变，长度不变；各语言实现都按这个口径，避免依赖各自的 Unicode 小写表
     */
    private static String asciiLower(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] += 'a' - 'A';
            }
        }
        return new String(chars);
    }

    /**
     * [from, to) 区间内每个码元都与第一个相同
     */
    private static boolean isSameChar(String s, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            if (s.charAt(i) != s.charAt(from)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 整串的最小重复单元长度：由更短片段重复组成时返回该片段的长度，否则返回串长。
     * 用 KMP 前缀函数计算，O(n)
     */
    private static int repeatUnitLength(String s) {
        int n = s.length();
        int[] prefix = new int[n];
        for (int i = 1; i < n; i++) {
            int k = prefix[i - 1];
            while (k > 0 && s.charAt(i) != s.charAt(k)) {
                k = prefix[k - 1];
            }
            if (s.charAt(i) == s.charAt(k)) {
                k++;
            }
            prefix[i] = k;
        }
        int period = n - prefix[n - 1];
        return n % period == 0 ? period : n;
    }

    /**
     * 是否含有长度为 SEQUENCE_MIN、且是这组某个序列子串的片段。
     * 更长的连续片段必然包含一个 SEQUENCE_MIN 长的片段，所以只需检查定长窗口
     */
    private static boolean containsSequence(String lower, int[] keys) {
        for (int i = 0; i + SEQUENCE_MIN <= lower.length(); i++) {
            int key = packWindow(lower, i);
            if (key >= 0 && Arrays.binarySearch(keys, key) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把 s 从 start 起 SEQUENCE_MIN 个字符按每字符 7 位打包；含非 ASCII 字符时返回 -1（序列只含 ASCII）
     */
    private static int packWindow(String s, int start) {
        int key = 0;
        for (int i = start; i < start + SEQUENCE_MIN; i++) {
            char c = s.charAt(i);
            if (c >= 128) {
                return -1;
            }
            key = (key << 7) | c;
        }
        return key;
    }

    private static int[][] sequenceKeys() {
        int[][] keys = new int[SEQUENCES.length][];
        for (int g = 0; g < SEQUENCES.length; g++) {
            int size = 0;
            for (String sequence : SEQUENCES[g]) {
                size += sequence.length() - SEQUENCE_MIN + 1;
            }
            int[] groupKeys = new int[size];
            int n = 0;
            for (String sequence : SEQUENCES[g]) {
                for (int j = 0; j + SEQUENCE_MIN <= sequence.length(); j++) {
                    groupKeys[n++] = packWindow(sequence, j);
                }
            }
            Arrays.sort(groupKeys);
            keys[g] = groupKeys;
        }
        return keys;
    }

    /**
     * 是否含有 8 位 ASCII 数字组成的 yyyyMMdd 日期：年份 1950-2049，月份 1-12，日期不超过当月天数（含闰年 2 月 29 日）
     */
    private static boolean containsDate(String s) {
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            run = charType(s.charAt(i)) == NUM ? run + 1 : 0;
            if (run >= 8) {
                int start = i - 7;
                int year = digits(s, start, 4);
                int month = digits(s, start + 4, 2);
                int day = digits(s, start + 6, 2);
                if (year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1
                        && day <= daysInMonth(year, month)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int digits(String s, int start, int count) {
        int value = 0;
        for (int i = start; i < start + count; i++) {
            value = value * 10 + (s.charAt(i) - '0');
        }
        return value;
    }

    private static int daysInMonth(int year, int month) {
        boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
        return month == 2 && leap ? 29 : DAYS_IN_MONTH[month - 1];
    }
}
