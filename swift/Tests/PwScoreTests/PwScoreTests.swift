// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时
import Foundation
import XCTest

import PwScore

final class PwScoreTests: XCTestCase {
    func testSnapshot() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("spec/score-snapshot.tsv")
        let text = try String(contentsOf: url, encoding: .utf8)
        var checked = 0
        for line in text.components(separatedBy: "\n") where !line.isEmpty && !line.hasPrefix("#") {
            let cols = line.split(separator: "\t", maxSplits: 2, omittingEmptySubsequences: false).map(String.init)
            let actual = "\(try PwScore.score(cols[2]))\t\(try PwScore.level(cols[2]).rawValue)"
            XCTAssertEqual(actual, "\(cols[0])\t\(cols[1])", "[\(cols[2])]")
            checked += 1
        }
        XCTAssertGreaterThan(checked, 0)
    }

    func testErrors() throws {
        XCTAssertThrowsError(try PwScore.score("")) { XCTAssertEqual($0 as? PwScoreError, .emptyPassword) }
        XCTAssertEqual(try PwScore.level("2hAj5#mne-ix.86H"), .extremelyStrong)
    }

    func testLinearTime() throws {
        let input = String(repeating: "a", count: 200_000) + "b"
        let start = Date()
        _ = try PwScore.score(input)
        XCTAssertLessThan(Date().timeIntervalSince(start), 2)
    }
}
