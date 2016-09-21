package com.cyfrant.satellitelock;

import android.content.Context;
import android.location.Location;

import com.cyfrant.satellitelock.contract.SatelliteReceiverCallback;
import com.cyfrant.satellitelock.data.Satellites;

/**
 * Created by mzakharov on 12.08.16.
 */
public class StatusMessageBuilder implements SatelliteReceiverCallback {
    public static class StatusView {
        private int used;
        private int view;
        private float accuracy;
        private String status;

        public int getUsed() {
            return used;
        }

        public void setUsed(int used) {
            this.used = used;
        }

        public int getView() {
            return view;
        }

        public void setView(int view) {
            this.view = view;
        }

        public float getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(float accuracy) {
            this.accuracy = accuracy;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + ":{" + getStatus() + ": " + getUsed() + "/" + getView() + "; HDOP " + getAccuracy() + "}";
        }
    }

    private Satellites satellites;
    private Location location;
    private Context context;

    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public void onSatellitesUpdated(Satellites satellites) {
        this.satellites = satellites;
    }

    @Override
    public void onLocationUpdated(Location location) {
        this.location = location;
    }

    public synchronized String getMessageText(){
        if (satellites == null && location == null){
            return context.getResources().getStringArray(R.array.lockon_status)[0];
        }

        if (satellites != null && location == null){
            return context.getResources().getStringArray(R.array.lockon_status)[1] + ": " + satellites.getInUse() + "/" + satellites.getSatellites().size();
        }

        return context.getResources().getStringArray(R.array.lockon_status)[2] + ": " + satellites.getInUse() + "/" + satellites.getSatellites().size() +
                " HDOP " + location.getAccuracy() + " m.";
    }

    public synchronized StatusView getStatusView(){
        StatusView result = new StatusView();
        if (satellites == null && location == null){
            result.setStatus(context.getResources().getStringArray(R.array.lockon_status)[0]);
            return result;
        }
        if (satellites != null && location == null){
            result.setStatus(context.getResources().getStringArray(R.array.lockon_status)[1]);
            result.setUsed(satellites.getInUse());
            result.setView(satellites.getSatellites().size());
            return result;
        }
        result.setStatus(context.getResources().getStringArray(R.array.lockon_status)[2]);
        result.setUsed(satellites.getInUse());
        result.setView(satellites.getSatellites().size());
        result.setAccuracy(location.getAccuracy());
        return result;
    }

    public void reset(){
        satellites = null;
        location = null;
    }
}
