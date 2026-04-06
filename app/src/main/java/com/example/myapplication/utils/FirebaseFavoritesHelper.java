package com.example.myapplication.utils;

import androidx.annotation.NonNull;

import com.example.myapplication.model.EntertainmentPlace;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FirebaseFavoritesHelper {

    public interface FavoritesCallback {
        void onLoaded(List<EntertainmentPlace> places);
        void onError(String error);
    }

    public interface IdsCallback {
        void onLoaded(Set<String> ids);
        void onError(String error);
    }

    private final DatabaseReference root;

    public FirebaseFavoritesHelper() {
        FirebaseDatabase db = FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/");
        root = db.getReference();
    }

    private String uid() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    public void setFavorite(EntertainmentPlace place, boolean favorite) {
        String uid = uid();
        if (uid == null || place == null || place.getId() == null) return;

        // сохраняем объект места в /places
        Map<String, Object> placeMap = new HashMap<>();
        placeMap.put("id", place.getId());
        placeMap.put("name", place.getName());
        placeMap.put("description", place.getDescription());
        placeMap.put("lat", place.getLat());
        placeMap.put("lon", place.getLon());
        placeMap.put("imageUrl", place.getImageUrl());
        placeMap.put("address", place.getAddress());
        placeMap.put("updatedAt", System.currentTimeMillis());

        root.child("places").child(place.getId()).updateChildren(placeMap);

        DatabaseReference favRef = root.child("users").child(uid).child("favorites").child(place.getId());
        if (favorite) {
            favRef.setValue(true);
        } else {
            favRef.removeValue();
        }
    }

    public void loadFavoriteIds(IdsCallback callback) {
        String uid = uid();
        if (uid == null) {
            callback.onLoaded(new HashSet<>());
            return;
        }
        root.child("users").child(uid).child("favorites")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Set<String> ids = new HashSet<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            ids.add(child.getKey());
                        }
                        callback.onLoaded(ids);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    public void loadFavorites(FavoritesCallback callback) {
        String uid = uid();
        if (uid == null) {
            callback.onLoaded(new ArrayList<>());
            return;
        }

        root.child("users").child(uid).child("favorites")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> ids = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            ids.add(child.getKey());
                        }
                        if (ids.isEmpty()) {
                            callback.onLoaded(new ArrayList<>());
                            return;
                        }

                        root.child("places").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot placesSnap) {
                                List<EntertainmentPlace> places = new ArrayList<>();
                                for (String id : ids) {
                                    DataSnapshot p = placesSnap.child(id);
                                    if (!p.exists()) continue;
                                    EntertainmentPlace place = p.getValue(EntertainmentPlace.class);
                                    if (place != null) places.add(place);
                                }
                                callback.onLoaded(places);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                callback.onError(error.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }
}

