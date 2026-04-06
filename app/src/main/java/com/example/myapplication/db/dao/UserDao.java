package com.example.myapplication.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myapplication.db.entity.UserEntity;

import java.util.List;

@Dao
public interface UserDao {

    @Query("SELECT * FROM users ORDER BY created_at DESC")
    List<UserEntity> getAll();

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    UserEntity getById(long id);

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity getByUsername(String username);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(UserEntity user);

    @Query("DELETE FROM users WHERE id = :id")
    void deleteById(long id);
}

