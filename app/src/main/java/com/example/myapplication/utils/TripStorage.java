package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.myapplication.model.Trip;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class TripStorage {
    private static final String PREFS_NAME = "trips_prefs";
    private static final String KEY_TRIPS = "trips";
    private final SharedPreferences prefs;
    private final Gson gson;

    public TripStorage(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void saveTrip(Trip trip) {
        List<Trip> trips = getAllTrips();
        trips.add(trip);
        saveTrips(trips);
    }

    public List<Trip> getAllTrips() {
        String json = prefs.getString(KEY_TRIPS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<Trip>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void updateTrip(Trip trip) {
        List<Trip> trips = getAllTrips();
        for (int i = 0; i < trips.size(); i++) {
            if (trips.get(i).getId().equals(trip.getId())) {
                trips.set(i, trip);
                break;
            }
        }
        saveTrips(trips);
    }

    public void deleteTrip(String tripId) {
        List<Trip> trips = getAllTrips();
        trips.removeIf(trip -> trip.getId().equals(tripId));
        saveTrips(trips);
    }

    private void saveTrips(List<Trip> trips) {
        String json = gson.toJson(trips);
        prefs.edit().putString(KEY_TRIPS, json).apply();
    }
}

