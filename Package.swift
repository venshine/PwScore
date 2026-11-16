// swift-tools-version:5.7
// Swift 与 Objective-C 实现。SPM 只认仓库根目录的 Package.swift，源码分别在 swift/ 和 objc/
import PackageDescription

let package = Package(
    name: "PwScore",
    products: [
        .library(name: "PwScore", targets: ["PwScore"]),
        .library(name: "PWScoreObjC", targets: ["PWScoreObjC"]),
    ],
    targets: [
        .target(name: "PwScore", path: "swift/Sources/PwScore"),
        .testTarget(name: "PwScoreTests", dependencies: ["PwScore"], path: "swift/Tests/PwScoreTests"),
        .target(name: "PWScoreObjC", path: "objc/Sources/PWScore"),
        .testTarget(name: "PWScoreObjCTests", dependencies: ["PWScoreObjC"], path: "objc/Tests/PWScoreTests"),
    ]
)
