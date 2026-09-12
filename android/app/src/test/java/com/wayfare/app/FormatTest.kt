package com.wayfare.app

import com.wayfare.app.core.amountText
import com.wayfare.app.core.dateRange
import com.wayfare.app.core.dayLabel
import com.wayfare.app.core.money
import com.wayfare.app.core.shortDate
import com.wayfare.app.core.symbolFor
import org.junit.Assert.assertEquals
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

/**
 * Fixtures shared with the web app's `src/lib/format.ts`. A figure has to read
 * identically in both clients or the same trip looks like two different trips.
 */
class FormatTest {
    @Test fun `groups thousands the en-US way regardless of device locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("nl-NL"))
            assertEquals("2,140.50", amountText(BigDecimal("2140.5")))
            assertEquals("€2,140.50", money(BigDecimal("2140.5")))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `drops cents once a figure would crowd a phone`() {
        assertEquals("99,999.99", amountText(BigDecimal("99999.99")))
        assertEquals("100,000", amountText(BigDecimal("100000")))
        assertEquals("1,250,000", amountText(BigDecimal("1250000.49")))
    }

    @Test fun `renders negatives and zero`() {
        assertEquals("-42.00", amountText(BigDecimal("-42")))
        assertEquals("€0.00", money(BigDecimal.ZERO))
    }

    @Test fun `uses the web app's symbol table`() {
        assertEquals("€", symbolFor("EUR"))
        assertEquals("CHF ", symbolFor("CHF"))
        assertEquals("CHF 88.00", money(BigDecimal("88"), "CHF"))
        assertEquals("XYZ ", symbolFor("XYZ"))
    }

    @Test fun `short dates read as uppercase month and day`() {
        assertEquals("Sep 10", shortDate(LocalDate.of(2026, 9, 10)))
        assertEquals("Jan 1", shortDate(LocalDate.of(2026, 1, 1)))
    }

    @Test fun `date ranges cover every combination of open ends`() {
        val start = LocalDate.of(2026, 9, 10)
        val end = LocalDate.of(2026, 9, 20)
        assertEquals("Dates open", dateRange(null, null))
        assertEquals("From Sep 10", dateRange(start, null))
        assertEquals("Until Sep 20", dateRange(null, end))
        assertEquals("Sep 10 — Sep 20", dateRange(start, end))
    }

    @Test fun `day labels name today and yesterday`() {
        val today = LocalDate.of(2026, 9, 10)
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("Tue, Sep 8", dayLabel(today.minusDays(2), today))
    }
}
