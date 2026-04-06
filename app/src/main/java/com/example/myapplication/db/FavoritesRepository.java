package com.example.myapplication.db;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.myapplication.db.entity.FavoriteEntity;
import com.example.myapplication.db.entity.PlaceEntity;
import com.example.myapplication.model.EntertainmentPlace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FavoritesRepository {

    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback<T> {
        void onResult(@Nullable T value);
    }

    public FavoritesRepository(Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    public void setFavorite(long userId, EntertainmentPlace place, boolean favorite) {
        if (userId <= 0 || place == null || place.getId() == null) return;
        executor.execute(() -> {
            PlaceEntity p = toEntity(place);
            db.placeDao().upsert(p);
            if (favorite) {
                db.favoriteDao().add(new FavoriteEntity(userId, p.id, System.currentTimeMillis()));
            } else {
                db.favoriteDao().remove(userId, p.id);
            }
        });
    }

    public void isFavorite(long userId, String placeId, Callback<Boolean> callback) {
        executor.execute(() -> {
            boolean res = userId > 0 && placeId != null && db.favoriteDao().isFavorite(userId, placeId);
            if (callback != null) callback.onResult(res);
        });
    }

    public void loadFavorites(long userId, Callback<List<EntertainmentPlace>> callback) {
        executor.execute(() -> {
            List<PlaceEntity> entities = db.favoriteDao().getFavoritePlaces(userId);
            List<EntertainmentPlace> places = new ArrayList<>();
            if (entities != null) {
                for (PlaceEntity e : entities) places.add(fromEntity(e));
            }
            if (callback != null) callback.onResult(places);
        });
    }

    public void searchFavorites(long userId, String q, Callback<List<EntertainmentPlace>> callback) {
        executor.execute(() -> {
            String query = q == null ? "" : q.trim();
            List<PlaceEntity> entities = query.isEmpty()
                    ? db.favoriteDao().getFavoritePlaces(userId)
                    : db.favoriteDao().searchFavorites(userId, query);
            List<EntertainmentPlace> places = new ArrayList<>();
            if (entities != null) {
                for (PlaceEntity e : entities) places.add(fromEntity(e));
            }
            if (callback != null) callback.onResult(places);
        });
    }

    public void getFavoriteIds(long userId, Callback<Set<String>> callback) {
        executor.execute(() -> {
            List<PlaceEntity> entities = db.favoriteDao().getFavoritePlaces(userId);
            Set<String> ids = new HashSet<>();
            if (entities != null) {
                for (PlaceEntity e : entities) ids.add(e.id);
            }
            if (callback != null) callback.onResult(ids);
        });
    }

    private PlaceEntity toEntity(EntertainmentPlace place) {
        String id = place.getId();
        String name = place.getName() == null ? "" : place.getName();
        return new PlaceEntity(
                id,
                name,
                place.getDescription(),
                place.getLat(),
                place.getLon(),
                place.getImageUrl(),
                place.getAddress(),
                place.getOpeningHours(),
                place.getWebsite(),
                place.getPhone(),
                place.getWikidataId(),
                place.getWikipediaTag(),
                System.currentTimeMillis()
        );
    }

    private EntertainmentPlace fromEntity(PlaceEntity e) {
        EntertainmentPlace p = new EntertainmentPlace(
                e.id,
                e.name,
                e.description,
                e.lat,
                e.lon,
                e.imageUrl,
                e.address
        );
        p.setOpeningHours(e.openingHours);
        p.setWebsite(e.website);
        p.setPhone(e.phone);
        p.setWikidataId(e.wikidataId);
        p.setWikipediaTag(e.wikipediaTag);
        return p;
    }
}

