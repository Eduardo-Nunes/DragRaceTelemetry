package com.eduardo.nunes.drt.features.race.model

internal data class TelemetryState(
    val speed: Int = 0,
    val rpm: Int = 0,
    val maxSpeed: Int = 0,
    val maxRpm: Int = 0
)