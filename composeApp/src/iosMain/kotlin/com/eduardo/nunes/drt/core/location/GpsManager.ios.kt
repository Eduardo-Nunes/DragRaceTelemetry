package com.eduardo.nunes.drt.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.Foundation.NSError
import platform.darwin.NSObject

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual open class GpsManager : NSObject(), CLLocationManagerDelegateProtocol {
    private val locationManager = CLLocationManager()
    private val _currentSpeed = MutableStateFlow(0)
    actual val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    init {
        locationManager.delegate = this
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = 0.0 // Todos os movimentos
    }

    actual fun startTracking() {
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }

    actual fun stopTracking() {
        locationManager.stopUpdatingLocation()
        _currentSpeed.value = 0
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        location?.let {
            if (it.speed >= 0) {
                val speedKmH = (it.speed * 3.6).toInt() // Convert from m/s to km/h
                _currentSpeed.value = speedKmH
            }
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        println("Location manager error: ${didFailWithError.localizedDescription}")
    }
}