package com.cyfrant.satellitelock.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.cyfrant.satellitelock.Application;
import com.cyfrant.satellitelock.MainActivity;
import com.cyfrant.satellitelock.R;
import com.cyfrant.satellitelock.StatusMessageBuilder;
import com.cyfrant.satellitelock.contract.SatelliteReceiverCallback;
import com.cyfrant.satellitelock.data.Satellites;

/**
 * Created by mzakharov on 10.08.16.
 */
public class SatelliteLockService extends Service implements SatelliteReceiverCallback {
    public static final String ACTION_LOCK_GPS = "GPS.Lock";
    public static final String ACTION_UNLOCK_GPS = "GPS.Unlock";
    public static final String ACTION_LOCK_BACKLIGHT = "Backlight.Lock";
    public static final String ACTION_UNLOCK_BACKLIGHT = "Backlight.Unlock";
    public static final String ACTION_SHOW_MAIN_ACTIVITY = ".MainActivity";
    public static final int NOTIFICATION_ID = 0xCA731117;

    private NotificationManager notificationManager;
    private PowerManager powerManager;
    private StatusMessageBuilder statusMessageBuilder;
    private PowerManager.WakeLock wakeLock;
    private boolean gpsLocked;
    // Service
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(SatelliteLockService.class.getName(), "Obtaining NotificationManager");
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        Log.i(SatelliteLockService.class.getName(), "--> " + notificationManager);
        statusMessageBuilder = new StatusMessageBuilder();
        statusMessageBuilder.setContext(this);
        gpsLocked = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(SatelliteLockService.class.getName(), "Releasing NotificationManager");
        notificationManager = null;
        releaseWakeLock();
        powerManager = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(SatelliteLockService.class.getName(), "Intent received " + intent);
        if (intent.getAction().equals(ACTION_LOCK_GPS)){
            Log.i(SatelliteLockService.class.getName(), "Binding listener and starting service");
            updateNotification("Starting satellite lock", "Waiting for receiver startup");
            acquireGpsLock();
        } else if (intent.getAction().equals(ACTION_UNLOCK_GPS)){
            Log.i(SatelliteLockService.class.getName(), "Unbinding listener and stopping service");
            releaseGpsLock();
            updateNotification("Stopped satellite lock", "Receiver released");
        } else if (intent.getAction().equals(ACTION_LOCK_BACKLIGHT)){
            releaseWakeLock();
            acquireWakeLock();
            updateNotification("Starting power lock", "Screen backlight locked");
        } else if (intent.getAction().equals(ACTION_UNLOCK_BACKLIGHT)){
            releaseWakeLock();
        }
        if (!gpsLocked && wakeLock == null){
            notificationManager.cancel(NOTIFICATION_ID);
            stopSelf(startId);
        }
        return START_REDELIVER_INTENT;
    }

    private void acquireWakeLock() {
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE,
                SatelliteLockService.class.getName());
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null){
            wakeLock.release();
        }
        wakeLock = null;
    }

    // SatelliteReceiverCallback
    @Override
    public void onSatellitesUpdated(Satellites satellites) {
        Log.i(SatelliteLockService.class.getName(), "Processing satellite coverage update " + satellites);
        statusMessageBuilder.onSatellitesUpdated(satellites);
        String text = statusMessageBuilder.getMessageText();
        updateNotification(text, text);
    }

    @Override
    public void onLocationUpdated(Location location) {
        Log.i(SatelliteLockService.class.getName(), "Processing location update " + location);
        statusMessageBuilder.onLocationUpdated(location);
        String text = statusMessageBuilder.getMessageText();
        updateNotification(text, text);
    }

    private Application getApplicationState(){
        return (Application)getApplication();
    }

    private void acquireGpsLock(){
        getApplicationState().registerCallback(this);
        gpsLocked = true;
    }

    private void releaseGpsLock(){
        getApplicationState().unregisterCallback(this);
        gpsLocked = false;
    }

    private Notification createNotification(String title, String text){
        Log.i(SatelliteLockService.class.getName(), "Creating notification for '" + title + "', '" + text + "'");
        Notification.Builder nb = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setTicker(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.satellite)
                .setLargeIcon(Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher), 256, 256, false))
                .setOngoing(true)
                .setContentIntent(createMainActivityIntent());
        if (isGpsLockAcquired()) {
            nb.addAction(R.drawable.ic_gps_off_white_18dp, getString(R.string.action_unlock), createUnlockCommandIntent());
        }
        if (isPowerLockAcquired()){
            nb.addAction(R.drawable.ic_battery_charging_full_white_18dp, getString(R.string.action_unlock_power), createUnlockScreenIntent());
        }
        return nb.build();
    }

    private PendingIntent createUnlockCommandIntent() {
        Intent stop = new Intent(this, SatelliteLockService.class);
        stop.setAction(ACTION_UNLOCK_GPS);
        PendingIntent unlockIntent = PendingIntent.getService(this, 0, stop, 0);
        return unlockIntent;
    }

    private PendingIntent createMainActivityIntent() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(ACTION_SHOW_MAIN_ACTIVITY);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        return pendingIntent;
    }

    private PendingIntent createUnlockScreenIntent(){
        Intent stop = new Intent(this, SatelliteLockService.class);
        stop.setAction(ACTION_UNLOCK_BACKLIGHT);
        PendingIntent unlockIntent = PendingIntent.getService(this, 0, stop, 0);
        return unlockIntent;
    }

    private void updateNotification(String title, String text){
        Log.i(SatelliteLockService.class.getName(), "Updating notification");
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, text));

    }

    public boolean isPowerLockAcquired() {
        return wakeLock != null;
    }

    public boolean isGpsLockAcquired() {
        return gpsLocked;
    }
}
