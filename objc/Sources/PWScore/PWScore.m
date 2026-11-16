// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。
//
// Licensed under the Apache License, Version 2.0
#import "PWScore.h"

NSErrorDomain const PWScoreErrorDomain = @"io.github.venshine.pwscore";

enum { NUM = 0, SMALL = 1, CAPITAL = 2, INTL = 3, OTHER = 4 };

static const NSUInteger kSequenceMin = 4;
static const NSUInteger kMaxBlock = 16;

static const int kDaysInMonth[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

static int CharType(unichar c) {
    if (c >= '0' && c <= '9') return NUM;
    if (c >= 'a' && c <= 'z') return SMALL;
    if (c >= 'A' && c <= 'Z') return CAPITAL;
    if (c >= 128) return INTL;
    return OTHER;
}

static BOOL IsPair(const unichar *s, NSUInteger i, NSUInteger end) {
    return i + 1 < end && CFStringIsSurrogateHighCharacter(s[i]) && CFStringIsSurrogateLowCharacter(s[i + 1]);
}

static BOOL RegionEquals(const unichar *s, NSUInteger a, NSUInteger b, NSUInteger count) {
    for (NSUInteger k = 0; k < count; k++) {
        if (s[a + k] != s[b + k]) return NO;
    }
    return YES;
}

static NSUInteger CharacterCount(const unichar *s, NSUInteger from, NSUInteger to) {
    NSUInteger count = 0;
    for (NSUInteger i = from; i < to; i += IsPair(s, i, to) ? 2 : 1) count++;
    return count;
}

/// 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
static NSUInteger EffectiveLength(const unichar *s, NSUInteger n) {
    NSUInteger i = 0, len = 0;
    while (i < n) {
        NSUInteger block = 0;
        for (NSUInteger m = 1; m <= kMaxBlock && i + 2 * m <= n; m++) {
            if (s[i] == s[i + m] && RegionEquals(s, i, i + m, m)) {
                block = m;
                break;
            }
        }
        if (block > 0) {
            NSUInteger r = 2;
            while (i + (r + 1) * block <= n && RegionEquals(s, i, i + r * block, block)) r++;
            len += block == 1 ? 2 : CharacterCount(s, i, i + block);
            i += r * block;
        } else {
            i += IsPair(s, i, n) ? 2 : 1;
            len++;
        }
    }
    return len;
}

static NSInteger LengthPoints(NSUInteger len) {
    if (len <= 4) return -2;
    if (len == 5) return -1;
    if (len < 8) return 0;
    return MIN((NSInteger)((len - 8) / 4 + 1), 5);
}

static BOOL IsSameChar(const unichar *s, NSUInteger from, NSUInteger to) {
    for (NSUInteger i = from + 1; i < to; i++) {
        if (s[i] != s[from]) return NO;
    }
    return YES;
}

static int Digits(const unichar *s, NSUInteger start, NSUInteger count) {
    int value = 0;
    for (NSUInteger i = start; i < start + count; i++) value = value * 10 + (s[i] - '0');
    return value;
}

static BOOL IsYear(int year) { return year >= 1950 && year <= 2049; }

static BOOL IsValidDate(int year, int month, int day) {
    if (month < 1 || month > 12 || day < 1) return NO;
    BOOL leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    return day <= (month == 2 && leap ? 29 : kDaysInMonth[month - 1]);
}

static BOOL IsDate8(const unichar *s, NSUInteger k) {
    int head = Digits(s, k, 4);
    if (IsYear(head) && IsValidDate(head, Digits(s, k + 4, 2), Digits(s, k + 6, 2))) return YES;
    int tail = Digits(s, k + 4, 4);
    return IsYear(tail) && (IsValidDate(tail, Digits(s, k + 2, 2), Digits(s, k, 2)) ||
                            IsValidDate(tail, Digits(s, k, 2), Digits(s, k + 2, 2)));
}

/// 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
static BOOL ContainsDateOrYear(const unichar *s, NSUInteger n) {
    NSUInteger i = 0;
    while (i < n) {
        if (CharType(s[i]) != NUM) {
            i++;
            continue;
        }
        NSUInteger start = i;
        while (i < n && CharType(s[i]) == NUM) i++;
        NSUInteger run = i - start;
        for (NSUInteger k = start; k + 8 <= i; k++) {
            if (IsDate8(s, k)) return YES;
        }
        if (run == 6 && IsValidDate(2000 + Digits(s, start, 2), Digits(s, start + 2, 2), Digits(s, start + 4, 2))) {
            return YES;
        }
        if (run == 4 && IsYear(Digits(s, start, 4))) return YES;
    }
    return NO;
}

@implementation PWScore

+ (NSArray<NSString *> *)dictionary {
    static NSArray<NSString *> *words;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        words = [@"password passwd admin iloveyou sunshine adobe123 abc123 123123 1314520 a1b2c3 123qwe aaa111 qweasd "
                 @"welcome letmein login master monkey dragon shadow princess football baseball soccer superman batman "
                 @"starwars pokemon freedom whatever trustno1 secret hello summer winter spring autumn flower angel "
                 @"lovely loveyou killer hunter cookie banana orange purple silver golden diamond chocolate computer "
                 @"internet google apple changeme default guest "
                 @"woaini iloveu zhang wang yang huang zhao xiaoming laopo laogong baobao nihao wodemima mima qq123 "
                 @"5201314 520520 147258 159753 147852 789456 741852 963852 258369 123321 112233"
            componentsSeparatedByString:@" "];
    });
    return words;
}

/// 每组序列（含反向）所有长度为 4 的片段；每组命中扣 1 分
+ (NSArray<NSSet<NSString *> *> *)sequenceKeys {
    static NSArray<NSSet<NSString *> *> *keys;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        NSArray<NSArray<NSString *> *> *sequences = @[
            @[ @"abcdefghijklmnopqrstuvwxyz" ],
            @[ @"01234567890" ],
            @[
                @"qwertyuiop", @"asdfghjkl", @"zxcvbnm", @"1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/",
                @"qazwsxedcrfvtgbyhnujmikolp", @"1q2w3e4r5t6y7u8i9o0p", @"q1w2e3r4t5y6u7i8o9p0",
                @"zaq1xsw2cde3vfr4bgt5nhy6mju7", @"!@#$%^&*()_+"
            ],
        ];
        NSMutableArray *groups = [NSMutableArray array];
        for (NSArray<NSString *> *group in sequences) {
            NSMutableSet *set = [NSMutableSet set];
            for (NSString *base in group) {
                NSMutableString *reversed = [NSMutableString stringWithCapacity:base.length];
                for (NSInteger i = (NSInteger)base.length - 1; i >= 0; i--) {
                    [reversed appendFormat:@"%C", [base characterAtIndex:(NSUInteger)i]];
                }
                for (NSString *seq in @[ base, reversed ]) {
                    for (NSUInteger i = 0; i + kSequenceMin <= seq.length; i++) {
                        [set addObject:[seq substringWithRange:NSMakeRange(i, kSequenceMin)]];
                    }
                }
            }
            [groups addObject:[set copy]];
        }
        keys = [groups copy];
    });
    return keys;
}

+ (NSInteger)scoreForPassword:(NSString *)password error:(NSError **)error {
    if (password.length == 0) {
        if (error) {
            *error = [NSError errorWithDomain:PWScoreErrorDomain
                                         code:PWScoreErrorEmptyPassword
                                     userInfo:@{NSLocalizedDescriptionKey : @"password is empty"}];
        }
        return -1;
    }
    NSUInteger n = password.length;
    unichar *s = malloc(n * sizeof(unichar));
    unichar *lower = malloc(n * sizeof(unichar));
    unichar *leet = malloc(n * sizeof(unichar));
    BOOL *covered = calloc(n, sizeof(BOOL));
    uint8_t *seen = calloc(65536 / 8, 1);
    [password getCharacters:s range:NSMakeRange(0, n)];
    // 全角 ASCII 还原为半角
    for (NSUInteger i = 0; i < n; i++) {
        if (s[i] >= 0xFF01 && s[i] <= 0xFF5E) s[i] -= 0xFEE0;
    }
    NSUInteger len = EffectiveLength(s, n);

    // 各类统计「不同字符」的个数
    NSUInteger count[5] = {0, 0, 0, 0, 0}, distinct = 0;
    for (NSUInteger i = 0; i < n; i++) {
        if (!(seen[s[i] >> 3] & (1u << (s[i] & 7)))) {
            seen[s[i] >> 3] |= (uint8_t)(1u << (s[i] & 7));
            count[CharType(s[i])]++;
            distinct++;
        }
    }
    NSUInteger num = count[NUM], small = count[SMALL], capital = count[CAPITAL], intl = count[INTL],
               other = count[OTHER];
    int classes = (num > 0) + (small > 0) + (capital > 0) + (intl > 0) + (other > 0);

    NSInteger level = 0;
    // 加分
    level += num > 0;
    level += small > 0;
    level += intl > 0;
    level += len > 4 && capital > 0;
    level += len > 6 && other > 0;
    level += len > 4 && classes >= 2;
    level += len > 6 && classes >= 3;
    level += len > 8 && classes >= 4;
    level += len > 6 && ((num >= 3 && small >= 3) || (num >= 3 && capital >= 3) || (num >= 3 && other >= 2) ||
                         (small >= 3 && capital >= 3) || (small >= 3 && other >= 2) || (capital >= 3 && other >= 2));
    level += len > 8 && ((num >= 2 && small >= 2 && capital >= 2) || (num >= 2 && small >= 2 && other >= 2) ||
                         (num >= 2 && capital >= 2 && other >= 2) || (small >= 2 && capital >= 2 && other >= 2));
    level += len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2;
    level += other >= 3;
    level += other >= 6;
    level += LengthPoints(len);

    // 减分；只把 A-Z 转小写
    static const char leetFrom[] = "@401!3$57", leetTo[] = "aaoiiesst";
    for (NSUInteger i = 0; i < n; i++) {
        lower[i] = s[i] >= 'A' && s[i] <= 'Z' ? s[i] + 32 : s[i];
        const char *p = lower[i] > 0 && lower[i] < 128 ? strchr(leetFrom, (int)lower[i]) : NULL;
        leet[i] = p != NULL ? (unichar)leetTo[p - leetFrom] : lower[i];
    }
    NSString *lowerText = [[NSString alloc] initWithCharacters:lower length:n];
    NSString *leetText = [[NSString alloc] initWithCharacters:leet length:n];
    for (NSSet<NSString *> *keys in [self sequenceKeys]) {
        BOOL hit = NO;
        for (NSUInteger i = 0; i + kSequenceMin <= n; i++) {
            if ([keys containsObject:[lowerText substringWithRange:NSMakeRange(i, kSequenceMin)]]) {
                hit = YES;
                for (NSUInteger j = i; j < i + kSequenceMin; j++) covered[j] = YES;
            }
        }
        if (hit) level--;
    }
    NSUInteger coveredCount = 0;
    for (NSUInteger i = 0; i < n; i++) coveredCount += covered[i];
    if (2 * coveredCount >= n) level--;
    if (len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)) level--;
    if (n % 2 == 0 && IsSameChar(s, 0, n / 2) && IsSameChar(s, n / 2, n)) level--;
    if (ContainsDateOrYear(s, n)) level--;
    if (len >= 8 && distinct <= MAX((NSUInteger)4, len / 3)) level--;
    for (NSString *word in [self dictionary]) {
        // 按码元字面比较，不做 Unicode 归一化
        if ([lowerText rangeOfString:word options:NSLiteralSearch].location != NSNotFound ||
            [word rangeOfString:lowerText options:NSLiteralSearch].location != NSNotFound ||
            [leetText rangeOfString:word options:NSLiteralSearch].location != NSNotFound) {
            level -= 2;
            break;
        }
    }
    if (len <= 3 || IsSameChar(s, 0, n)) level = 0;
    free(s);
    free(lower);
    free(leet);
    free(covered);
    free(seen);
    return MIN(MAX(level, 0), 14);
}

+ (PWScoreLevel)levelForPassword:(NSString *)password error:(NSError **)error {
    NSInteger score = [self scoreForPassword:password error:error];
    if (score < 0) return -1;
    if (score <= 3) return PWScoreLevelEasy;
    if (score <= 6) return PWScoreLevelMedium;
    if (score <= 9) return PWScoreLevelStrong;
    if (score <= 12) return PWScoreLevelVeryStrong;
    return PWScoreLevelExtremelyStrong;
}

+ (NSString *)nameForLevel:(PWScoreLevel)level {
    switch (level) {
        case PWScoreLevelEasy: return @"EASY";
        case PWScoreLevelMedium: return @"MEDIUM";
        case PWScoreLevelStrong: return @"STRONG";
        case PWScoreLevelVeryStrong: return @"VERY_STRONG";
        case PWScoreLevelExtremelyStrong: return @"EXTREMELY_STRONG";
    }
    return @"";
}

@end
