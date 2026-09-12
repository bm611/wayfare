// swift-tools-version: 6.0

import PackageDescription

let package = Package(
  name: "WayfareCore",
  platforms: [.macOS(.v14), .iOS(.v17)],
  products: [
    .library(name: "WayfareCore", targets: ["WayfareCore"]),
    .library(name: "WayfareData", targets: ["WayfareData"]),
  ],
  dependencies: [
    .package(url: "https://github.com/swiftlang/swift-testing.git", from: "0.12.0")
  ],
  targets: [
    .target(name: "WayfareCore"),
    .target(name: "WayfareData", dependencies: ["WayfareCore"], path: "Wayfare/Data"),
    .testTarget(
      name: "WayfareCoreTests",
      dependencies: ["WayfareCore", .product(name: "Testing", package: "swift-testing")]
    ),
    .testTarget(
      name: "WayfareDataTests",
      dependencies: [
        "WayfareData", "WayfareCore", .product(name: "Testing", package: "swift-testing"),
      ]
    ),
  ]
)
