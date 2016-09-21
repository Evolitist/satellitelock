package com.cyfrant.satellitelock.contract;

/**
 * Created by mzakharov on 09.08.16.
 */
public interface SatelliteReceiver {
    void registerCallback(SatelliteReceiverCallback callback);
    void unregisterCallback(SatelliteReceiverCallback callback);
}
