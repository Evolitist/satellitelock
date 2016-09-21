package com.cyfrant.satellitelock;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.cyfrant.satellitelock.contract.SatelliteReceiverCallback;
import com.cyfrant.satellitelock.data.Satellites;
import com.cyfrant.satellitelock.service.SatelliteLockService;

public class MainActivity extends AppCompatActivity implements SatelliteReceiverCallback{
    private static int REQUEST_ALL_PERMISSIONS = 0xFF;
    private TextView textInView;
    private TextView textInUse;
    private TextView textAccuracy;
    private TextView textStatus;
    private Switch switchLock;
    private Switch switchBacklightLock;
    private ImageButton buttonSettings;
    private ImageButton buttonForceAGPS;
    private StatusMessageBuilder statusMessageBuilder;
    private boolean permissionDenied = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(MainActivity.class.getName(), "MainActivity created");
        statusMessageBuilder = new StatusMessageBuilder();
        statusMessageBuilder.setContext(this);
        textAccuracy = (TextView)findViewById(R.id.textAccuracy);
        textInUse = (TextView)findViewById(R.id.textSatellitesInUse);
        textInView = (TextView)findViewById(R.id.textSatellitesInView);
        textStatus = (TextView)findViewById(R.id.textStatus);

        switchLock = (Switch)findViewById(R.id.switchLock);
        switchLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent lockIntent = new Intent(MainActivity.this, SatelliteLockService.class);
                if (switchLock.isChecked()){
                    Log.i(MainActivity.class.getName(), "Starting Lock service");
                    lockIntent.setAction(SatelliteLockService.ACTION_LOCK_GPS);
                    startService(lockIntent);
                } else {
                    Log.i(MainActivity.class.getName(), "Stopping Lock service");
                    lockIntent.setAction(SatelliteLockService.ACTION_UNLOCK_GPS);
                    startService(lockIntent);
                }

            }
        });
        buttonSettings = (ImageButton)findViewById(R.id.buttonLocationSettings);
        buttonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(MainActivity.class.getName(), "Requesting location settings");
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                MainActivity.this.startActivity(intent);
            }
        });

        buttonForceAGPS = (ImageButton)findViewById(R.id.buttonForceAGPS);
        buttonForceAGPS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(MainActivity.class.getName(), "Acquiring LocationManager to perform A-GPS reset");
                LocationManager locationManager = getApplicationState().getLocationManager();
                if (locationManager != null){
                    restartAGPS(locationManager);
                } else {
                    Log.w(MainActivity.class.getName(), "LocationManager released");
                }
            }
        });

        switchBacklightLock = (Switch)findViewById(R.id.switchBacklightLock);
        switchBacklightLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent lockIntent = new Intent(MainActivity.this, SatelliteLockService.class);
                if (switchLock.isChecked()){
                    Log.i(MainActivity.class.getName(), "Starting Power Lock");
                    lockIntent.setAction(SatelliteLockService.ACTION_LOCK_BACKLIGHT);
                    startService(lockIntent);
                } else {
                    Log.i(MainActivity.class.getName(), "Stopping Power Lock");
                    lockIntent.setAction(SatelliteLockService.ACTION_UNLOCK_BACKLIGHT);
                    startService(lockIntent);
                }
            }
        });
    }

    private void restartAGPS(LocationManager locationManager) {
        Log.i(MainActivity.class.getName(), "Sending request to clear A-GPS data");
        locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", null);
        Bundle bundle = new Bundle();
        Log.i(MainActivity.class.getName(), "Sending request to download XTRA data");
        locationManager.sendExtraCommand("gps", "force_xtra_injection", bundle);
        Log.i(MainActivity.class.getName(), "Sending request to resync network time");
        locationManager.sendExtraCommand("gps", "force_time_injection", bundle);
    }


    @Override
    protected void onResume() {
        super.onResume();
        resetViews();
        Log.i(MainActivity.class.getName(), "Registering for updates");
        if(!permissionDenied)
        {
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ALL_PERMISSIONS);
                //return;
            }
            else getApplicationState().registerCallback(this);
        }
    }

    private void resetViews() {
        statusMessageBuilder.reset();
        textAccuracy.setText("N/A");
        textStatus.setText(getResources().getStringArray(R.array.lockon_status)[3]);
        textInUse.setText("0");
        textInView.setText("0");
        switchLock.setChecked(getApplicationState().isGpsLockAcquired());
        switchBacklightLock.setChecked(getApplicationState().isPowerLockAcquired());
        updateControlsAvailability();
    }

    private void updateControlsAvailability() {
        switchLock.setEnabled(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        //switchBacklightLock.setEnabled(ActivityCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED);
        //buttonForceAGPS.setEnabled(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(MainActivity.class.getName(), "Releasing updates");
        getApplicationState().unregisterCallback(this);
    }

    @Override
    public void onSatellitesUpdated(final Satellites satellites) {
        Log.i(MainActivity.class.getName(), "Satellite info update received " + satellites);
        statusMessageBuilder.onSatellitesUpdated(satellites);
        updateViews();
    }

    @Override
    public void onLocationUpdated(final Location location) {
        Log.i(MainActivity.class.getName(), "Location update received " + location);
        statusMessageBuilder.onLocationUpdated(location);
        updateViews();
    }

    private void updateViews(){
        final StatusMessageBuilder.StatusView statusView = statusMessageBuilder.getStatusView();
        Log.i(MainActivity.class.getName(), "Updating views with " + statusView);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textInView.setText(Integer.toString(statusView.getView()));
                textInUse.setText(Integer.toString(statusView.getUsed()));
                textStatus.setText(statusView.getStatus());
                textAccuracy.setText(Float.toString(statusView.getAccuracy()));
            }
        });
    }

    private Application getApplicationState(){
        return (Application)getApplication();
    }

    private boolean hasAllGrants(int[] grantResults){
        if (grantResults == null || grantResults.length == 0){
            return true;
        }

        for(int result : grantResults){
            if (result != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }

        return true;
    }

    private boolean hasGrantedPermission(String permission, String[] permissions, int[] grants){
        if (permissions == null || permissions.length == 0 || grants == null || grants.length == 0 || permission == null || permission.trim().isEmpty()){
            return true;
        }
        if (permissions.length != grants.length){
            return false;
        }

        for(int i = 0; i < permissions.length; i++){
            if (permission.equals(permissions[i])){
                return grants[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_ALL_PERMISSIONS){
            if (hasGrantedPermission(Manifest.permission.ACCESS_FINE_LOCATION, permissions, grantResults)){
                getApplicationState().registerCallback(this);
            } else {
                //Toast.makeText(MainActivity.this, R.string.status_no_permissions, Toast.LENGTH_SHORT).show();
                permissionDenied = true;
                new AlertDialog.Builder(this).setTitle(R.string.error)
                    .setMessage(R.string.status_no_permissions)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            finish();
                        }
                    }).show();
            }
        }
        updateControlsAvailability();
    }

}
