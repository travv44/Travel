package com.example.myapplication.db;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.myapplication.db.entity.UserEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManager {

    private static final String PREFS = "user_manager";
    private static final String KEY_ACTIVE_USER_ID = "active_user_id";

    private final SharedPreferences prefs;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback<T> {
        void onResult(@Nullable T value);
    }

    public UserManager(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.db = AppDatabase.getInstance(app);
    }

    public long getActiveUserId() {
        return prefs.getLong(KEY_ACTIVE_USER_ID, -1L);
    }

    public void setActiveUserId(long userId) {
        prefs.edit().putLong(KEY_ACTIVE_USER_ID, userId).apply();
    }

    /**
     * Гарантирует, что в БД есть хотя бы один пользователь, и выставляет активного.
     */
    public void ensureActiveUser(Callback<UserEntity> callback) {
        executor.execute(() -> {
            List<UserEntity> users = db.userDao().getAll();
            UserEntity active = null;
            long activeId = getActiveUserId();
            if (activeId > 0) {
                active = db.userDao().getById(activeId);
            }

            if (active == null) {
                if (users.isEmpty()) {
                    UserEntity u = new UserEntity("user_" + (System.currentTimeMillis() % 10000), System.currentTimeMillis());
                    long id = db.userDao().insert(u);
                    u.id = id;
                    active = u;
                } else {
                    active = users.get(0);
                }
                setActiveUserId(active.id);
            }

            UserEntity finalActive = active;
            if (callback != null) {
                callback.onResult(finalActive);
            }
        });
    }

    public void getAllUsers(Callback<List<UserEntity>> callback) {
        executor.execute(() -> {
            List<UserEntity> users = db.userDao().getAll();
            if (callback != null) callback.onResult(users);
        });
    }

    public void createUser(String username, Callback<UserEntity> callback) {
        executor.execute(() -> {
            String cleaned = username == null ? "" : username.trim();
            if (cleaned.isEmpty()) {
                if (callback != null) callback.onResult(null);
                return;
            }
            UserEntity u = new UserEntity(cleaned, System.currentTimeMillis());
            try {
                long id = db.userDao().insert(u);
                u.id = id;
                if (callback != null) callback.onResult(u);
            } catch (Exception e) {
                // например, username уже существует
                if (callback != null) callback.onResult(null);
            }
        });
    }
}

