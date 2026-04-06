package com.example.myapplication.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "favorites",
        primaryKeys = {"user_id", "place_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = UserEntity.class,
                        parentColumns = "id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = PlaceEntity.class,
                        parentColumns = "id",
                        childColumns = "place_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"user_id"}),
                @Index(value = {"place_id"})
        }
)
public class FavoriteEntity {

    @ColumnInfo(name = "user_id")
    public long userId;

    @NonNull
    @ColumnInfo(name = "place_id")
    public String placeId;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public FavoriteEntity(long userId, @NonNull String placeId, long createdAt) {
        this.userId = userId;
        this.placeId = placeId;
        this.createdAt = createdAt;
    }
}

