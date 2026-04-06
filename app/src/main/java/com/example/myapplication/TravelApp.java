package com.example.myapplication;

import android.app.Application;

import com.example.myapplication.db.UserManager;
import com.google.firebase.FirebaseApp;

public class TravelApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        new UserManager(this).ensureActiveUser(null);
    }
}
