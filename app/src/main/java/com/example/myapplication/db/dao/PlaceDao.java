package com.example.myapplication.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myapplication.db.entity.PlaceEntity;

import java.util.List;

@Dao
public interface PlaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PlaceEntity place);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PlaceEntity> places);

    @Query("SELECT * FROM places WHERE id = :id LIMIT 1")
    PlaceEntity getById(String id);

    @Query("SELECT * FROM places ORDER BY saved_at DESC")
    List<PlaceEntity> getAll();
}

