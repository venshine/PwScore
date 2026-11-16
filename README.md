PwScore
==

English | [简体中文](README-CN.md)

[![CI](https://github.com/venshine/PwScore/actions/workflows/ci.yml/badge.svg)](https://github.com/venshine/PwScore/actions/workflows/ci.yml) [![JitPack](https://jitpack.io/v/venshine/PwScore.svg)](https://jitpack.io/#venshine/PwScore)

PwScore scores how strong a password is, from 0 to 14, and maps the score to one of five levels. It has no dependencies and gives the same result for the same password in 14 languages: Java, Kotlin, Swift, Objective-C, Dart, JavaScript, Python, Go, Rust, C, C++, C#, Ruby and Perl.

**[Try it in your browser](https://venshine.github.io/PwScore/)**. The demo runs the JavaScript version locally; nothing you type is sent anywhere.

Formerly CheckPasswordStrength. See [Upgrading from 2.x](#upgrading-from-2x).

How it scores
--
Mixing character types and adding length raise the score. Runs and keyboard patterns (`abcd`, `4321`, `qwer`, `1qaz`), dates and years (`19881010`, `2024`), about 80 common words and pinyin even when written as `p@ssw0rd`, very short passwords, and passwords made of only a few different characters lower it. Repeated characters and repeated pieces count once, so `aaaa` or `Ab1!Ab1!Ab1!x` gain almost nothing from their length. Fullwidth letters and digits count as their ASCII forms; other non-ASCII characters, including emoji, form a class of their own.

| Score | Level |
|---|---|
| 0-3 | EASY |
| 4-6 | MEDIUM |
| 7-9 | STRONG |
| 10-12 | VERY_STRONG |
| 13-14 | EXTREMELY_STRONG |

The exact rules are in [spec/SPEC.md](spec/SPEC.md). Every implementation is tested against the same [snapshot of expected scores](spec/score-snapshot.tsv).

Each language exposes two calls, `score` and `level`, named in that language's style. An empty password is an error: an exception, an error value or a negative error code, as each section below shows.

Usage
--
Packages are not on the language registries yet. Install from this repository as shown, or copy the source file into your project.

[Java](#java) · [Kotlin](#kotlin) · [Swift](#swift) · [Objective-C](#objective-c) · [Dart](#dart) · [JavaScript](#javascript) · [Python](#python) · [Go](#go) · [Rust](#rust) · [C](#c) · [C++](#c-1) · [C#](#c-2) · [Ruby](#ruby) · [Perl](#perl)

### Java

Java 8 or later. Add [JitPack](https://jitpack.io/#venshine/PwScore) to Gradle:

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

A Kotlin Multiplatform implementation for JVM, Android, iOS, macOS, JS, Linux and Windows. Copy [`kotlin/src/commonMain/kotlin/io/github/venshine/pwscore/PwScore.kt`](kotlin/src/commonMain/kotlin/io/github/venshine/pwscore/PwScore.kt) into your common source set. On JVM or Android only, you can use the Java dependency above instead.

```kotlin
import io.github.venshine.pwscore.Level
import io.github.venshine.pwscore.PwScore

val score = PwScore.score("2hAj5#mne-ix.86H") // 13
val level = PwScore.level("2hAj5#mne-ix.86H") // Level.EXTREMELY_STRONG
// "" throws IllegalArgumentException
```

### Swift

Swift Package Manager:

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

Swift Package Manager, product `PWScoreObjC` (same package URL as Swift), or copy the two files in [`objc/Sources/PWScore/`](objc/Sources/PWScore).

```objc
@import PWScoreObjC;

NSError *error = nil;
NSInteger score = [PWScore scoreForPassword:@"2hAj5#mne-ix.86H" error:&error];   // 13
PWScoreLevel level = [PWScore levelForPassword:@"2hAj5#mne-ix.86H" error:&error]; // PWScoreLevelExtremelyStrong
// @"" returns -1 and sets error (PWScoreErrorEmptyPassword)
```

### Dart

Dart 3 or Flutter. In `pubspec.yaml`:

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

An ES module for browsers and Node.js 18 or later, with TypeScript types. Copy [`js/pwscore.js`](js/pwscore.js) and [`js/pwscore.d.ts`](js/pwscore.d.ts).

```js
import { score, level } from './pwscore.js';

score('2hAj5#mne-ix.86H'); // 13
level('2hAj5#mne-ix.86H'); // 'EXTREMELY_STRONG'
// '' throws RangeError; a non-string throws TypeError
```

### Python

Python 3.8 or later:

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

Go 1.18 or later:

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

In `Cargo.toml`:

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

C99. Add [`c/pwscore.h`](c/pwscore.h) and [`c/pwscore.c`](c/pwscore.c) to your build, or use CMake:

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

C++17, a header-only wrapper around the C library. With the CMake setup above, link `pwscore::cpp`; without CMake, also add [`cpp/pwscore.hpp`](cpp/pwscore.hpp).

```cpp
#include "pwscore.hpp"

int score = pwscore::score("2hAj5#mne-ix.86H");             // 13
pwscore::Level level = pwscore::level("2hAj5#mne-ix.86H");  // pwscore::Level::ExtremelyStrong
// "" throws std::invalid_argument
```

### C#

.NET Standard 2.0 (.NET Framework 4.6.1+, .NET Core 2.0+, .NET 5+, Unity). Copy [`csharp/PwScore/PwScore.cs`](csharp/PwScore/PwScore.cs) or reference [`csharp/PwScore/PwScore.csproj`](csharp/PwScore/PwScore.csproj).

```csharp
using Venshine;

int score = PwScore.Score("2hAj5#mne-ix.86H");     // 13
PwLevel level = PwScore.Level("2hAj5#mne-ix.86H"); // PwLevel.ExtremelyStrong
// null throws ArgumentNullException; "" throws ArgumentException
```

### Ruby

Ruby 2.6 or later. In your `Gemfile`:

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

Perl 5.10 or later. Copy [`perl/lib/PwScore.pm`](perl/lib/PwScore.pm) into your library path. Pass decoded strings, not UTF-8 bytes.

```perl
use PwScore qw(score level);

score('2hAj5#mne-ix.86H'); # 13
level('2hAj5#mne-ix.86H'); # 'EXTREMELY_STRONG'
# undef or '' croaks
```

Upgrading from 2.x
--
3.0.0 renames the project from CheckPasswordStrength to PwScore:

| 2.x | 3.0.0 |
|---|---|
| `com.github.venshine:CheckPasswordStrength` | `com.github.venshine:PwScore` |
| `com.wx.pwd.CheckStrength.checkPasswordStrength(s)` | `io.github.venshine.pwscore.PwScore.score(s)` |
| `CheckStrength.getPasswordLevel(s)` | `PwScore.level(s)` |
| `CheckStrength.LEVEL` | `PwScore.Level` |
| `com.wx.pwd.StringUtils` | removed |

3.0.0 also reworks the scoring rules, so many passwords score differently, and passwords that only looked strong (repeats, keyboard patterns, common words, years) score lower. See the [CHANGELOG](CHANGELOG.md).

Development
--
`./test.sh` runs the tests of every language whose toolchain is installed; `./test.sh go rust` runs only those. To change a rule, follow the steps at the end of [spec/SPEC.md](spec/SPEC.md).

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
