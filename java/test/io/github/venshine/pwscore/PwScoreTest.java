package io.github.venshine.pwscore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 回归测试（无第三方依赖），运行方式见 java/test.sh。
 * <p>
 * 用法：PwScoreTest &lt;score-snapshot.tsv&gt; 校验快照；PwScoreTest --generate &lt;score-snapshot.tsv&gt;
 * 按本实现重写快照的分值列（注释行和密码列保持不变），之后在 diff 里逐行确认变化。
 */
public class PwScoreTest {

    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        if (args.length == 2 && args[0].equals("--generate")) {
            generate(args[1]);
            return;
        }
        checkSnapshot(args[0]);

        // 连续字母、键盘序列、连续数字出现在开头时也要扣分，与出现在中间时得分一致
        assertEquals("letter sequence at index 0", score("bcdefghijklmnopq"), score("abcdefghijklmnop"));
        assertEquals("keyboard sequence at index 0", score("wertyuiop"), score("qwertyuiop"));
        assertEquals("number sequence at index 0", score("12345678901"), score("01234567890"));

        // 包含字典词（不区分大小写）的密码应比同构成的非字典密码分低
        assertTrue("dictionary word inside password", score("Password123!") < score("Xkqwvbzm123!"));

        // 组合加分的长度门槛约束整组条件：大小写待遇一致，5 位密码不会到 MEDIUM
        assertEquals("upper and lower treated alike", score("kqm482"), score("KQM482"));
        assertTrue("5-char password stays easy", PwScore.level("Ab1!x") == PwScore.Level.EASY);
        assertTrue("medium level", PwScore.level("admin@2024") == PwScore.Level.MEDIUM);

        // 由更短片段重复组成的密码，按重复单元评分
        assertEquals("repeat x2 scores as unit", score("Ab1!"), score("Ab1!Ab1!"));
        assertEquals("repeat x5 scores as unit", score("Ab1!"), score("Ab1!Ab1!Ab1!Ab1!Ab1!"));

        // 含 4 位及以上的连续片段要扣分；嵌在密码中的 8 位 yyyyMMdd 日期要扣分，非法日期不扣
        assertTrue("keyboard run inside", score("Qwerty123!") < score("Xmzkvb847!"));
        assertTrue("embedded date", score("Tom19881010!") < score("Tom19881310!"));
        assertTrue("leap day is a date", score("x20240229y") < score("x20230229y"));

        // 「全部相同」只看码元是否真的相同：空白、Tab 不能当成与首字符相同（3.0.0 修复）
        assertTrue("spaces are not the same char as x", score("x xx  xxx   x") > 0);
        assertTrue("tabs are not the same char as x", score("x\txx\t\txxx\t\t\tx") > 0);

        // 有效长度里一个 emoji（代理对）算 1 个字符，与其他非 ASCII 字符同类
        assertEquals("emoji counts as one character", score("abécd"), score("ab😀cd"));

        // 审查发现的绕过写法（3.0.0 修复）
        assertTrue("near repeat is not strong", score("Ab1!Ab1!Ab1!x") <= 6);
        assertTrue("char runs do not add length", score("aaaaaaaaaaaaaaaaaaaaAb1!") <= 6);
        assertTrue("repeated symbol counts once", score("Pa1!!!!!!!!!!!!") <= 3);
        assertEquals("fullwidth folds to ascii", score("QWERTY123!a"), score("ＱＷＥＲＴＹ123!a"));
        assertTrue("non-ascii letters are not symbols", score("Пароль2024") <= 6);
        assertTrue("year is penalized", score("Summer2024!") < score("Summer2051!"));
        assertTrue("ddMMyyyy is a date", score("Tom10101988!") < score("Tom10131988!"));
        assertTrue("keyboard column", score("1qaz2wsx") < score("1qmz7wpx"));
        assertTrue("pinyin dictionary word", score("Woaini1314!") < score("Wuoqnx1314!"));

        // 步长为 2 的数字和字母序列按连续序列扣分（3.1.0）
        assertEquals("step-2 digits like a run", score("Xk12345!"), score("Xk13579!"));
        assertTrue("step-2 digits", score("Xk13579!") < score("Xk13597!"));
        assertTrue("step-2 letters reversed", score("Hq!wusq8x") < score("Hq!wuqs8x"));

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

        // 只有 null 和空串抛异常，且信息可区分
        expectThrows("null input", null, "password is null");
        expectThrows("empty input", "", "password is empty");

        if (failures > 0) {
            System.out.println(failures + " test(s) failed");
            System.exit(1);
        }
        System.out.println("java: all tests passed");
    }

    /**
     * 快照里每行是「分值<TAB>等级<TAB>密码」，锁住全部规则的口径，任何评分变化都会在这里显式失败
     */
    private static void checkSnapshot(String path) throws IOException {
        int checked = 0;
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\t", 3);
            String expected = cols[0] + "\t" + cols[1];
            String actual = PwScore.score(cols[2]) + "\t" + PwScore.level(cols[2]).name();
            assertTrue("snapshot [" + cols[2] + "]: expected " + expected + " but was " + actual, expected.equals(actual));
            checked++;
        }
        assertTrue("snapshot has no cases", checked > 0);
    }

    private static void generate(String path) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                out.add(line);
                continue;
            }
            String password = line.split("\t", 3)[2];
            out.add(PwScore.score(password) + "\t" + PwScore.level(password).name() + "\t" + password);
        }
        Files.write(Paths.get(path), (String.join("\n", out) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static int score(String password) {
        return PwScore.score(password);
    }

    private static void assertEquals(String name, int expected, int actual) {
        if (expected != actual) {
            fail(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static void expectThrows(String name, String password, String message) {
        try {
            score(password);
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
