package com.routepulse.app;

import android.app.Application;
import androidx.lifecycle.ProcessLifecycleOwner;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize ProcessLifecycleOwner to ensure lifecycle components are set up
        ProcessLifecycleOwner.get().getLifecycle();
    }
}