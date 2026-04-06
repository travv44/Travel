package com.example.myapplication.model;

/**
 * Одна запись истории посещений направлений (сохраняется в Firebase:
 * users/{uid}/visitHistory/{pushId}).
 */
public class VisitRecord {
    private String tripId;
    private String placeId;
    private String placeName;
    private String destination;
    private String lat;
    private String lon;
    private long visitedAt;
    private String type;
    private String tags;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public long getVisitedAt() {
        return visitedAt;
    }

    public void setVisitedAt(long visitedAt) {
        this.visitedAt = visitedAt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean hasValidCoordinates() {
        return !Double.isNaN(parseCoord(lat)) && !Double.isNaN(parseCoord(lon));
    }

    public double getLatAsDouble() {
        return parseCoord(lat);
    }

    public double getLonAsDouble() {
        return parseCoord(lon);
    }

    private static double parseCoord(String s) {
        if (s == null || s.trim().isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
