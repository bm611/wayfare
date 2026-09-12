package com.wayfare.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Money and date rendering, ported from `src/lib/format.ts`.
 *
 * The web app formats every figure with the en-US grouping style regardless of
 * the reader's locale, so the two clients have to agree here or the same trip
 * reads as €2,140.50 in the browser and € 2.140,50 on the phone.
 */

private val SYMBOLS = mapOf(
    "EUR" to "€", "USD" to "$", "GBP" to "£", "CHF" to "CHF ", "SEK" to "kr ",
    "NOK" to "kr ", "DKK" to "kr ", "PLN" to "zł ", "CZK" to "Kč ", "HUF" to "Ft ",
    "RON" to "lei ", "ISK" to "kr ", "TRY" to "₺", "JPY" to "¥", "CNY" to "¥",
    "HKD" to "HK$", "SGD" to "S$", "KRW" to "₩", "INR" to "₹", "IDR" to "Rp",
    "MYR" to "RM", "PHP" to "₱", "THB" to "฿", "ILS" to "₪", "AUD" to "A$",
    "NZD" to "NZ$", "CAD" to "C$", "BRL" to "R$", "MXN" to "MX$", "ZAR" to "R",
)

fun symbolFor(currency: String): String = SYMBOLS[currency] ?: "$currency "

private val US = DecimalFormatSymbols(Locale.US)
private val WITH_CENTS = DecimalFormat("#,##0.00", US)
private val WITHOUT_CENTS = DecimalFormat("#,##0", US)

/** 2140.5 -> "2,140.50". Cents are dropped once a number gets long enough to crowd a phone. */
fun amountText(value: BigDecimal, cents: Boolean = value.abs() < BigDecimal(100_000)): String =
    if (cents) WITH_CENTS.format(value) else WITHOUT_CENTS.format(value.setScale(0, RoundingMode.HALF_UP))

/** The full figure a reader sees, e.g. "€2,140.50" or "CHF 88.00". */
fun money(value: BigDecimal, currency: String = "EUR"): String =
    symbolFor(currency) + amountText(value)

// Sentence case, not capitals: the native design system reserves uppercase for
// the single 8sp superscript role, and these dates sit on every trip card.
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun shortDate(date: LocalDate): String = "${MONTHS[date.monthValue - 1]} ${date.dayOfMonth}"

fun dateRange(start: LocalDate?, end: LocalDate?): String = when {
    start == null && end == null -> "Dates open"
    end == null -> "From ${shortDate(start!!)}"
    start == null -> "Until ${shortDate(end)}"
    else -> "${shortDate(start)} — ${shortDate(end)}"
}

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        -1L -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    }
