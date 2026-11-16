package io.github.venshine.pwscore

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv。算法全在 commonMain，在 JVM 上跑即可覆盖 */
class PwScoreTest {

    @Test
    fun matchesSnapshot() {
        val lines = File("../spec/score-snapshot.tsv").readText(Charsets.UTF_8).split("\n")
        var checked = 0
        for (line in lines) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val (score, level, password) = line.split("\t", limit = 3)
            assertEquals("$score\t$level", "${PwScore.score(password)}\t${PwScore.level(password).name}", "[$password]")
            checked++
        }
        assertTrue(checked > 0)
    }

    @Test
    fun rejectsEmptyPassword() {
        assertFailsWith<IllegalArgumentException> { PwScore.score("") }
        assertEquals(Level.EXTREMELY_STRONG, PwScore.level("2hAj5#mne-ix.86H"))
    }

    @Test
    fun linearTime() {
        val start = System.nanoTime()
        PwScore.score("a".repeat(200_000) + "b")
        assertTrue(System.nanoTime() - start < 2_000_000_000L)
    }
}
