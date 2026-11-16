# PwScore 项目规范

密码强度评分库（0-14 分、五个等级），同一套规则用 14 种语言各自原生实现，零依赖。项目原名 CheckPasswordStrength，3.0.0 起更名。

## 目录与职责

- `spec/`：`SPEC.md` 是评分规则的唯一口径；`score-snapshot.tsv` 是所有语言共用的一致性用例。
- `java/`：参考实现，负责生成快照。Gradle 工程（`build.gradle`、`settings.gradle`、`gradlew`）留在仓库根目录，因为 JitPack 从根目录构建。
- `kotlin/`：KMP 实现，是独立的 Gradle 工程，用 `./gradlew -p kotlin` 构建，不并入根工程，避免 JitPack 去编译 Native 目标。
- `swift/`、`objc/`：两个 SwiftPM target，共用根目录的 `Package.swift`（SwiftPM 只认仓库根目录）。
- `c/`：C 核心；`cpp/`：C++ header-only 包装，算法在 C 核心里。两者共用根目录的 `CMakeLists.txt`。
- `dart/`、`js/`、`python/`、`go/`、`rust/`、`csharp/`、`ruby/`、`perl/`：各语言实现及测试。
- `web/`：在线 demo，由 `.github/workflows/pages.yml` 发布到 GitHub Pages。`web/pwscore.js` 是指向 `js/pwscore.js` 的软链接，不另存副本。
- `process/release.md`：发版参数，通用步骤见 `release-seal` skill。

## 规则口径（改评分必读）

- 修改评分规则的顺序：先改 `spec/SPEC.md` → 改 Java 参考实现 → 运行 `java/test.sh --generate` 重写快照 → 在 diff 里逐行确认变化 → 同步修改其余 13 种语言，直到 `./test.sh` 全部通过 → 写入 `CHANGELOG.md`。
- 所有实现都必须遵守这几条口径：
  - 长度、下标、比较一律按 UTF-16 码元计。
  - 只把 A-Z 转小写，不调用语言自带的 Unicode 小写函数。
  - 字节输入按 UTF-8 解码，非法字节按 U+FFFD。
  - 空串报错，报错方式按各语言惯例。
- 不允许为了让某种语言跑通而修改快照；快照只能由 Java 参考实现生成。
- 各实现不引入第三方运行时依赖。测试只用语言自带的工具，Dart 的 `package:test` 除外。

## 版本与发布

- 全部语言共用一个版本号。版本号所在的位置用 `grep -rn "<版本号>" --exclude-dir=.tmp --exclude-dir=.private --exclude=CHANGELOG.md .` 查全，不另外维护清单。
- tag 用不带 `v` 的版本号（如 `3.0.0`），服务 JitPack、SwiftPM 和各语言的 git 依赖。Go 模块另外需要 `go/vX.Y.Z` 这个 tag；主版本变化时要同步修改 `go/go.mod` 里模块路径的 `/vN` 后缀。
- 第一期只做「仓库直装 / 拷文件」，不发布到各语言的包仓库；进度见 `ROADMAP.md`。

## 文档

- `README.md`（英文）和 `README-CN.md`（中文）的代码块必须逐字一致，只有说明文字不同。可用这条命令核对：`diff <(awk '/^```/{f=!f;print;next} f' README.md) <(awk '/^```/{f=!f;print;next} f' README-CN.md)`。
- `CHANGELOG.md` 用中文；GitHub Release、commit 信息用英文。

## 验证

- `./test.sh` 运行本机已装工具链的全部语言，`./test.sh go rust` 只运行指定语言。构建产物写入 `.tmp/test-build/`。
- 本机没有 .NET SDK，C# 只在 CI（`.github/workflows/ci.yml`）里验证。
- 改动 `web/` 后，本地起静态服务预览（`python3 -m http.server --directory web`），浅色、深色、移动端宽度和中英文都要核对。
