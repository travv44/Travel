package com.example.myapplication.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "users",
        indices = {
                @Index(value = {"username"}, unique = true)
        }
)
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public UserEntity(@NonNull String username, long createdAt) {
        this.username = username;
        this.createdAt = createdAt;
    }
}

