package com.example.journal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import android.preference.PreferenceManager

object LocationUtils {

    const val PREF_LAST_KNOWN_LAT = "last_known_lat"
    const val PREF_LAST_KNOWN_LON = "last_known_lon"

    fun getLastKnownLocation(context: Context, locationManager: LocationManager): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationUpdate", "Location permission not granted")
            return null
        }

        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        val lastKnownLocation = when {
            gpsLocation != null && networkLocation != null -> if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            gpsLocation != null -> gpsLocation
            networkLocation != null -> networkLocation
            else -> null
        }

        // Save the last known location to SharedPreferences
        lastKnownLocation?.let {
            saveLocationToPreferences(context, it)
        }

        return lastKnownLocation
    }

    private fun saveLocationToPreferences(context: Context, location: Location) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putString(PREF_LAST_KNOWN_LAT, location.latitude.toString())
        editor.putString(PREF_LAST_KNOWN_LON, location.longitude.toString())
        editor.apply()
    }


    fun getSavedLocation(context: Context): Location? {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val lat = prefs.getString(PREF_LAST_KNOWN_LAT, null)
        val lon = prefs.getString(PREF_LAST_KNOWN_LON, null)

        return if (lat != null && lon != null) {
            Location(LocationManager.PASSIVE_PROVIDER).apply {
                latitude = lat.toDouble()
                longitude = lon.toDouble()
            }
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun requestSingleLocationUpdate(
        context: Context,
        locationManager: LocationManager,
        locationListener: LocationListener
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationUpdate", "Location permission not granted")
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val singleUpdateListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationListener.onLocationChanged(location)
                locationManager.removeUpdates(this)
                saveLocationToPreferences(context, location)
            }

            @Deprecated("This method is deprecated in API level 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Request location updates from both providers
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, singleUpdateListener, Looper.getMainLooper())
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, singleUpdateListener, Looper.getMainLooper())

        // Remove updates after a timeout to avoid waiting indefinitely
        handler.postDelayed({
            locationManager.removeUpdates(singleUpdateListener)
        }, 10000) // Timeout after 10 seconds
    }


    fun requestLocationPermissions(activity: Activity) {
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                EntryEditorActivity.REQUEST_CODE_PERMISSIONS
            )
        }
    }

    fun getCurrentLocation(context: Context, locationManager: LocationManager): Location? {
        return getLastKnownLocation(context, locationManager) ?: getSavedLocation(context)
    }
}
