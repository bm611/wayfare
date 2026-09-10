package com.wayfare.app.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Euro-anchored conversion, ported from `src/lib/fx.ts`.
 *
 * Rates are units of a currency per 1 EUR, so crossing two of them is a
 * division. Kept free of Android types so both clients' fixtures can run here.
 */

/** How many units of [to] one unit of [from] buys, or null if either is unknown. */
fun rateBetween(from: String, to: String, rates: Map<String, BigDecimal>): BigDecimal? {
    if (from == to) return BigDecimal.ONE
    val fromRate = rates[from]?.takeIf { it.signum() > 0 } ?: return null
    val toRate = rates[to]?.takeIf { it.signum() > 0 } ?: return null
    return toRate.divide(fromRate, RATE_SCALE, RoundingMode.HALF_UP)
}

/** The converted figure, rounded to cents the same way the web app rounds it. */
fun convert(amount: BigDecimal, from: String, to: String, rates: Map<String, BigDecimal>): BigDecimal? =
    rateBetween(from, to, rates)?.let { amount.multiply(it).setScale(2, RoundingMode.HALF_UP) }

/**
 * Enough places that a JPY or IDR rate survives the round trip. The rate that
 * was actually used is stored on the expense, so this figure is durable.
 */
const val RATE_SCALE = 8
