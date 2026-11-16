#!/bin/sh
# 编译并运行 Java 回归测试；传 --generate 时按参考实现重写 spec/score-snapshot.tsv 的分值列
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" "$DIR"/src/io/github/venshine/pwscore/*.java "$DIR"/test/io/github/venshine/pwscore/*.java
java -cp "$OUT" io.github.venshine.pwscore.PwScoreTest "$@" "$DIR/../spec/score-snapshot.tsv"
