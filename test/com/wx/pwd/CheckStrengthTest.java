package com.wx.pwd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 回归测试（无第三方依赖），运行方式见 test.sh。参数为评分快照文件路径。
 */
public class CheckStrengthTest {

    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        checkSnapshot(args[0]);

        // 连续字母、键盘序列、连续数字出现在开头时也要扣分，与出现在中间时得分一致
        assertEquals("letter sequence at index 0",
                score("bcdefghijklmnopq"), score("abcdefghijklmnop"));
        assertEquals("keyboard sequence at index 0",
                score("wertyuiop"), score("qwertyuiop"));
        assertEquals("number sequence at index 0",
                score("12345678901"), score("01234567890"));

        // 包含字典词（不区分大小写）的密码应比同构成的非字典密码分低
        assertTrue("dictionary word inside password",
                score("Password123!") < score("Xkqwvbzm123!"));

        // 超长纯数字不应抛异常
        try {
            score("1234567890123");
            score("12345678901234567890");
        } catch (RuntimeException e) {
            fail("long numeric password throws " + e);
        }

        // 等级枚举：4-6 分为 MEDIUM
        assertTrue("medium level", CheckStrength.getPasswordLevel("admin@2024") == CheckStrength.LEVEL.MEDIUM);

        // 组合加分的长度门槛约束整组条件：大小写待遇一致，5 位密码不会到 MEDIUM
        assertEquals("upper and lower treated alike", score("kqm482"), score("KQM482"));
        assertTrue("5-char password stays easy", CheckStrength.getPasswordLevel("Ab1!x") == CheckStrength.LEVEL.EASY);

        // 由更短片段重复组成的密码，按重复单元评分
        assertEquals("repeat x2 scores as unit", score("Ab1!"), score("Ab1!Ab1!"));
        assertEquals("repeat x5 scores as unit", score("Ab1!"), score("Ab1!Ab1!Ab1!Ab1!Ab1!"));
        assertEquals("repeated emoji scores as unit", 0, score("😀😀😀😀"
                + "😀😀😀😀"));

        // 含 4 位及以上的连续片段（字母、数字、键盘行，正反向、不分大小写）要扣分
        assertTrue("keyboard run inside", score("Qwerty123!") < score("Xmzkvb847!"));
        assertTrue("letter run inside", score("Abcd2024!x") < score("Aqcm2024!x"));
        assertTrue("descending digits inside", score("P@ss9876word") < score("P@ss9176word"));

        // 嵌在密码中的 8 位 yyyyMMdd 日期要扣分；非法日期不扣
        assertTrue("embedded date", score("Tom19881010!") < score("Tom19881310!"));
        assertTrue("leap day is a date", score("x20240229y") < score("x20230229y"));

        // 重复检测必须是线性时间：20 万个 a 加一个 b 这种几乎周期的长输入，不能退化成平方级
        StringBuilder nearlyPeriodic = new StringBuilder();
        for (int i = 0; i < 200000; i++) {
            nearlyPeriodic.append('a');
        }
        nearlyPeriodic.append('b');
        long start = System.nanoTime();
        score(nearlyPeriodic.toString());
        long millis = (System.nanoTime() - start) / 1000000;
        assertTrue("nearly periodic long input took " + millis + " ms", millis < 2000);

        // 只有 null 和空串抛异常，且信息可区分；字面量 "null" 和纯空白正常评分
        expectThrows("null input", null, "password is null");
        expectThrows("empty input", "", "password is empty");
        try {
            score("null");
            score("NULL");
            score("        ");
        } catch (IllegalArgumentException e) {
            fail("\"null\" or blank password throws " + e);
        }

        if (failures > 0) {
            System.out.println(failures + " test(s) failed");
            System.exit(1);
        }
        System.out.println("all tests passed");
    }

    /**
     * 快照里每行是「分值<TAB>等级<TAB>密码」，EXC 表示应抛 IllegalArgumentException。
     * 锁住全部规则的现有口径，任何评分变化都会在这里显式失败。
     */
    private static void checkSnapshot(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        int checked = 0;
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\t", 3);
            String expected = cols[0] + "\t" + cols[1];
            String actual;
            try {
                actual = CheckStrength.checkPasswordStrength(cols[2]) + "\t" + CheckStrength.getPasswordLevel(cols[2]).name();
            } catch (IllegalArgumentException e) {
                actual = "EXC\t-";
            }
            assertTrue("snapshot [" + cols[2] + "]: expected " + expected + " but was " + actual, expected.equals(actual));
            checked++;
        }
        assertTrue("snapshot has no cases", checked > 0);
    }

    private static int score(String passwd) {
        return CheckStrength.checkPasswordStrength(passwd);
    }

    private static void assertEquals(String name, int expected, int actual) {
        if (expected != actual) {
            fail(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectThrows(String name, String passwd, String message) {
        try {
            score(passwd);
            fail(name + ": expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(name + ": message was \"" + e.getMessage() + "\"", message.equals(e.getMessage()));
        }
    }

    private static void assertTrue(String name, boolean condition) {
        if (!condition) {
            fail(name);
        }
    }

    private static void fail(String message) {
        failures++;
        System.out.println("FAIL " + message);
    }
}
