package com.cyfrant.satellitelock.data;

import android.location.GpsSatellite;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by mzakharov on 09.08.16.
 */
public class Satellites {
    public static enum Status {
        NO_GPS_SIGNAL,
        ACQUIRING,
        LOCKED_ON,
        UNKNOWN
    }

    private Status status;
    private Set<GpsSatellite> satellites;
    private int inUse;

    public Satellites(){
        status = Status.UNKNOWN;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Set<GpsSatellite> getSatellites() {
        if (satellites == null){
            satellites = new HashSet<>();
        }
        return satellites;
    }

    public void setSatellites(Set<GpsSatellite> satellites) {
        this.satellites = satellites;
    }

    public int getInUse() {
        return inUse;
    }

    public void setInUse(int inUse) {
        this.inUse = inUse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":{" + status + ": " + getInUse() + "/" + getSatellites().size() + "}";
    }
}
