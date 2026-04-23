package com.eduardo.nunes.drt.core.ui.utils

fun formatTimer(timeMs: Long? = null): String {
    if (timeMs == null) return "--.---"
    val seconds = timeMs / 1000
    val fractions = timeMs % 1000

    return "$seconds.${fractions.toString().padStart(3, '0')}"
}