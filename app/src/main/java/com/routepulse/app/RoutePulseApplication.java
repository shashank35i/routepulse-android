package com.routepulse.app;

import android.app.Application;

import com.google.android.libraries.places.api.Places;

public class RoutePulseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
    }
}