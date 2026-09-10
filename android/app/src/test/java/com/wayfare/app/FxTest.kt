package com.wayfare.app

import com.wayfare.app.core.convert
import com.wayfare.app.core.rateBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.math.BigDecimal
import org.junit.Test

/**
 * The euro-anchored conversion the web app performs in `src/lib/fx.ts`. The two
 * clients read the same ledger, so a JPY dinner has to land on the same cent.
 */
class FxTest {
    private val rates = mapOf(
        "EUR" to BigDecimal("1"),
        "USD" to BigDecimal("1.1578"),
        "GBP" to BigDecimal("0.8587"),
        "JPY" to BigDecimal("184.78"),
    )

    @Test fun `a currency converts to itself at par`() {
        assertEquals(BigDecimal.ONE, rateBetween("USD", "USD", rates))
    }

    @Test fun `crossing two rates goes through the euro`() {
        // 1 USD buys 0.8587 / 1.1578 GBP.
        assertEquals(BigDecimal("0.74166523"), rateBetween("USD", "GBP", rates))
    }

    @Test fun `converting to euro rounds to cents`() {
        assertEquals(BigDecimal("86.37"), convert(BigDecimal("100"), "USD", "EUR", rates))
        assertEquals(BigDecimal("21.65"), convert(BigDecimal("4000"), "JPY", "EUR", rates))
    }

    @Test fun `half a cent rounds up, matching the web app`() {
        val par = mapOf("EUR" to BigDecimal.ONE, "AAA" to BigDecimal("1"))
        assertEquals(BigDecimal("1.01"), convert(BigDecimal("1.005"), "AAA", "EUR", par))
    }

    @Test fun `an unknown currency has no rate rather than a wrong one`() {
        assertNull(rateBetween("EUR", "XYZ", rates))
        assertNull(convert(BigDecimal.TEN, "XYZ", "EUR", rates))
    }

    @Test fun `a zero rate is treated as missing, not as a division by zero`() {
        val broken = mapOf("EUR" to BigDecimal.ONE, "BAD" to BigDecimal.ZERO)
        assertNull(rateBetween("BAD", "EUR", broken))
    }
}
