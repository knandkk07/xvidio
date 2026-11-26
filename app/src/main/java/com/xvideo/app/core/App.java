package com.xvideo.app.core;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class App extends Application {
    private static final String TAG = "App";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initFirebase();
    }

    public static App getInstance() {
        return instance;
    }

    private void initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
                Log.d(TAG, "Firebase initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase init error: " + e.getMessage());
        }
    }
}
