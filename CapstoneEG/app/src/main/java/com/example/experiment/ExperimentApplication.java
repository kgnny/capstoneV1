package com.example.experiment;

import android.app.Application;
import android.util.Log;

public class ExperimentApplication extends Application {
    private DatabaseManager dbManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize shared resources
        Log.d("ExperimentApplication", "Initializing database manager");
        dbManager = DatabaseManager.getInstance(this);
        Log.d("ExperimentApplication", "Database manager initialized");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // Release non-critical resources
        if (dbManager != null) {
            dbManager.onLowMemory();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Handle memory pressure
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Release resources when memory is low
            if (dbManager != null) {
                dbManager.onLowMemory();
            }
        }
    }

    public DatabaseManager getDbManager() {
        if (dbManager == null) {
            dbManager = new DatabaseManager(this);
        }
        return dbManager;
    }
}