# ROADMAP

## 当前状态

- 3.0.0 改造（更名 PwScore，新增 13 种语言实现和在线 demo）已在本地完成，尚未提交、推送和打 tag。
- 版本记录以 `CHANGELOG.md` 和 GitHub Release 为准。

## 进行中

- 3.0.0 发布（待确认）：
  - GitHub 仓库改名为 PwScore。
  - 推送并通过 CI，其中 C# 第一次验证要在 CI 上完成。
  - 启用 GitHub Pages（Source 选 GitHub Actions）。
  - 打 `3.0.0` 和 `go/v3.0.0` 两个 tag，发布 GitHub Release，核对 JitPack 构建。

## 待办

- 第二期：上架各语言的包仓库。2026-11-15 查询时，`pwscore` 这个名字在 npm、PyPI、crates.io、pub.dev、NuGet、RubyGems 上都没有被注册；CPAN 和 Maven Central 尚未查询。上架前需要主人准备各平台的账号和 token；pub.dev 还要求 `dart/` 下有 README.md 和 CHANGELOG.md（`dart pub publish --dry-run` 已提示）。

## 待确认

- 仓库改名后，旧坐标 `com.github.venshine:CheckPasswordStrength:2.x` 在 JitPack 上是否仍可用。
- 仓库改名后，GitHub 对旧地址的重定向范围，执行改名前查官方文档核实。

## 最近验证

- 2026-11-16 12:03 本地全量：`./test.sh` 覆盖 13 种语言，全部通过（C# 本机无工具链，已跳过）；C/C++ 另外用 ASan 和 UBSan 跑过；Kotlin 的 JS、linuxX64、iosArm64 目标编译通过；web demo 用 Playwright 核对了浅色、深色、390px 宽度和中英文，评分与快照一致，控制台无报错。
