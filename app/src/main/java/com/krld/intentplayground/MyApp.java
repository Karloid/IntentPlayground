package com.krld.intentplayground;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;


public class MyApp extends Application {
    private static Handler mainHandler;
    private static Handler workerHandler;
    private static MyApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        mainHandler = new Handler();

        HandlerThread thread = new HandlerThread("Worker");
        thread.start();
        workerHandler = new Handler(thread.getLooper());
    }

    public static Handler getWorkerHandler() {
        return workerHandler;
    }

    @SuppressWarnings("unused")
    public static Handler getMainHandler() {
        return mainHandler;
    }

    public static MyApp getInstance() {
        return instance;
    }

    public SharedPreferences getSharedPrefs() {
        return getSharedPreferences("common", MODE_PRIVATE);
    }
}
