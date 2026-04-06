package com.example.myapplication.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "places")
public class PlaceEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "lat")
    public String lat;

    @ColumnInfo(name = "lon")
    public String lon;

    @ColumnInfo(name = "image_url")
    public String imageUrl;

    @ColumnInfo(name = "address")
    public String address;

    @ColumnInfo(name = "opening_hours")
    public String openingHours;

    @ColumnInfo(name = "website")
    public String website;

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "wikidata_id")
    public String wikidataId;

    @ColumnInfo(name = "wikipedia_tag")
    public String wikipediaTag;

    @ColumnInfo(name = "saved_at")
    public long savedAt;

    public PlaceEntity(
            @NonNull String id,
            @NonNull String name,
            String description,
            String lat,
            String lon,
            String imageUrl,
            String address,
            String openingHours,
            String website,
            String phone,
            String wikidataId,
            String wikipediaTag,
            long savedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.lat = lat;
        this.lon = lon;
        this.imageUrl = imageUrl;
        this.address = address;
        this.openingHours = openingHours;
        this.website = website;
        this.phone = phone;
        this.wikidataId = wikidataId;
        this.wikipediaTag = wikipediaTag;
        this.savedAt = savedAt;
    }
}

