package com.eduardo.nunes.drt.core.location

import kotlinx.coroutines.flow.StateFlow

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect open class GpsManager() {
    val currentSpeed: StateFlow<Int>
    fun startTracking()
    fun stopTracking()
}