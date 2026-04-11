package com.eduardo.nunes.drt.core.location

import kotlinx.coroutines.flow.StateFlow

expect class GpsManager() {
    val currentSpeed: StateFlow<Int>
    fun startTracking()
    fun stopTracking()
}