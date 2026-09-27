import Foundation
import Testing

@testable import WayfareCore

// Same fixtures as tests/unit/trip-cover-stamp.mjs and Android's StampTest.kt;
// a trip's stamp has to look the same on every client.
@Test func stampStyleIsDerivedFromTheTripId() {
  #expect(
    StampStyle(tripId: "0f8e2a4c-1b3d-4e5f-8a9b-0c1d2e3f4a5b")
      == StampStyle(tint: .lilac, tilt: 2.5, arched: false))
  #expect(
    StampStyle(tripId: "7c9e6679-7425-40de-944b-e07fc1f90ae7")
      == StampStyle(tint: .peach, tilt: -1.5, arched: true))
  #expect(
    StampStyle(tripId: "a3bb189e-8bf9-3888-9912-ace4e6543002")
      == StampStyle(tint: .sage, tilt: -1.5, arched: false))
  #expect(
    StampStyle(tripId: "e1f2d3c4-b5a6-4789-9abc-def012345678")
      == StampStyle(tint: .peach, tilt: 2.5, arched: false))
}

@Test func stampMonthIsTheStartMonth() {
  #expect(stampMonth("2026-09-03") == "SEP 2026")
  #expect(stampMonth(nil) == nil)
  #expect(stampMonth("soon") == nil)
}

@Test func normalisedPathDataParses() {
  #expect(
    parseStampPath("M12 52 L12 46 C12 44.34 13.34 43 15 43 Q16 40 18 46 Z") == [
      .move(12, 52), .line(12, 46), .cubic(12, 44.34, 13.34, 43, 15, 43), .quad(16, 40, 18, 46),
      .close,
    ])
}

@Test func anythingButNormalisedPathDataIsRefused() {
  #expect(parseStampPath("M4 52 H60") == nil)  // shorthand the server never writes
  #expect(parseStampPath("m4 52 l56 0") == nil)  // relative
  #expect(parseStampPath("M4 52 L70 52") == nil)  // off the grid
  #expect(parseStampPath("L4 52 L60 52") == nil)  // no moveto
  #expect(parseStampPath("M4 52 L60") == nil)  // short a number
  #expect(parseStampPath("M4 52 L60 52 9") == nil)  // stray number
}

@Test func storedArtDecodesLeniently() throws {
  let json = #"{"v":1,"label":"LISBOA","paths":["M4 52 L60 52"],"fallback":false,"extra":1}"#
  let art = try JSONDecoder().decode(StampArt.self, from: Data(json.utf8))
  #expect(art == StampArt(label: "LISBOA", paths: ["M4 52 L60 52"]))
}
