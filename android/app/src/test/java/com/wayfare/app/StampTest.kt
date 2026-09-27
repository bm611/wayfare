package com.wayfare.app

import com.wayfare.app.core.StampArt
import com.wayfare.app.core.StampStyle
import com.wayfare.app.core.StampTint
import com.wayfare.app.core.stampMonth
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StampTest {
    // Same fixtures as tests/unit/trip-cover-stamp.mjs and the iOS StampTests;
    // a trip's stamp has to look the same on every client.
    @Test fun `stamp style is derived from the trip id`() {
        assertEquals(StampStyle(StampTint.Lilac, 2.5f, false), StampStyle.of("0f8e2a4c-1b3d-4e5f-8a9b-0c1d2e3f4a5b"))
        assertEquals(StampStyle(StampTint.Peach, -1.5f, true), StampStyle.of("7c9e6679-7425-40de-944b-e07fc1f90ae7"))
        assertEquals(StampStyle(StampTint.Sage, -1.5f, false), StampStyle.of("a3bb189e-8bf9-3888-9912-ace4e6543002"))
        assertEquals(StampStyle(StampTint.Peach, 2.5f, false), StampStyle.of("e1f2d3c4-b5a6-4789-9abc-def012345678"))
    }

    @Test fun `stamp month is the start month`() {
        assertEquals("SEP 2026", stampMonth(LocalDate.of(2026, 9, 3)))
        assertNull(stampMonth(null))
    }

    @Test fun `stored art round trips and tolerates junk`() {
        val art = StampArt(label = "LISBOA", paths = listOf("M4 52 L60 52", "M10 52 L10 30"))
        assertEquals(art, StampArt.decode(art.encode()))
        assertEquals(art, StampArt.decode("""{"v":1,"label":"LISBOA","paths":["M4 52 L60 52","M10 52 L10 30"],"extra":true}"""))
        assertNull(StampArt.decode("not json"))
        assertNull(StampArt.decode(null))
    }
}
