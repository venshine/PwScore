// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。
//
// Licensed under the Apache License, Version 2.0
#import "PWScore.h"

NSErrorDomain const PWScoreErrorDomain = @"io.github.venshine.pwscore";

enum { NUM = 0, SMALL = 1, CAPITAL = 2, OTHER = 3 };

static const NSUInteger kSequenceMin = 4;

static const int kDaysInMonth[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

static int CharType(unichar c) {
    if (c >= '0' && c <= '9') return NUM;
    if (c >= 'a' && c <= 'z') return SMALL;
    if (c >= 'A' && c <= 'Z') return CAPITAL;
    return OTHER;
}

static NSUInteger RepeatUnitLength(const unichar *s, NSUInteger n) {
    NSUInteger *prefix = calloc(n, sizeof(NSUInteger));
    for (NSUInteger i = 1; i < n; i++) {
        NSUInteger k = prefix[i - 1];
        while (k > 0 && s[i] != s[k]) k = prefix[k - 1];
        if (s[i] == s[k]) k++;
        prefix[i] = k;
    }
    NSUInteger period = n - prefix[n - 1];
    free(prefix);
    return n % period == 0 ? period : n;
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

static int DaysInMonth(int year, int month) {
    BOOL leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    return month == 2 && leap ? 29 : kDaysInMonth[month - 1];
}

static BOOL ContainsDate(const unichar *s, NSUInteger n) {
    NSUInteger run = 0;
    for (NSUInteger i = 0; i < n; i++) {
        run = CharType(s[i]) == NUM ? run + 1 : 0;
        if (run >= 8) {
            NSUInteger start = i - 7;
            int year = Digits(s, start, 4), month = Digits(s, start + 4, 2), day = Digits(s, start + 6, 2);
            if (year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1 &&
                day <= DaysInMonth(year, month)) {
                return YES;
            }
        }
    }
    return NO;
}

@implementation PWScore

+ (NSArray<NSString *> *)dictionary {
    static NSArray<NSString *> *words;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        words = @[ @"password", @"abc123", @"iloveyou", @"adobe123", @"123123", @"sunshine", @"1314520", @"a1b2c3",
                   @"123qwe", @"aaa111", @"qweasd", @"admin", @"passwd" ];
    });
    return words;
}

/// 每组序列所有长度为 4 的片段
+ (NSArray<NSSet<NSString *> *> *)sequenceKeys {
    static NSArray<NSSet<NSString *> *> *keys;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        NSArray<NSArray<NSString *> *> *sequences = @[
            @[ @"abcdefghijklmnopqrstuvwxyz", @"zyxwvutsrqponmlkjihgfedcba" ],
            @[ @"01234567890", @"09876543210" ],
            @[ @"qwertyuiop", @"poiuytrewq", @"asdfghjkl", @"lkjhgfdsa", @"zxcvbnm", @"mnbvcxz" ],
        ];
        NSMutableArray *groups = [NSMutableArray array];
        for (NSArray<NSString *> *group in sequences) {
            NSMutableSet *set = [NSMutableSet set];
            for (NSString *seq in group) {
                for (NSUInteger i = 0; i + kSequenceMin <= seq.length; i++) {
                    [set addObject:[seq substringWithRange:NSMakeRange(i, kSequenceMin)]];
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
    NSUInteger total = password.length;
    unichar *s = malloc(total * sizeof(unichar));
    [password getCharacters:s range:NSMakeRange(0, total)];

    // 整串由更短片段重复组成时，按重复单元评分
    NSUInteger len = RepeatUnitLength(s, total);

    NSUInteger count[4] = {0, 0, 0, 0};
    for (NSUInteger i = 0; i < len; i++) count[CharType(s[i])]++;
    NSUInteger num = count[NUM], small = count[SMALL], capital = count[CAPITAL], other = count[OTHER];
    int classes = (num > 0) + (small > 0) + (capital > 0) + (other > 0);

    NSInteger level = 0;
    // 加分
    level += num > 0;
    level += small > 0;
    level += len > 4 && capital > 0;
    level += len > 6 && other > 0;
    level += len > 4 && classes >= 2;
    level += len > 6 && classes >= 3;
    level += len > 8 && classes == 4;
    level += len > 6 && ((num >= 3 && small >= 3) || (num >= 3 && capital >= 3) || (num >= 3 && other >= 2) ||
                         (small >= 3 && capital >= 3) || (small >= 3 && other >= 2) || (capital >= 3 && other >= 2));
    level += len > 8 && ((num >= 2 && small >= 2 && capital >= 2) || (num >= 2 && small >= 2 && other >= 2) ||
                         (num >= 2 && capital >= 2 && other >= 2) || (small >= 2 && capital >= 2 && other >= 2));
    level += len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2;
    level += other >= 3;
    level += other >= 6;
    level += len > 12;
    level += len >= 16;

    // 减分：先算依赖原文的条件，再只把 A-Z 转小写
    BOOL halvesSame = len % 2 == 0 && IsSameChar(s, 0, len / 2) && IsSameChar(s, len / 2, len);
    BOOL allSame = IsSameChar(s, 0, len);
    BOOL hasDate = ContainsDate(s, len);
    for (NSUInteger i = 0; i < len; i++) {
        if (s[i] >= 'A' && s[i] <= 'Z') s[i] += 32;
    }
    NSString *lower = [[NSString alloc] initWithCharacters:s length:len];
    free(s);

    for (NSSet<NSString *> *keys in [self sequenceKeys]) {
        for (NSUInteger i = 0; i + kSequenceMin <= len; i++) {
            if ([keys containsObject:[lower substringWithRange:NSMakeRange(i, kSequenceMin)]]) {
                level--;
                break;
            }
        }
    }
    if (num == len || small == len || capital == len) level--;
    if (halvesSame) level--;
    if (hasDate) level--;
    for (NSString *word in [self dictionary]) {
        // 按码元字面比较，不做 Unicode 归一化
        if ([lower rangeOfString:word options:NSLiteralSearch].location != NSNotFound ||
            [word rangeOfString:lower options:NSLiteralSearch].location != NSNotFound) {
            level--;
            break;
        }
    }
    if (len <= 6) level--;
    if (len <= 4) level--;
    if (len <= 3 || allSame) level = 0;
    return MAX(level, 0);
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
