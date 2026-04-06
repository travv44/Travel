package com.example.myapplication.model;

/**
 * Место из каталога с пояснением, почему оно попало в подборку.
 */
public class RecommendedPlace {
    private final EntertainmentPlace place;
    private final String reason;
    /** Расстояние до пользователя, км; null если не считали (каталог без гео). */
    private final Double distanceKm;

    public RecommendedPlace(EntertainmentPlace place, String reason) {
        this(place, reason, null);
    }

    public RecommendedPlace(EntertainmentPlace place, String reason, Double distanceKm) {
        this.place = place;
        this.reason = reason;
        this.distanceKm = distanceKm;
    }

    public EntertainmentPlace getPlace() {
        return place;
    }

    public String getReason() {
        return reason;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }
}
