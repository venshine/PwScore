// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错
#import <XCTest/XCTest.h>

#import "PWScore.h"

@interface PWScoreTests : XCTestCase
@end

@implementation PWScoreTests

- (void)testSnapshot {
    // 本文件位于 objc/Tests/PWScoreTests/，向上四级是仓库根目录
    NSString *root = [[[[@(__FILE__) stringByDeletingLastPathComponent] stringByDeletingLastPathComponent]
        stringByDeletingLastPathComponent] stringByDeletingLastPathComponent];
    NSString *path = [root stringByAppendingPathComponent:@"spec/score-snapshot.tsv"];
    NSString *text = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    XCTAssertNotNil(text);
    NSUInteger checked = 0;
    for (NSString *line in [text componentsSeparatedByString:@"\n"]) {
        if (line.length == 0 || [line hasPrefix:@"#"]) continue;
        NSRange first = [line rangeOfString:@"\t"];
        NSRange second = [line rangeOfString:@"\t" options:0 range:NSMakeRange(NSMaxRange(first), line.length - NSMaxRange(first))];
        NSString *password = [line substringFromIndex:NSMaxRange(second)];
        NSString *actual = [NSString stringWithFormat:@"%ld\t%@", (long)[PWScore scoreForPassword:password error:nil],
                                                      [PWScore nameForLevel:[PWScore levelForPassword:password error:nil]]];
        XCTAssertEqualObjects(actual, [line substringToIndex:second.location], @"[%@]", password);
        checked++;
    }
    XCTAssertGreaterThan(checked, 0u);
}

- (void)testErrors {
    NSError *error = nil;
    XCTAssertEqual([PWScore scoreForPassword:@"" error:&error], -1);
    XCTAssertEqual(error.code, PWScoreErrorEmptyPassword);
    XCTAssertEqual([PWScore levelForPassword:@"2hAj5#mne-ix.86H" error:nil], PWScoreLevelExtremelyStrong);
}

- (void)testLinearTime {
    NSString *input = [[@"" stringByPaddingToLength:200000 withString:@"a" startingAtIndex:0] stringByAppendingString:@"b"];
    NSDate *start = [NSDate date];
    [PWScore scoreForPassword:input error:nil];
    XCTAssertLessThan(-[start timeIntervalSinceNow], 2.0);
}

@end
