package com.cyfrant.satellitelock.contract;

import android.location.Location;

import com.cyfrant.satellitelock.data.Satellites;

/**
 * Created by mzakharov on 09.08.16.
 */
public interface SatelliteReceiverCallback {
    void onSatellitesUpdated(Satellites satellites);
    void onLocationUpdated(Location location);
}
