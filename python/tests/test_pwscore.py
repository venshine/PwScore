"""跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时"""

import time
import unittest
from pathlib import Path

import pwscore

SNAPSHOT = Path(__file__).resolve().parents[2] / "spec" / "score-snapshot.tsv"


class PwScoreTest(unittest.TestCase):
    def test_snapshot(self):
        checked = 0
        for line in SNAPSHOT.read_text(encoding="utf-8").split("\n"):
            if not line or line.startswith("#"):
                continue
            expected_score, expected_level, password = line.split("\t", 2)
            actual = (str(pwscore.score(password)), pwscore.level(password).value)
            self.assertEqual((expected_score, expected_level), actual, repr(password))
            checked += 1
        self.assertGreater(checked, 0)

    def test_errors(self):
        with self.assertRaises(TypeError):
            pwscore.score(None)
        with self.assertRaises(ValueError):
            pwscore.score("")
        self.assertEqual(pwscore.Level.EXTREMELY_STRONG, pwscore.level("2hAj5#mne-ix.86H"))

    def test_linear_time(self):
        start = time.perf_counter()
        pwscore.score("a" * 200000 + "b")
        self.assertLess(time.perf_counter() - start, 2)


if __name__ == "__main__":
    unittest.main()
