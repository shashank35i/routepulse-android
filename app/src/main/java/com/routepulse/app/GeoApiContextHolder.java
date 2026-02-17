package com.routepulse.app;

import android.util.Log;

import com.google.maps.GeoApiContext;

public class GeoApiContextHolder {
    private static GeoApiContext instance;

    public static synchronized GeoApiContext getInstance(String apiKey) {
        if (instance == null) {
            instance = new GeoApiContext.Builder()
                    .apiKey(apiKey)
                    .build();
        }
        return instance;
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            try {
                instance.shutdown();
            } catch (Exception e) {
                Log.e("GeoApiContextHolder", "Error shutting down GeoApiContext: " + e.getMessage());
            }
            instance = null;
        }
    }
}