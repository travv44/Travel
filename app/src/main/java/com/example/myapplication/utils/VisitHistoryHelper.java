package com.example.myapplication.utils;

import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.VisitRecord;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * История посещённых направлений в Firebase Realtime Database:
 * users/{uid}/visitHistory/{autoKey}
 */
public class VisitHistoryHelper {

    private static final String DATABASE_URL = "https://nosql-562de-default-rtdb.firebaseio.com/";

    private static VisitHistoryHelper instance;
    private final DatabaseReference root;

    private VisitHistoryHelper() {
        root = FirebaseDatabase.getInstance(DATABASE_URL).getReference();
    }

    public static VisitHistoryHelper getInstance() {
        if (instance == null) {
            instance = new VisitHistoryHelper();
        }
        return instance;
    }

    public interface OnVisitHistoryLoaded {
        void onLoaded(List<VisitRecord> records);
        void onError(String message);
    }

    /**
     * Добавляет событие в историю (не блокирует UI).
     */
    public void recordTripDestinationVisit(String uid, String tripId, String destination,
                                           String lat, String lon, String type) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        DatabaseReference ref = root.child("users").child(uid).child("visitHistory").push();
        Map<String, Object> m = new HashMap<>();
        m.put("tripId", tripId != null ? tripId : "");
        m.put("destination", destination != null ? destination : "");
        m.put("lat", lat != null ? lat : "");
        m.put("lon", lon != null ? lon : "");
        m.put("visitedAt", System.currentTimeMillis());
        m.put("type", type != null ? type : "unknown");
        ref.setValue(m);
    }

    /**
     * Событие взаимодействия с местом (просмотр/добавление/удаление и т.д.) с тегами.
     */
    public void recordPlaceInteraction(String uid, EntertainmentPlace place, String eventType) {
        if (uid == null || uid.isEmpty() || place == null) {
            return;
        }
        DatabaseReference ref = root.child("users").child(uid).child("visitHistory").push();
        Map<String, Object> m = new HashMap<>();
        m.put("tripId", "");
        m.put("placeId", place.getId() != null ? place.getId() : "");
        m.put("placeName", place.getName() != null ? place.getName() : "");
        m.put("destination", place.getAddress() != null ? place.getAddress() : "");
        m.put("lat", place.getLat() != null ? place.getLat() : "");
        m.put("lon", place.getLon() != null ? place.getLon() : "");
        m.put("tags", place.getTags() != null ? place.getTags() : "");
        m.put("visitedAt", System.currentTimeMillis());
        m.put("type", eventType != null ? eventType : "place_interaction");
        ref.setValue(m);
    }

    public void loadVisitHistory(String uid, OnVisitHistoryLoaded callback) {
        if (uid == null || uid.isEmpty()) {
            if (callback != null) {
                callback.onLoaded(new ArrayList<>());
            }
            return;
        }
        root.child("users").child(uid).child("visitHistory")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<VisitRecord> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            VisitRecord r = fromSnapshot(child);
                            if (r != null) {
                                list.add(r);
                            }
                        }
                        if (callback != null) {
                            callback.onLoaded(list);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        if (callback != null) {
                            callback.onError(error != null ? error.getMessage() : "cancelled");
                        }
                    }
                });
    }

    private static VisitRecord fromSnapshot(DataSnapshot child) {
        if (child == null) {
            return null;
        }
        VisitRecord r = new VisitRecord();
        r.setTripId(stringOf(child.child("tripId").getValue()));
        r.setPlaceId(stringOf(child.child("placeId").getValue()));
        r.setPlaceName(stringOf(child.child("placeName").getValue()));
        r.setDestination(stringOf(child.child("destination").getValue()));
        r.setLat(stringOf(child.child("lat").getValue()));
        r.setLon(stringOf(child.child("lon").getValue()));
        r.setType(stringOf(child.child("type").getValue()));
        r.setTags(stringOf(child.child("tags").getValue()));
        Object at = child.child("visitedAt").getValue();
        if (at instanceof Long) {
            r.setVisitedAt((Long) at);
        } else if (at instanceof Number) {
            r.setVisitedAt(((Number) at).longValue());
        } else {
            r.setVisitedAt(0L);
        }
        return r;
    }

    private static String stringOf(Object v) {
        return v != null ? v.toString() : "";
    }
}
