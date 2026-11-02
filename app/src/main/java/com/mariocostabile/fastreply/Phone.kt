package com.mariocostabile.fastreply

/** Returns digits only in international form (no "+"), or null if [raw] has no digits. */
// shortcut: prefisso di default fisso a 39, renderlo configurabile se servono contatti esteri senza prefisso
fun normalizePhone(raw: String, defaultPrefix: String = "39"): String? {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    return when {
        raw.trim().startsWith("+") -> digits
        digits.startsWith("00") -> digits.drop(2)
        else -> defaultPrefix + digits
    }
}
