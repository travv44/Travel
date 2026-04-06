package com.example.myapplication.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Trip implements Serializable {
    private String id;
    // uid пользователя Firebase (для статистики/синхронизации)
    private String userId;
    private String name;
    private String destination;
    private String destinationLat;
    private String destinationLon;
    private String date;
    private long createdAt;
    private List<EntertainmentPlace> favoritePlaces;

    public Trip() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.createdAt = System.currentTimeMillis();
        this.favoritePlaces = new ArrayList<>();
    }

    public Trip(String name, String destination, String destinationLat, String destinationLon) {
        this();
        this.name = name;
        this.destination = destination;
        this.destinationLat = destinationLat;
        this.destinationLon = destinationLon;
        this.favoritePlaces = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDestinationLat() {
        return destinationLat;
    }

    public void setDestinationLat(String destinationLat) {
        this.destinationLat = destinationLat;
    }

    public String getDestinationLon() {
        return destinationLon;
    }

    public void setDestinationLon(String destinationLon) {
        this.destinationLon = destinationLon;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<EntertainmentPlace> getFavoritePlaces() {
        if (favoritePlaces == null) {
            favoritePlaces = new ArrayList<>();
        }
        return favoritePlaces;
    }

    public void setFavoritePlaces(List<EntertainmentPlace> favoritePlaces) {
        this.favoritePlaces = favoritePlaces;
    }

    public void addFavoritePlace(EntertainmentPlace place) {
        if (favoritePlaces == null) {
            favoritePlaces = new ArrayList<>();
        }
        // Проверяем, нет ли уже такого места
        boolean exists = false;
        for (EntertainmentPlace p : favoritePlaces) {
            if (p.getId().equals(place.getId())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            favoritePlaces.add(place);
        }
    }

    public void removeFavoritePlace(String placeId) {
        if (favoritePlaces != null) {
            favoritePlaces.removeIf(p -> p.getId().equals(placeId));
        }
    }

    public boolean isFavoritePlace(String placeId) {
        if (favoritePlaces == null) {
            return false;
        }
        for (EntertainmentPlace p : favoritePlaces) {
            if (p.getId().equals(placeId)) {
                return true;
            }
        }
        return false;
    }
}

