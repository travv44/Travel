package com.example.myapplication.model;

import java.io.Serializable;

public class EntertainmentPlace implements Serializable {
    private String id;
    private String name;
    private String description;
    private String lat;
    private String lon;
    private String imageUrl;
    private String address;
    private String tags;
    private String openingHours;
    private String website;
    private String phone;
    private String wikidataId;
    private String wikipediaTag;
    // Поля для планирования визита
    private String plannedDate; // формат: dd.MM.yyyy
    private String plannedTime; // формат: HH:mm

    public EntertainmentPlace() {
    }

    public EntertainmentPlace(String id, String name, String description, String lat, String lon, String imageUrl, String address) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.lat = lat;
        this.lon = lon;
        this.imageUrl = imageUrl;
        this.address = address;
    }

    public String getId() {
        return id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(String plannedDate) {
        this.plannedDate = plannedDate;
    }

    public String getPlannedTime() {
        return plannedTime;
    }

    public void setPlannedTime(String plannedTime) {
        this.plannedTime = plannedTime;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWikidataId() {
        return wikidataId;
    }

    public void setWikidataId(String wikidataId) {
        this.wikidataId = wikidataId;
    }

    public String getWikipediaTag() {
        return wikipediaTag;
    }

    public void setWikipediaTag(String wikipediaTag) {
        this.wikipediaTag = wikipediaTag;
    }
}

