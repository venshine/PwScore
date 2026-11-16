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
import java.util.HashSet;
import java.util.Set;

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
    private static final int INTL = 3;
    private static final int OTHER = 4;

    private static final String[] DICTIONARY = {
            "password", "passwd", "admin", "iloveyou", "sunshine", "adobe123", "abc123", "123123", "1314520", "a1b2c3",
            "123qwe", "aaa111", "qweasd",
            "welcome", "letmein", "login", "master", "monkey", "dragon", "shadow", "princess", "football", "baseball",
            "soccer", "superman", "batman", "starwars", "pokemon", "freedom", "whatever", "trustno1", "secret", "hello",
            "summer", "winter", "spring", "autumn", "flower", "angel", "lovely", "loveyou", "killer", "hunter", "cookie",
            "banana", "orange", "purple", "silver", "golden", "diamond", "chocolate", "computer", "internet", "google",
            "apple", "changeme", "default", "guest",
            "woaini", "iloveu", "zhang", "wang", "yang", "huang", "zhao", "xiaoming", "laopo", "laogong", "baobao",
            "nihao", "wodemima", "mima", "qq123",
            "5201314", "520520", "147258", "159753", "147852", "789456", "741852", "963852", "258369", "123321", "112233"};

    /**
     * 序列分组，每组命中扣 1 分：字母表、数字（含 9 到 0 的回绕）、键盘（横排、竖列、斜列、之字形、shift 符号行）。
     * 每个序列的反向在 sequenceKeys() 里自动加入
     */
    private static final String[][] SEQUENCES = {
            {"abcdefghijklmnopqrstuvwxyz", "acegikmoqsuwy", "bdfhjlnprtvxz"},
            {"01234567890", "02468", "13579"},
            {"qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/",
                    "qazwsxedcrfvtgbyhnujmikolp", "1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0",
                    "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+"}};

    /**
     * 连续片段达到这个长度才算序列。片段按每字符 7 位打包成 int，因此不能超过 4
     */
    private static final int SEQUENCE_MIN = 4;

    /**
     * 有效长度扫描时尝试的最长重复片段
     */
    private static final int MAX_BLOCK = 16;

    /**
     * 每组序列（含反向）所有长度为 SEQUENCE_MIN 的片段的打包值，已排序，用于二分查找
     */
    private static final int[][] SEQUENCE_KEYS = sequenceKeys();

    private static final int[] DAYS_IN_MONTH = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private PwScore() {
    }

    /**
     * 计算密码强度分值
     *
     * @param password 密码，按 UTF-16 码元处理
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
        char[] s = foldFullwidth(password);
        int n = s.length;
        int len = effectiveLength(s);

        // 各类统计「不同字符」的个数，同一个字符重复出现不重复计分
        Set<Character>[] seen = newSets(5);
        Set<Character> all = new HashSet<>();
        for (char c : s) {
            seen[charType(c)].add(c);
            all.add(c);
        }
        int num = seen[NUM].size();
        int small = seen[SMALL].size();
        int capital = seen[CAPITAL].size();
        int intl = seen[INTL].size();
        int other = seen[OTHER].size();
        int classes = 0;
        for (Set<Character> set : seen) {
            if (!set.isEmpty()) {
                classes++;
            }
        }

        int level = 0;

        // 加分
        if (num > 0) {
            level++;
        }
        if (small > 0) {
            level++;
        }
        if (intl > 0) {
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
        if (len > 8 && classes >= 4) {
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
        level += lengthPoints(len);

        // 减分
        char[] lower = asciiLower(s);
        boolean[] covered = new boolean[n];
        for (int[] keys : SEQUENCE_KEYS) {
            if (markSequences(lower, keys, covered)) {
                level--;
            }
        }
        int coveredCount = 0;
        for (boolean b : covered) {
            if (b) {
                coveredCount++;
            }
        }
        // 密码一半以上由序列组成
        if (2 * coveredCount >= n) {
            level--;
        }
        if (len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)) {
            level--;
        }
        // 前后两半各自由同一字符组成，如 aaabbb
        if (n % 2 == 0 && isSameChar(s, 0, n / 2) && isSameChar(s, n / 2, n)) {
            level--;
        }
        if (containsDateOrYear(s)) {
            level--;
        }
        if (len >= 8 && all.size() <= Math.max(4, len / 3)) {
            level--;
        }
        String lowerText = new String(lower);
        String leetText = new String(leet(lower));
        for (String word : DICTIONARY) {
            // 密码包含字典词（含 P@ssw0rd 这类替换写法），或密码本身是字典词的一部分
            if (lowerText.contains(word) || word.contains(lowerText) || leetText.contains(word)) {
                level -= 2;
                break;
            }
        }
        if (len <= 3 || isSameChar(s, 0, n)) {
            level = 0;
        }
        return Math.min(Math.max(level, 0), 14);
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

    @SuppressWarnings("unchecked")
    private static Set<Character>[] newSets(int count) {
        Set<Character>[] sets = new Set[count];
        for (int i = 0; i < count; i++) {
            sets[i] = new HashSet<>();
        }
        return sets;
    }

    /**
     * 全角 ASCII（U+FF01-U+FF5E）还原为半角
     */
    private static char[] foldFullwidth(String password) {
        char[] s = password.toCharArray();
        for (int i = 0; i < s.length; i++) {
            if (s[i] >= '！' && s[i] <= '～') {
                s[i] -= 0xFEE0;
            }
        }
        return s;
    }

    /**
     * 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
     */
    private static int effectiveLength(char[] s) {
        int n = s.length;
        int len = 0;
        int i = 0;
        while (i < n) {
            int block = 0;
            for (int m = 1; m <= MAX_BLOCK && i + 2 * m <= n; m++) {
                if (s[i] == s[i + m] && regionEquals(s, i, i + m, m)) {
                    block = m;
                    break;
                }
            }
            if (block > 0) {
                int r = 2;
                while (i + (r + 1) * block <= n && regionEquals(s, i, i + r * block, block)) {
                    r++;
                }
                len += block == 1 ? 2 : characterCount(s, i, i + block);
                i += r * block;
            } else if (isSurrogatePair(s, i, n)) {
                len++;
                i += 2;
            } else {
                len++;
                i++;
            }
        }
        return len;
    }

    private static boolean regionEquals(char[] s, int a, int b, int count) {
        for (int k = 0; k < count; k++) {
            if (s[a + k] != s[b + k]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSurrogatePair(char[] s, int i, int end) {
        return i + 1 < end && Character.isHighSurrogate(s[i]) && Character.isLowSurrogate(s[i + 1]);
    }

    private static int characterCount(char[] s, int from, int to) {
        int count = 0;
        for (int i = from; i < to; i++) {
            if (isSurrogatePair(s, i, to)) {
                i++;
            }
            count++;
        }
        return count;
    }

    /**
     * 长度分：&lt;=4 为 -2，5 为 -1，6-7 为 0，8-11 为 +1，此后每 4 位 +1，24 位及以上 +5
     */
    private static int lengthPoints(int len) {
        if (len <= 4) {
            return -2;
        }
        if (len == 5) {
            return -1;
        }
        if (len < 8) {
            return 0;
        }
        return Math.min((len - 8) / 4 + 1, 5);
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
        if (c >= 128) {
            return INTL;
        }
        return OTHER;
    }

    /**
     * 只把 A-Z 转成 a-z，其余码元不变；各语言实现都按这个口径，避免依赖各自的 Unicode 小写表
     */
    private static char[] asciiLower(char[] s) {
        char[] chars = s.clone();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] += 'a' - 'A';
            }
        }
        return chars;
    }

    /**
     * 常见替换字符还原：@4→a，0→o，1!→i，3→e，$5→s，7→t
     */
    private static char[] leet(char[] lower) {
        char[] chars = lower.clone();
        for (int i = 0; i < chars.length; i++) {
            int k = "@401!3$57".indexOf(chars[i]);
            if (k >= 0) {
                chars[i] = "aaoiiesst".charAt(k);
            }
        }
        return chars;
    }

    /**
     * [from, to) 区间内每个码元都与第一个相同
     */
    private static boolean isSameChar(char[] s, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            if (s[i] != s[from]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否含有长度为 SEQUENCE_MIN、且是这组某个序列子串的片段；命中的位置记入 covered
     */
    private static boolean markSequences(char[] lower, int[] keys, boolean[] covered) {
        boolean hit = false;
        for (int i = 0; i + SEQUENCE_MIN <= lower.length; i++) {
            int key = packWindow(lower, i);
            if (key >= 0 && Arrays.binarySearch(keys, key) >= 0) {
                hit = true;
                Arrays.fill(covered, i, i + SEQUENCE_MIN, true);
            }
        }
        return hit;
    }

    /**
     * 把 s 从 start 起 SEQUENCE_MIN 个字符按每字符 7 位打包；含非 ASCII 字符时返回 -1（序列只含 ASCII）
     */
    private static int packWindow(char[] s, int start) {
        int key = 0;
        for (int i = start; i < start + SEQUENCE_MIN; i++) {
            char c = s[i];
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
            Set<Integer> groupKeys = new HashSet<>();
            for (String sequence : SEQUENCES[g]) {
                String reversed = new StringBuilder(sequence).reverse().toString();
                for (String seq : new String[]{sequence, reversed}) {
                    char[] chars = seq.toCharArray();
                    for (int j = 0; j + SEQUENCE_MIN <= chars.length; j++) {
                        groupKeys.add(packWindow(chars, j));
                    }
                }
            }
            int[] sorted = new int[groupKeys.size()];
            int k = 0;
            for (int key : groupKeys) {
                sorted[k++] = key;
            }
            Arrays.sort(sorted);
            keys[g] = sorted;
        }
        return keys;
    }

    /**
     * 是否含有日期或年份：8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
     */
    private static boolean containsDateOrYear(char[] s) {
        int i = 0;
        while (i < s.length) {
            if (charType(s[i]) != NUM) {
                i++;
                continue;
            }
            int start = i;
            while (i < s.length && charType(s[i]) == NUM) {
                i++;
            }
            int run = i - start;
            for (int k = start; k + 8 <= i; k++) {
                if (isDate8(s, k)) {
                    return true;
                }
            }
            if (run == 6 && isValidDate(2000 + digits(s, start, 2), digits(s, start + 2, 2), digits(s, start + 4, 2))) {
                return true;
            }
            if (run == 4 && isYear(digits(s, start, 4))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDate8(char[] s, int k) {
        int head = digits(s, k, 4);
        if (isYear(head) && isValidDate(head, digits(s, k + 4, 2), digits(s, k + 6, 2))) {
            return true;
        }
        int tail = digits(s, k + 4, 4);
        return isYear(tail) && (isValidDate(tail, digits(s, k + 2, 2), digits(s, k, 2))
                || isValidDate(tail, digits(s, k, 2), digits(s, k + 2, 2)));
    }

    private static boolean isYear(int year) {
        return year >= 1950 && year <= 2049;
    }

    private static boolean isValidDate(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1) {
            return false;
        }
        boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
        return day <= (month == 2 && leap ? 29 : DAYS_IN_MONTH[month - 1]);
    }

    private static int digits(char[] s, int start, int count) {
        int value = 0;
        for (int i = start; i < start + count; i++) {
            value = value * 10 + (s[i] - '0');
        }
        return value;
    }
}
