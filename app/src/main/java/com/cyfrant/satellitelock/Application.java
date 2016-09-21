package com.cyfrant.satellitelock;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.cyfrant.satellitelock.contract.SatelliteReceiver;
import com.cyfrant.satellitelock.contract.SatelliteReceiverCallback;
import com.cyfrant.satellitelock.data.Satellites;
import com.cyfrant.satellitelock.service.SatelliteLockService;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by mzakharov on 09.08.16.
 */
public class Application extends android.app.Application implements LocationListener, GpsStatus.Listener, SatelliteReceiver {
    private LocationManager locationManager;
    private Set<SatelliteReceiverCallback> callbacks;
    private ExecutorService threadPool;
    private boolean bounded;

    // Application
    @Override
    public void onCreate() {
        super.onCreate();
        callbacks = new HashSet<>();
        threadPool = Executors.newCachedThreadPool();
        bounded = false;
    }

    // GpsStatus.Listener
    @Override
    public void onGpsStatusChanged(int event) {
        if (event == GpsStatus.GPS_EVENT_FIRST_FIX) {
            Log.i(Application.class.getName(), "First fix occured");
            GpsStatus status = locationManager.getGpsStatus(null);
            Log.i(Application.class.getName(), "--> " + status);
            if (status != null && !callbacks.isEmpty()) {
                for (SatelliteReceiverCallback callback : callbacks) {
                    notifyListener(callback, fromGpsStatus(status));
                }
            }
        }
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            Log.i(Application.class.getName(), "Satellites status updated");
            GpsStatus status = locationManager.getGpsStatus(null);
            Log.i(Application.class.getName(), "--> " + status);
            if (status != null && !callbacks.isEmpty()) {
                for (SatelliteReceiverCallback callback : callbacks) {
                    notifyListener(callback, fromGpsStatus(status));
                }
            }
        }
    }

    // LocationListener
    @Override
    public void onLocationChanged(Location location) {
        Log.i(Application.class.getName(), "Location updated: " + location == null ? "???" : location.getLatitude() + ";" + location.getLongitude());
        if (location != null && location.hasAccuracy() && !callbacks.isEmpty()) {
            Log.i(Application.class.getName(), "Accuracy is " + location.getAccuracy() + " m.");
            for (SatelliteReceiverCallback callback : callbacks) {
                notifyListener(callback, replicate(location));
            }
        }

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    // SatelliteReceiver
    @Override
    public void registerCallback(SatelliteReceiverCallback callback) {
        Log.i(Application.class.getName(), "Registering " + callback);
        callbacks.add(callback);
        Log.i(Application.class.getName(), "Registered " + callbacks.size() + " callback(s)");
        ensureProviderLock();
    }

    @Override
    public void unregisterCallback(SatelliteReceiverCallback callback) {
        Log.i(Application.class.getName(), "Unregistering " + callback);
        callbacks.remove(callback);
        Log.i(Application.class.getName(), "Remaining " + callbacks.size() + " callback(s)");
        ensureProviderUnlock();
    }

    private void ensureProviderLock() {
        if (locationManager == null) {
            Log.i(Application.class.getName(), "Acquiring LocationManager");
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        if (!bounded && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.i(Application.class.getName(), "Requesting location updates");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            Log.i(Application.class.getName(), "Binding location listener");
            locationManager.addGpsStatusListener(this);
            bounded = true;
        }
    }

    private void ensureProviderUnlock() {
        if (callbacks.isEmpty() && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.i(Application.class.getName(), "No callbacks remaining. Removing status listener");
            locationManager.removeGpsStatusListener(this);
            Log.i(Application.class.getName(), "Unbinding location listener");
            locationManager.removeUpdates(this);
            bounded = false;
        }
    }

    private void notifyListener(final SatelliteReceiverCallback listener, final Location location) {
        Log.i(Application.class.getName(), "Notifying " + listener + " with " + location);
        threadPool.submit(new Runnable() {
            @Override
            public void run() {
                listener.onLocationUpdated(location);
            }
        });
    }

    private void notifyListener(final SatelliteReceiverCallback listener, final Satellites satellites) {
        Log.i(Application.class.getName(), "Notifying " + listener + " with " + satellites);
        threadPool.submit(new Runnable() {
            @Override
            public void run() {
                listener.onSatellitesUpdated(satellites);
            }
        });
    }

    private Satellites fromGpsStatus(GpsStatus source) {
        Satellites result = new Satellites();
        Iterator<GpsSatellite> gpsSatelliteIterator = source.getSatellites().iterator();
        int total = 0;
        int good = 0;
        int inuse = 0;
        while (gpsSatelliteIterator.hasNext()) {
            GpsSatellite satellite = gpsSatelliteIterator.next();
            result.getSatellites().add(satellite);
            total++;
            if (satellite.usedInFix()) {
                inuse++;
            }
            if (satellite.getSnr() > 10) {
                good++;
            }
        }
        result.setInUse(inuse);
        if (total == 0 || good == 0 || inuse == 0) {
            result.setStatus(Satellites.Status.NO_GPS_SIGNAL);
        }
        if (total > 0 && good < 3) {
            result.setStatus(Satellites.Status.ACQUIRING);
        }

        return result;
    }

    private Location replicate(Location source) {
        return new Location(source);
    }

    private SatelliteLockService getLockService(){
        if (!callbacks.isEmpty()) {
            for (SatelliteReceiverCallback callback : callbacks) {
                if (callback instanceof SatelliteLockService) {
                    Log.i(Application.class.getName(), "SatelliteLockService callback found : " + callback);
                    return (SatelliteLockService)callback;
                }
            }
        }
        return null;
    }

    public boolean isLockServiceRunning() {
        return getLockService() != null;
    }

    public LocationManager getLocationManager(){
        return locationManager;
    }

    public boolean isPowerLockAcquired()
    {
        return isLockServiceRunning() && getLockService().isPowerLockAcquired();
    }

    public boolean isGpsLockAcquired()
    {
        return isLockServiceRunning() && getLockService().isGpsLockAcquired();
    }
}
