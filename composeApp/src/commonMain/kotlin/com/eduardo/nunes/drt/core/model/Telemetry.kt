package com.eduardo.nunes.drt.core.model

data class Telemetry(
    val id: String,
    val date: String, // Usando String para simplificar sem kotlinx-datetime por enquanto
    val timer0to100: String = "0.000",
    val timer60ft: String = "--.---",
    val timer100m: String = "--.---",
    val timer201m: String = "--.---",
    val maxSpeed: Int = 0,
    val maxRpm: Int = 0
)
