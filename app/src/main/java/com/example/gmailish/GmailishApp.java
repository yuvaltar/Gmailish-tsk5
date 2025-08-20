package com.example.gmailish;

import android.app.Application;

import com.example.gmailish.util.ThemeManager;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class GmailishApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
