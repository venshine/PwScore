// PwScore：密码强度评分，规则见仓库 spec/SPEC.md。NSString 本身是 UTF-16，与规则口径一致。
//
// Licensed under the Apache License, Version 2.0
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// 强度等级，按分值从低到高
typedef NS_ENUM(NSInteger, PWScoreLevel) {
    PWScoreLevelEasy = 0,
    PWScoreLevelMedium,
    PWScoreLevelStrong,
    PWScoreLevelVeryStrong,
    PWScoreLevelExtremelyStrong,
};

/// 错误域与错误码
FOUNDATION_EXPORT NSErrorDomain const PWScoreErrorDomain;
typedef NS_ERROR_ENUM(PWScoreErrorDomain, PWScoreErrorCode) {
    PWScoreErrorEmptyPassword = 1,
};

@interface PWScore : NSObject

/// 计算密码强度分值，0 到 14；空串返回 -1 并设置 error
+ (NSInteger)scoreForPassword:(NSString *)password error:(NSError **)error;

/// 计算密码强度等级；空串返回 -1 并设置 error
+ (PWScoreLevel)levelForPassword:(NSString *)password error:(NSError **)error;

/// 与其他语言一致的等级名，如 @"VERY_STRONG"
+ (NSString *)nameForLevel:(PWScoreLevel)level;

- (instancetype)init NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
