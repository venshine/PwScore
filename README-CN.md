PwScore
==

[English](README.md) | 简体中文

[![CI](https://github.com/venshine/PwScore/actions/workflows/ci.yml/badge.svg)](https://github.com/venshine/PwScore/actions/workflows/ci.yml) [![JitPack](https://jitpack.io/v/venshine/PwScore.svg)](https://jitpack.io/#venshine/PwScore)

PwScore 给密码强度打分（0 到 14 分），并换算成五个等级。没有任何依赖，同一个密码在 14 种语言里得分完全相同：Java、Kotlin、Swift、Objective-C、Dart、JavaScript、Python、Go、Rust、C、C++、C#、Ruby 和 Perl。

**[在浏览器里试一试](https://venshine.github.io/PwScore/)**。在线 demo 在本地运行 JavaScript 版，输入的内容不会发送到任何地方。

项目原名 CheckPasswordStrength，升级说明见[从 2.x 升级](#从-2x-升级)。

评分规则
--
混用多种字符、增加长度会加分；`abcd`、`4321`、`qwer` 这类连续序列，`19881010` 这类日期，`password` 这类常见词，以及过短的密码会减分。由更短片段重复组成的密码（如 `Ab1!Ab1!Ab1!`）按那个片段评分。长度按 UTF-16 码元计，一个 emoji 算 2。

| 分值 | 等级 |
|---|---|
| 0-3 | EASY |
| 4-6 | MEDIUM |
| 7-9 | STRONG |
| 10-12 | VERY_STRONG |
| 13-14 | EXTREMELY_STRONG |

完整规则见 [spec/SPEC.md](spec/SPEC.md)。每种语言的实现都用同一份[评分快照](spec/score-snapshot.tsv)测试。

每种语言都提供 `score` 和 `level` 两个调用，命名遵循各语言习惯。空密码视为错误，按各语言惯例抛异常、返回错误值或负的错误码，见下文各节。

使用
--
目前还没有发布到各语言的包仓库，可以按下文从本仓库安装，或者把源文件拷进项目。

[Java](#java) · [Kotlin](#kotlin) · [Swift](#swift) · [Objective-C](#objective-c) · [Dart](#dart) · [JavaScript](#javascript) · [Python](#python) · [Go](#go) · [Rust](#rust) · [C](#c) · [C++](#c-1) · [C#](#c-2) · [Ruby](#ruby) · [Perl](#perl)

### Java

Java 8 及以上。在 Gradle 中添加 [JitPack](https://jitpack.io/#venshine/PwScore)：

```groovy
repositories {
    maven { url = 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.venshine:PwScore:3.0.0'
}
```

```java
import io.github.venshine.pwscore.PwScore;

int score = PwScore.score("2hAj5#mne-ix.86H");           // 13
PwScore.Level level = PwScore.level("2hAj5#mne-ix.86H"); // EXTREMELY_STRONG
// null or "" throws IllegalArgumentException
```

### Kotlin

Kotlin Multiplatform 实现，支持 JVM、Android、iOS、macOS、JS、Linux 和 Windows。把 [`kotlin/src/commonMain/kotlin/io/github/venshine/pwscore/PwScore.kt`](kotlin/src/commonMain/kotlin/io/github/venshine/pwscore/PwScore.kt) 拷进 common 源码集即可。只用 JVM 或 Android 的项目，也可以直接依赖上面的 Java 包。

```kotlin
import io.github.venshine.pwscore.Level
import io.github.venshine.pwscore.PwScore

val score = PwScore.score("2hAj5#mne-ix.86H") // 13
val level = PwScore.level("2hAj5#mne-ix.86H") // Level.EXTREMELY_STRONG
// "" throws IllegalArgumentException
```

### Swift

Swift Package Manager：

```swift
.package(url: "https://github.com/venshine/PwScore.git", from: "3.0.0")
// target dependency: .product(name: "PwScore", package: "PwScore")
```

```swift
import PwScore

let score = try PwScore.score("2hAj5#mne-ix.86H") // 13
let level = try PwScore.level("2hAj5#mne-ix.86H") // .extremelyStrong
// "" throws PwScoreError.emptyPassword
```

### Objective-C

Swift Package Manager，包地址与 Swift 相同，产品名 `PWScoreObjC`；也可以拷贝 [`objc/Sources/PWScore/`](objc/Sources/PWScore) 下的两个文件。

```objc
@import PWScoreObjC;

NSError *error = nil;
NSInteger score = [PWScore scoreForPassword:@"2hAj5#mne-ix.86H" error:&error];   // 13
PWScoreLevel level = [PWScore levelForPassword:@"2hAj5#mne-ix.86H" error:&error]; // PWScoreLevelExtremelyStrong
// @"" returns -1 and sets error (PWScoreErrorEmptyPassword)
```

### Dart

Dart 3 或 Flutter。在 `pubspec.yaml` 中：

```yaml
dependencies:
  pwscore:
    git:
      url: https://github.com/venshine/PwScore.git
      path: dart
      ref: 3.0.0
```

```dart
import 'package:pwscore/pwscore.dart' as pwscore;

final score = pwscore.score('2hAj5#mne-ix.86H'); // 13
final level = pwscore.level('2hAj5#mne-ix.86H'); // Level.extremelyStrong
// '' throws ArgumentError
```

### JavaScript

ES 模块，支持浏览器和 Node.js 18 及以上，带 TypeScript 类型。拷贝 [`js/pwscore.js`](js/pwscore.js) 和 [`js/pwscore.d.ts`](js/pwscore.d.ts)。

```js
import { score, level } from './pwscore.js';

score('2hAj5#mne-ix.86H'); // 13
level('2hAj5#mne-ix.86H'); // 'EXTREMELY_STRONG'
// '' throws RangeError; a non-string throws TypeError
```

### Python

Python 3.8 及以上：

```shell
pip install "pwscore @ git+https://github.com/venshine/PwScore.git@3.0.0#subdirectory=python"
```

```python
import pwscore

pwscore.score("2hAj5#mne-ix.86H")  # 13
pwscore.level("2hAj5#mne-ix.86H")  # Level.EXTREMELY_STRONG
# "" raises ValueError; None raises TypeError
```

### Go

Go 1.18 及以上：

```shell
go get github.com/venshine/PwScore/go/v3@v3.0.0
```

```go
import "github.com/venshine/PwScore/go/v3"

score, err := pwscore.Score("2hAj5#mne-ix.86H")   // 13
level, err := pwscore.LevelOf("2hAj5#mne-ix.86H") // pwscore.ExtremelyStrong
// "" returns pwscore.ErrEmpty; invalid UTF-8 bytes count as U+FFFD
```

### Rust

在 `Cargo.toml` 中：

```toml
[dependencies]
pwscore = { git = "https://github.com/venshine/PwScore", tag = "3.0.0" }
```

```rust
let score = pwscore::score("2hAj5#mne-ix.86H")?; // 13
let level = pwscore::level("2hAj5#mne-ix.86H")?; // Level::ExtremelyStrong
// "" returns Err(pwscore::EmptyPassword)
```

### C

C99。把 [`c/pwscore.h`](c/pwscore.h) 和 [`c/pwscore.c`](c/pwscore.c) 加入编译，或者用 CMake：

```cmake
include(FetchContent)
FetchContent_Declare(pwscore GIT_REPOSITORY https://github.com/venshine/PwScore.git GIT_TAG 3.0.0)
FetchContent_MakeAvailable(pwscore)
target_link_libraries(app PRIVATE pwscore)
```

```c
#include "pwscore.h"

int score = pwscore_score("2hAj5#mne-ix.86H"); /* 13 */
int level = pwscore_level("2hAj5#mne-ix.86H"); /* PWSCORE_EXTREMELY_STRONG */
/* Input is UTF-8. NULL returns PWSCORE_ERR_NULL, "" returns PWSCORE_ERR_EMPTY */
```

### C++

C++17，是 C 库外面的一层 header-only 包装。用上面的 CMake 配置时链接 `pwscore::cpp`；不用 CMake 时，在 C 的两个文件之外再加上 [`cpp/pwscore.hpp`](cpp/pwscore.hpp)。

```cpp
#include "pwscore.hpp"

int score = pwscore::score("2hAj5#mne-ix.86H");             // 13
pwscore::Level level = pwscore::level("2hAj5#mne-ix.86H");  // pwscore::Level::ExtremelyStrong
// "" throws std::invalid_argument
```

### C#

.NET Standard 2.0（.NET Framework 4.6.1+、.NET Core 2.0+、.NET 5+、Unity）。拷贝 [`csharp/PwScore/PwScore.cs`](csharp/PwScore/PwScore.cs)，或者引用 [`csharp/PwScore/PwScore.csproj`](csharp/PwScore/PwScore.csproj)。

```csharp
using Venshine;

int score = PwScore.Score("2hAj5#mne-ix.86H");     // 13
PwLevel level = PwScore.Level("2hAj5#mne-ix.86H"); // PwLevel.ExtremelyStrong
// null throws ArgumentNullException; "" throws ArgumentException
```

### Ruby

Ruby 2.6 及以上。在 `Gemfile` 中：

```ruby
gem 'pwscore', git: 'https://github.com/venshine/PwScore.git', tag: '3.0.0', glob: 'ruby/*.gemspec'
```

```ruby
require 'pwscore'

PwScore.score('2hAj5#mne-ix.86H') # 13
PwScore.level('2hAj5#mne-ix.86H') # :extremely_strong
# nil or '' raises ArgumentError
```

### Perl

Perl 5.10 及以上。把 [`perl/lib/PwScore.pm`](perl/lib/PwScore.pm) 拷进库路径。参数要传解码后的字符串，不要传 UTF-8 字节串。

```perl
use PwScore qw(score level);

score('2hAj5#mne-ix.86H'); # 13
level('2hAj5#mne-ix.86H'); # 'EXTREMELY_STRONG'
# undef or '' croaks
```

从 2.x 升级
--
3.0.0 起项目由 CheckPasswordStrength 更名为 PwScore：

| 2.x | 3.0.0 |
|---|---|
| `com.github.venshine:CheckPasswordStrength` | `com.github.venshine:PwScore` |
| `com.wx.pwd.CheckStrength.checkPasswordStrength(s)` | `io.github.venshine.pwscore.PwScore.score(s)` |
| `CheckStrength.getPasswordLevel(s)` | `PwScore.level(s)` |
| `CheckStrength.LEVEL` | `PwScore.Level` |
| `com.wx.pwd.StringUtils` | 已删除 |

两处评分修正会让少数密码的分值变化，详见 [CHANGELOG](CHANGELOG.md)。

开发
--
`./test.sh` 运行本机已装工具链的所有语言的测试，`./test.sh go rust` 只运行指定语言。修改规则时按 [spec/SPEC.md](spec/SPEC.md) 末尾的步骤操作。

License
--
    Copyright (C) 2014 venshine.cn@gmail.com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
