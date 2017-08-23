package com.example.linphonedemo;

import android.app.Application;

/**
 * Created by sunyao1 on 2017/8/15.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        LinphoneManager.createAndStart(this);
        super.onCreate();
    }
}
