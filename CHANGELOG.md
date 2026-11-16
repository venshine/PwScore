## 3.0.0

### 项目更名与不兼容变更
* 项目由 CheckPasswordStrength 更名为 PwScore。JitPack 坐标改为 `com.github.venshine:PwScore:3.0.0`。
* Java API 改名：`com.wx.pwd.CheckStrength.checkPasswordStrength` 改为 `io.github.venshine.pwscore.PwScore.score`，`getPasswordLevel` 改为 `level`，枚举 `CheckStrength.LEVEL` 改为 `PwScore.Level`（取值不变）。
* 删除 `com.wx.pwd.StringUtils`。其中 `sizeOfInt`、`isNumeric`、`equalsNull` 在库内没有任何引用。

### 评分变更
* 修复「全部相同」的判断：原实现 `replace(首字符, ' ').trim()` 会把所有 ≤ U+0020 的字符（空格、Tab 等）当成与首字符相同，导致 `x x x x`、`x` 加 7 个空格这类密码直接判 0 分，「前后两半各自相同」也会误判。现在严格比较每个码元，例如 `x       ` 由 0 分变为 5 分。
* 字典与序列匹配前的小写转换只转换 A-Z，不再使用 `toLowerCase(Locale.ROOT)`。只有开尔文符号 `K`（U+212A）、`İ`（U+0130）这类小写后会变成 ASCII 字母的字符，评分可能与 2.1.0 不同。这样各语言实现不依赖各自的 Unicode 小写表。

### 新增
* 新增 Kotlin（Multiplatform）、Swift、Objective-C、Dart、JavaScript、Python、Go、Rust、C、C++、C#、Ruby、Perl 实现，各语言用法见 README。
* 新增规则说明 `spec/SPEC.md`；评分快照移至 `spec/score-snapshot.tsv`，所有语言用同一份快照测试，由 `java/test.sh --generate` 生成。
* 新增在线 demo（`web/`），在浏览器本地评分，通过 GitHub Pages 发布。

### 其他
* 删除 Swing 示例 `sample/` 和截图。

## 2.1.0

### 评分变更
不是整串重复的密码，除下文日期一条提到的全角、阿拉伯-印度数字日期外，分值只会持平或降低。实测（字符取自大小写字母、数字和 `!@#$%&*-_.`，均匀随机）：4-8 位约 40% 分值降低、约 15% 等级降低；9-16 位约 0.04% 分值降低。整串重复的密码改按重复单元评分，绝大多数降低；重复单元本身已有 16 位以上时，由于不再扣「两半相同」的 1 分，可能比原来高 1 分（实测这类密码约 8% 升高）。
* 组合加分的长度门槛约束整组条件。原来写成 `len > k && A || B || ...`，门槛只约束第一组，导致大写与小写待遇不对称（`KQM482` 原为 3 分、`kqm482` 为 2 分），5-6 位密码也能评到 MEDIUM（`Ab1!x` 由 4 MEDIUM 降为 3 EASY）。
* 由更短片段重复组成的密码按重复单元评分。原来只识别对半和三等分重复，`Ab1!` 重复 5 次可得 13 分 EXTREMELY_STRONG；现在 `Ab1!Ab1!Ab1!` 与 `Ab1!` 同分，`Passw0rd!Passw0rd!` 由 11 分降为 7 分。
* 序列检测改为检查密码中的连续片段：长度不少于 4 的连续字母、连续数字、键盘行，正反向均算，不区分大小写，每类扣 1 分。原来只在整个密码都是序列时才扣分；例如 `Qwerty123!` 由 8 分降为 7 分。
* 日期检测改为识别密码中任意位置的 8 位 `yyyyMMdd`（年份 1950-2049，按实际月份天数和闰年校验），例如 `Tom19881010!` 由 7 分降为 6 分。原来的规则只作用于 6 位和 8 位纯数字，而这类密码在扣分前已是 0 分，所以对 ASCII 数字从未生效；去掉后，整串由全角或阿拉伯-印度数字组成的 6 位、8 位日期不再被这条规则扣分。

### API 行为
* 只有 `null` 和空串抛 `IllegalArgumentException`，异常信息分别为 `password is null` 和 `password is empty`。原来纯空白串和字面量 `"null"`（不区分大小写）也会抛异常，现在正常评分。

### 其他
* 字符分类改为一次遍历统计，重复检测用 KMP 前缀函数，整体为线性时间：常规 16 位密码单次评分快约 1.3-1.7 倍（本机两次独立实测），100 万字符的输入在 100 ms 以内。
* 新增 Swing 示例 `sample/`（`./gradlew :sample:run`），以及覆盖各条规则的评分快照测试 `test/score-snapshot.tsv`。

## 2.0.0

### 不兼容变更
* 枚举 `CheckStrength.LEVEL.MIDIUM` 更名为 `LEVEL.MEDIUM`（修正拼写），调用方需同步修改。
* 以下修复会改变部分密码的评分，例如 `Password123!` 由 8 分降为 7 分，`iloveyou520` 由 MEDIUM 降为 EASY。

### 修复
* 连续字母、键盘序列、连续数字出现在密码开头时未扣分（`indexOf > 0` 应为 `>= 0`）。
* 字典检测写反：原来判断「密码是否为字典词的一部分」，现在同时判断「密码是否包含字典词」，且不区分大小写。
* 13 位及以上的纯数字密码会抛出 `NumberFormatException`；日期检测只识别 `yyyyMMdd` 与 `yyMMdd` 两种格式。

### 其他
* 新增 Gradle 构建，通过 JitPack 发布：`com.github.venshine:CheckPasswordStrength:2.0.0`。
* 新增无依赖的回归测试 `test.sh`。
