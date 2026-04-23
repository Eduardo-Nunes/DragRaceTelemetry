package com.eduardo.nunes.drt.features.race.model

import com.eduardo.nunes.drt.core.ui.utils.formatTimer

internal data class Checkpoint(val timeMs: Long? = null) {
    val isCompleted: Boolean get() = timeMs != null

    val formattedTime: String
        get() = formatTimer(timeMs)
}