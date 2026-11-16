#!/bin/sh
# 运行全部语言的一致性测试；本机缺少某个工具链时跳过该语言并在末尾列出。
# 用法：./test.sh            全部语言
#       ./test.sh go rust   只跑指定语言
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
SNAPSHOT="$ROOT/spec/score-snapshot.tsv"
BUILD="$ROOT/.tmp/test-build"
ALL="java kotlin swift objc dart js python go rust c cpp csharp ruby perl"
LANGS="${*:-$ALL}"
failed=""
skipped=""

have() { command -v "$1" >/dev/null 2>&1; }

run() {
  case "$1" in
    java) have javac && "$ROOT/java/test.sh" ;;
    kotlin) have java && "$ROOT/gradlew" -q -p "$ROOT/kotlin" jvmTest ;;
    swift) have swift && swift test -c release --package-path "$ROOT" --scratch-path "$BUILD/swift" --filter PwScoreTests ;;
    objc) have swift && [ "$(uname)" = Darwin ] && swift test -c release --package-path "$ROOT" --scratch-path "$BUILD/swift" --filter PWScoreTests ;;
    dart) have dart && (cd "$ROOT/dart" && dart pub get >/dev/null && dart test) ;;
    js) have node && node --test "$ROOT/js/pwscore.test.js" ;;
    python) have python3 && PYTHONPATH="$ROOT/python" python3 -m unittest discover -s "$ROOT/python/tests" -t "$ROOT/python/tests" ;;
    go) have go && go -C "$ROOT/go" test ./... ;;
    rust) have cargo && CARGO_TARGET_DIR="$BUILD/rust" cargo test --manifest-path "$ROOT/rust/Cargo.toml" ;;
    c | cpp) have cmake && cmake -S "$ROOT" -B "$BUILD/cmake" >/dev/null && cmake --build "$BUILD/cmake" >/dev/null &&
      ctest --test-dir "$BUILD/cmake" -R "^$1\$" --output-on-failure ;;
    csharp) have dotnet && dotnet run --project "$ROOT/csharp/PwScore.Tests" -- "$SNAPSHOT" ;;
    ruby) have ruby && ruby "$ROOT/ruby/test/test_pwscore.rb" ;;
    perl) have prove && prove "$ROOT/perl/t" ;;
    *) echo "unknown language: $1" >&2; return 1 ;;
  esac
}

toolchain() {
  case "$1" in
    java | kotlin) echo java ;;
    swift | objc) echo swift ;;
    js) echo node ;;
    python) echo python3 ;;
    rust) echo cargo ;;
    c | cpp) echo cmake ;;
    csharp) echo dotnet ;;
    perl) echo prove ;;
    *) echo "$1" ;;
  esac
}

for lang in $LANGS; do
  if ! have "$(toolchain "$lang")" || { [ "$lang" = objc ] && [ "$(uname)" != Darwin ]; }; then
    skipped="$skipped $lang"
    continue
  fi
  echo "==> $lang"
  run "$lang" || failed="$failed $lang"
done

[ -n "$skipped" ] && echo "skipped (toolchain not found):$skipped"
if [ -n "$failed" ]; then
  echo "FAILED:$failed"
  exit 1
fi
echo "all selected languages passed"
