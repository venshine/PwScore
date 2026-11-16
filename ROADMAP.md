# ROADMAP

## 当前状态

- 3.0.0 已发布：更名为 PwScore，新增 13 种语言实现和在线 demo，并完成评分规则重做（长度阶梯、有效长度、不同字符计数、非 ASCII 单独成类、字典扩充与替换识别、年份与日期、键盘模式与覆盖率）。评分审查报告见 `.private/reviews/2026-11-16-scoring-loopholes.md`。
- 版本记录以 `CHANGELOG.md` 和 GitHub Release 为准。

## 最近完成

- 2026-11-16 15:02 发布 3.0.0：在 244a20d 上打 `3.0.0` 和 `go/v3.0.0` 两个 tag，GitHub Release 附 PwScore.jar；JitPack 构建 ok，Pages 已部署新规则。
- 2026-11-16 12:07 GitHub 仓库改名为 PwScore（旧地址 301 跳转），启用 Pages（GitHub Actions 部署），更新仓库 About；推送 0e668d1，CI 和 Pages 全部通过。

## 待办

- 第二期：上架各语言的包仓库。2026-11-15 查询时，`pwscore` 这个名字在 npm、PyPI、crates.io、pub.dev、NuGet、RubyGems 上都没有被注册；CPAN 和 Maven Central 尚未查询。上架前需要主人准备各平台的账号和 token；pub.dev 还要求 `dart/` 下有 README.md 和 CHANGELOG.md（`dart pub publish --dry-run` 已提示）。

## 待确认

- 仓库改名后，旧坐标 `com.github.venshine:CheckPasswordStrength:2.x` 在 JitPack 上是否仍可用。

## 最近验证

- 2026-11-16 15:02 线上：CI 的 12 个 job 全部通过（含 C#），Release 附件可下载（200），JitPack 3.0.0 构建 status ok，`go mod download github.com/venshine/PwScore/go/v3@v3.0.0`（GOPROXY=direct）成功，在线 demo 的 pwscore.js 已是新规则。
- 2026-11-16 14:55 本地全量：`./test.sh` 覆盖 13 种语言，全部通过，179 条快照逐行一致（C# 本机无工具链，已跳过）；Java 参考实现与独立编写的 JS 原型逐条比对 0 差异；C/C++ 用 ASan 和 UBSan 跑过；Kotlin 的 JS、linuxX64、iosArm64 目标编译通过。
