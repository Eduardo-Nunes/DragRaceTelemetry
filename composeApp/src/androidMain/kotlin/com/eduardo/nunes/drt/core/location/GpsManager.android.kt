package com.eduardo.nunes.drt.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class GpsManager : KoinComponent, LocationListener {
    private val context: Context by inject()
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _currentSpeed = MutableStateFlow(0)
    actual val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    @SuppressLint("MissingPermission")
    actual fun startTracking() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // 1000ms (1 segundo). É o limite realista do hardware GPS.
                0f, // 0 metros
                this
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun stopTracking() {
        locationManager.removeUpdates(this)
        _currentSpeed.value = 0
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            val speedKmH = (location.speed * 3.6).toInt() // Convert from m/s to km/h
            _currentSpeed.value = speedKmH
        }
    }
}