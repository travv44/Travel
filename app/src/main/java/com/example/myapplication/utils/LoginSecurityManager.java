package com.example.myapplication.utils;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Защита входа:
 * если за 1 час 10 неудачных попыток входа по email -> бан на 10 минут.
 *
 * Реализовано через транзакцию в Realtime Database (атомарное обновление счётчиков).
 */
public class LoginSecurityManager {

    public interface BanCheckCallback {
        void onAllowed();
        void onBanned(long bannedUntilMs);
        void onError(String error);
    }

    private static final long WINDOW_MS = 60 * 60_000L; // 1 час
    private static final long BAN_MS = 10 * 60_000L;
    private static final int MAX_ATTEMPTS = 10;

    private final DatabaseReference securityRoot;

    public LoginSecurityManager() {
        FirebaseDatabase db = FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/");
        securityRoot = db.getReference("security");
    }

    public void checkBan(String email, BanCheckCallback callback) {
        String key = safeKey(email);
        securityRoot.child("login_attempts").child(key)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long now = System.currentTimeMillis();
                        Long bannedUntil = snapshot.child("bannedUntil").getValue(Long.class);
                        if (bannedUntil != null && bannedUntil > now) {
                            callback.onBanned(bannedUntil);
                        } else {
                            callback.onAllowed();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    public void recordFailedAttempt(String email) {
        String key = safeKey(email);
        DatabaseReference ref = securityRoot.child("login_attempts").child(key);

        ref.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                long now = System.currentTimeMillis();

                Long firstAt = currentData.child("firstAt").getValue(Long.class);
                Long count = currentData.child("count").getValue(Long.class);
                Long bannedUntil = currentData.child("bannedUntil").getValue(Long.class);

                if (bannedUntil != null && bannedUntil > now) {
                    return Transaction.success(currentData);
                }

                if (firstAt == null || count == null || now - firstAt > WINDOW_MS) {
                    firstAt = now;
                    count = 0L;
                }

                count = count + 1;

                Map<String, Object> map = new HashMap<>();
                map.put("firstAt", firstAt);
                map.put("count", count);
                map.put("lastAt", now);

                if (count >= MAX_ATTEMPTS) {
                    map.put("bannedUntil", now + BAN_MS);
                    map.put("count", 0L);
                    map.put("firstAt", now);
                }

                currentData.setValue(map);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                // no-op
            }
        });
    }

    public void clearAttempts(String email) {
        String key = safeKey(email);
        securityRoot.child("login_attempts").child(key).removeValue();
    }

    private String safeKey(String email) {
        if (email == null) return "unknown";
        // Firebase keys cannot contain . # $ [ ] /
        return email.trim().toLowerCase()
                .replace(".", ",")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "(")
                .replace("]", ")")
                .replace("/", "_");
    }
}

