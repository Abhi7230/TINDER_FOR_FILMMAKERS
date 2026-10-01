package com.tff.app.core

object Money {
    fun symbol(currency: String): String = when (currency.uppercase()) {
        "INR" -> "₹"; "USD" -> "$"; "GBP" -> "£"; "EUR" -> "€"; else -> currency.uppercase() + " "
    }

    /** "₹2,500", "₹1,20,000" (Indian grouping for INR), "$350", "£1,200". Whole currency units only. */
    fun format(amount: Int, currency: String): String {
        val digits = kotlin.math.abs(amount).toString()
        val grouped = if (currency.equals("INR", true)) indianGroup(digits) else westernGroup(digits)
        return (if (amount < 0) "-" else "") + symbol(currency) + grouped
    }

    /** "₹2,000–3,000" or "₹2,500" when min == max or min is null. */
    fun range(min: Int?, max: Int?, currency: String): String = when {
        max == null && min == null -> ""
        min == null || max == null || min == max -> format(max ?: min!!, currency)
        else -> format(min, currency) + "–" + (if (currency.equals("INR", true)) indianGroup(max.toString()) else westernGroup(max.toString()))
    }

    private fun westernGroup(d: String): String = d.reversed().chunked(3).joinToString(",").reversed()
    private fun indianGroup(d: String): String {
        if (d.length <= 3) return d
        val last3 = d.takeLast(3)
        val rest = d.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }
}
