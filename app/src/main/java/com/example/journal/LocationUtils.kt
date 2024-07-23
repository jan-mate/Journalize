package com.example.journal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat

object LocationUtils {

    fun getLastKnownLocation(context: Context, locationManager: LocationManager): Location? {
        var lastKnownLocation: Location? = null
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Use the most recent known location
            lastKnownLocation = when {
                gpsLocation != null && networkLocation != null -> if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } else {
            Log.e("LocationUpdate", "Location permission not granted")
        }
        return lastKnownLocation
    }

    fun requestSingleLocationUpdate(context: Context, locationManager: LocationManager, locationListener: LocationListener) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)
            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null)
        } else {
            Log.e("LocationUpdate", "Location permission not granted")
        }
    }

    fun requestLocationPermissions(activity: Activity, locationManager: LocationManager) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), EntryEditorActivity.REQUEST_CODE_PERMISSIONS)
        } else {
            val location = getLastKnownLocation(activity, locationManager)
            if (location != null) {
                (activity as EntryEditorActivity).updateCurrentLocationUI(location)
            }
        }
    }

    fun getCurrentLocation(context: Context, locationManager: LocationManager): Location? {
        var currentLocation: Location? = null
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Use the most recent known location
            currentLocation = when {
                gpsLocation != null && networkLocation != null -> if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } else {
            Log.e("LocationUpdate", "Location permission not granted")
        }
        return currentLocation
    }
}
