package com.eduardo.nunes.drt.features.race.model

import com.eduardo.nunes.drt.core.ui.utils.formatTimer

internal data class RaceSession(
    val isRecording: Boolean = false,
    val currentDistance: Double = 0.0,
    val currentTimerMs: Long = 0L,

    // Olha como fica limpo! Cada métrica é apenas um Checkpoint.
    val run0to100: Checkpoint = Checkpoint(),
    val run60ft: Checkpoint = Checkpoint(),
    val run100m: Checkpoint = Checkpoint(),
    val run201m: Checkpoint = Checkpoint()
) {
    // Getter para a UI consumir o tempo atual já formatado
    val formattedCurrentTimer: String
        get() = formatTimer(currentTimerMs)
}