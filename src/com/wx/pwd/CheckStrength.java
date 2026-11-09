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

package com.wx.pwd;

import java.util.Arrays;
import java.util.Locale;

/**
 * 检测密码强度
 *
 * @author venshine
 */
public class CheckStrength {

    public enum LEVEL {
        EASY, MEDIUM, STRONG, VERY_STRONG, EXTREMELY_STRONG
    }

    private static final int NUM = 1;
    private static final int SMALL_LETTER = 2;
    private static final int CAPITAL_LETTER = 3;
    private static final int OTHER_CHAR = 4;

    /**
     * Simple password dictionary
     */
    private final static String[] DICTIONARY = {"password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
            "1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd"};

    /**
     * 连续序列，按类别分组，每类命中扣 1 分：字母表、数字（含 9 到 0 的回绕）、键盘三行。
     * 每组都同时给出正向和反向，匹配时不区分大小写。
     */
    private final static String[][] SEQUENCES = {
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

    /**
     * Check character's type, includes num, capital letter, small letter and other character.
     *
     * @param c
     * @return
     */
    private static int checkCharacterType(char c) {
        if (c >= 48 && c <= 57) {
            return NUM;
        }
        if (c >= 65 && c <= 90) {
            return CAPITAL_LETTER;
        }
        if (c >= 97 && c <= 122) {
            return SMALL_LETTER;
        }
        return OTHER_CHAR;
    }

    /**
     * Check password's strength
     *
     * @param passwd
     * @return strength level, 0 to 14
     * @throws IllegalArgumentException if passwd is null ("password is null") or empty ("password is empty")
     */
    public static int checkPasswordStrength(String passwd) {
        if (passwd == null) {
            throw new IllegalArgumentException("password is null");
        }
        if (passwd.isEmpty()) {
            throw new IllegalArgumentException("password is empty");
        }
        int len = passwd.length();

        // 整串由更短的片段重复组成（如 Ab1!Ab1!Ab1!）时，重复几乎不增加破解难度，按重复单元评分
        int unit = repeatUnitLength(passwd);
        if (unit < len) {
            return checkPasswordStrength(passwd.substring(0, unit));
        }

        int level = 0;

        // 一次遍历统计四类字符的数量，下标为字符类型常量
        int[] count = new int[OTHER_CHAR + 1];
        for (int i = 0; i < len; i++) {
            count[checkCharacterType(passwd.charAt(i))]++;
        }
        int num = count[NUM];
        int small = count[SMALL_LETTER];
        int capital = count[CAPITAL_LETTER];
        int other = count[OTHER_CHAR];
        int classes = (num > 0 ? 1 : 0) + (small > 0 ? 1 : 0) + (capital > 0 ? 1 : 0) + (other > 0 ? 1 : 0);

        // increase points
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

        // 长度门槛约束整组条件
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
            if (len >= 16) {
                level++;
            }
        }

        // decrease points
        String lowerPasswd = passwd.toLowerCase(Locale.ROOT);
        for (int[] keys : SEQUENCE_KEYS) {
            if (containsSequence(lowerPasswd, keys)) {
                level--;
            }
        }

        if (num == len || small == len || capital == len) {
            level--;
        }

        // 前后两半各自由同一字符组成，如 aaabbb；整串重复的情况已在开头按重复单元处理
        if (len % 2 == 0) {
            String part1 = passwd.substring(0, len / 2);
            String part2 = passwd.substring(len / 2);
            if (StringUtils.isCharEqual(part1) && StringUtils.isCharEqual(part2)) {
                level--;
            }
        }

        if (containsDate(passwd)) {
            level--;
        }

        if (null != DICTIONARY && DICTIONARY.length > 0) {// dictionary
            for (int i = 0; i < DICTIONARY.length; i++) {
                // 密码包含字典词，或密码本身是字典词的一部分
                if (lowerPasswd.contains(DICTIONARY[i]) || DICTIONARY[i].contains(lowerPasswd)) {
                    level--;
                    break;
                }
            }
        }

        if (len <= 6) {
            level--;
            if (len <= 4) {
                level--;
                if (len <= 3) {
                    level = 0;
                }
            }
        }

        if (StringUtils.isCharEqual(passwd)) {
            level = 0;
        }

        if (level < 0) {
            level = 0;
        }

        return level;
    }

    /**
     * 整串的最小重复单元长度：由更短片段重复组成时返回该片段的长度，否则返回串长。
     * 用 KMP 前缀函数计算，O(n)；不用 (s + s).indexOf(s, 1)，String.indexOf 是朴素匹配，
     * 遇到 aaa...ab 这类几乎周期的长输入会退化为平方级
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
     * 是否含有长度不小于 SEQUENCE_MIN 的片段，且该片段是这组序列中某个序列的子串。
     * 更长的连续片段必然包含一个 SEQUENCE_MIN 长的片段，所以只需检查定长窗口
     *
     * @param lower 已转小写的密码
     * @param keys  这组序列的片段打包值（已排序）
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
    private static boolean containsDate(String passwd) {
        int run = 0;
        for (int i = 0; i < passwd.length(); i++) {
            run = checkCharacterType(passwd.charAt(i)) == NUM ? run + 1 : 0;
            if (run >= 8) {
                int start = i - 7;
                int year = digits(passwd, start, 4);
                int month = digits(passwd, start + 4, 2);
                int day = digits(passwd, start + 6, 2);
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

    /**
     * Get password strength level, includes easy, midium, strong, very strong, extremely strong
     *
     * @param passwd
     * @return
     */
    public static LEVEL getPasswordLevel(String passwd) {
        int level = checkPasswordStrength(passwd);
        switch (level) {
            case 0:
            case 1:
            case 2:
            case 3:
                return LEVEL.EASY;
            case 4:
            case 5:
            case 6:
                return LEVEL.MEDIUM;
            case 7:
            case 8:
            case 9:
                return LEVEL.STRONG;
            case 10:
            case 11:
            case 12:
                return LEVEL.VERY_STRONG;
            default:
                return LEVEL.EXTREMELY_STRONG;
        }
    }

}