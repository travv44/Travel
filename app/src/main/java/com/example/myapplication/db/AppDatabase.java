package com.example.myapplication.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.myapplication.db.dao.FavoriteDao;
import com.example.myapplication.db.dao.PlaceDao;
import com.example.myapplication.db.dao.UserDao;
import com.example.myapplication.db.entity.FavoriteEntity;
import com.example.myapplication.db.entity.PlaceEntity;
import com.example.myapplication.db.entity.UserEntity;

@Database(
        entities = {
                UserEntity.class,
                PlaceEntity.class,
                FavoriteEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public abstract PlaceDao placeDao();

    public abstract FavoriteDao favoriteDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "travel_app.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

