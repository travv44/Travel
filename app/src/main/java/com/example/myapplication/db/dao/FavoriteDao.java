package com.example.myapplication.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.myapplication.db.entity.FavoriteEntity;
import com.example.myapplication.db.entity.PlaceEntity;

import java.util.List;

@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void add(FavoriteEntity favorite);

    @Query("DELETE FROM favorites WHERE user_id = :userId AND place_id = :placeId")
    void remove(long userId, String placeId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id = :userId AND place_id = :placeId)")
    boolean isFavorite(long userId, String placeId);

    @Query("SELECT COUNT(*) FROM favorites WHERE user_id = :userId")
    int countFavorites(long userId);

    @Transaction
    @Query(
            "SELECT p.* FROM places p " +
            "JOIN favorites f ON f.place_id = p.id " +
            "WHERE f.user_id = :userId " +
            "ORDER BY f.created_at DESC"
    )
    List<PlaceEntity> getFavoritePlaces(long userId);

    @Transaction
    @Query(
            "SELECT p.* FROM places p " +
            "JOIN favorites f ON f.place_id = p.id " +
            "WHERE f.user_id = :userId AND (p.name LIKE '%' || :q || '%' OR p.address LIKE '%' || :q || '%') " +
            "ORDER BY f.created_at DESC"
    )
    List<PlaceEntity> searchFavorites(long userId, String q);
}

