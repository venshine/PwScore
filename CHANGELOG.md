## 3.0.0

### 项目更名与不兼容变更
* 项目由 CheckPasswordStrength 更名为 PwScore。JitPack 坐标改为 `com.github.venshine:PwScore:3.0.0`。
* Java API 改名：`com.wx.pwd.CheckStrength.checkPasswordStrength` 改为 `io.github.venshine.pwscore.PwScore.score`，`getPasswordLevel` 改为 `level`，枚举 `CheckStrength.LEVEL` 改为 `PwScore.Level`（取值不变）。
* 删除 `com.wx.pwd.StringUtils`。其中 `sizeOfInt`、`isNumeric`、`equalsNull` 在库内没有任何引用。

### 评分变更
规则整体重做，完整口径见 `spec/SPEC.md`。2.1.0 快照的 118 条用例中，77 条分值不变、28 条升高、13 条降低。
* 长度改为连续阶梯：≤4 位 −2，5 位 −1，6-7 位 0，8-11 位 +1，此后每 4 位 +1，24 位及以上 +5；替换原来的「≤4、≤6 位扣分」和「>12、≥16 位加分」。总分仍封顶 14。
* 有效长度：同一字符连写只计 2 位，紧邻重复的片段（长 2-16）只计一次，取代原来只识别整串周期的重复归约。原来多加一个字符就能绕过（`Ab1!Ab1!Ab1!x` 在 2.1.0 为 12 分，现为 3 分）。
* 各类字符的数量门槛改为统计不同字符的个数，同一个符号连写不再重复计分（`Pa1!` 加 11 个 `!` 在 2.1.0 为 10 分，现为 3 分）。
* 全角 ASCII 先还原为半角；其他非 ASCII 字符（中文、西里尔字母、emoji 等）单独成一类，出现时 +1，不再拿符号加分；有效长度里一个 emoji 计 1 位。
* 「全是同一类字符」只在不足 6 位时扣分；新增「不同字符太少」扣 1 分：8 位及以上，且不同字符不超过 max(4, 长度/3) 种。
* 字典从 13 个词扩充到 84 个（英文高频词、拼音、数字和小键盘组合）；同时识别 `@4→a`、`0→o`、`1!→i`、`3→e`、`$5→s`、`7→t` 替换写法；命中扣 2 分。
* 日期在 yyyyMMdd 之外，增加 ddMMyyyy、MMddyyyy、6 位 yyMMdd 和 4 位年份（1950-2049）。
* 键盘组增加竖列、斜列、之字形和 shift 符号行；序列覆盖密码一半及以上字符时再扣 1 分。
* 修复「全部相同」的判断：原实现 `replace(首字符, ' ').trim()` 会把 ≤ U+0020 的字符（空格、Tab 等）当成与首字符相同；现在严格比较每个码元。
* 小写转换只转换 A-Z，不再使用 `toLowerCase(Locale.ROOT)`，各语言实现不依赖各自的 Unicode 小写表。

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
