package com.example.myapplication.utils;

import android.content.Context;
import android.util.Log;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.entity.PlaceEntity;
import com.example.myapplication.model.EntertainmentPlace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Оркестратор: Overpass -> (кеш Room) -> Wikidata/Wikipedia images -> сохранить.
 */
public class OsmPoiRepository {

    private static final String TAG = "OsmPoiRepository";
    private final OsmOverpassPoiSearchHelper overpass = new OsmOverpassPoiSearchHelper();
    private final WikiMediaImageHelper wiki = new WikiMediaImageHelper();
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onResult(List<EntertainmentPlace> places);
    }

    public OsmPoiRepository(Context context) {
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    public void searchAround(double lat, double lon, int radiusMeters, Callback callback) {
        executor.execute(() -> {
            try {
                final int desired = 10;
                final int[] radii = new int[] { radiusMeters, Math.max(radiusMeters, 30000), 45000, 60000 };
                List<EntertainmentPlace> places = new ArrayList<>();

                for (int r : radii) {
                    List<EntertainmentPlace> attempt = loadAndEnrich(lat, lon, r);
                    List<EntertainmentPlace> onlyReal = filterRealPhotos(attempt);
                    if (onlyReal.size() >= desired) {
                        places = onlyReal;
                        break;
                    }
                    // keep best we have so far
                    if (onlyReal.size() > places.size()) {
                        places = onlyReal;
                    }
                }

                // Note: we intentionally do NOT fallback to static maps here.

                // Save to Room as cache as well (same table as favorites; harmless)
                long now = System.currentTimeMillis();
                List<PlaceEntity> entities = new ArrayList<>();
                for (EntertainmentPlace p : places) {
                    if (p == null || p.getId() == null || p.getName() == null) continue;
                    entities.add(new PlaceEntity(
                            p.getId(),
                            p.getName(),
                            p.getDescription(),
                            p.getLat(),
                            p.getLon(),
                            p.getImageUrl(),
                            p.getAddress(),
                            p.getOpeningHours(),
                            p.getWebsite(),
                            p.getPhone(),
                            p.getWikidataId(),
                            p.getWikipediaTag(),
                            now
                    ));
                }
                try {
                    db.placeDao().upsertAll(entities);
                } catch (Exception e) {
                    Log.w(TAG, "Room cache save failed", e);
                }

                if (callback != null) callback.onResult(places);
            } catch (Exception e) {
                Log.e(TAG, "searchAround failed", e);
                if (callback != null) callback.onResult(Collections.emptyList());
            }
        });
    }

    private List<EntertainmentPlace> loadAndEnrich(double lat, double lon, int radiusMeters) {
        List<EntertainmentPlace> result = new ArrayList<>();
        // Ask for more candidates to increase chance of real photos.
        result.addAll(overpass.search(lat, lon, radiusMeters, 120, OsmOverpassPoiSearchHelper.Category.SIGHTS));
        result.addAll(overpass.search(lat, lon, radiusMeters, 120, OsmOverpassPoiSearchHelper.Category.PARKS));
        result.addAll(overpass.search(lat, lon, radiusMeters, 120, OsmOverpassPoiSearchHelper.Category.FOOD));

        // Merge by id, prefer first (sights/parks/food order)
        Map<String, EntertainmentPlace> merged = new HashMap<>();
        for (EntertainmentPlace p : result) {
            if (p == null || p.getId() == null) continue;
            if (!merged.containsKey(p.getId())) merged.put(p.getId(), p);
        }
        List<EntertainmentPlace> places = new ArrayList<>(merged.values());

        // Normalize OSM image tag formats (often "File:Something.jpg" or Commons "File:" pages)
        for (EntertainmentPlace p : places) {
            if (p == null) continue;
            String normalized = normalizeOsmImage(p.getImageUrl(), 900);
            if (normalized != null) {
                p.setImageUrl(normalized);
            }
        }

        // Enrich with Wikidata/Wikipedia images if missing or non-real
        for (EntertainmentPlace p : places) {
            if (p == null) continue;
            if (isRealPhotoUrl(p.getImageUrl())) continue;
            String img = wiki.findImageUrl(p.getWikidataId(), p.getWikipediaTag(), 900);
            if (img != null && isRealPhotoUrl(img)) {
                p.setImageUrl(img);
            }
        }
        return places;
    }

    private List<EntertainmentPlace> filterRealPhotos(List<EntertainmentPlace> input) {
        List<EntertainmentPlace> out = new ArrayList<>();
        if (input == null) return out;
        for (EntertainmentPlace p : input) {
            if (p != null && isRealPhotoUrl(p.getImageUrl())) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean isRealPhotoUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        if (!u.startsWith("http")) return false;
        // Reject static map previews
        if (u.contains("static-maps.yandex.ru")) return false;
        return true;
    }

    private String normalizeOsmImage(String raw, int widthPx) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Common OSM formats:
        // - "File:Example.jpg"
        // - "https://commons.wikimedia.org/wiki/File:Example.jpg"
        // - "https://commons.wikimedia.org/wiki/Special:FilePath/Example.jpg"
        if (s.regionMatches(true, 0, "file:", 0, 5)) {
            String file = s.substring(5).trim();
            if (file.isEmpty()) return null;
            return "https://commons.wikimedia.org/wiki/Special:FilePath/" + android.net.Uri.encode(file.replace(' ', '_'))
                    + "?width=" + Math.max(320, widthPx);
        }

        String lower = s.toLowerCase();
        if (lower.startsWith("https://commons.wikimedia.org/wiki/file:") || lower.startsWith("http://commons.wikimedia.org/wiki/file:")) {
            int idx = s.indexOf("File:");
            if (idx >= 0) {
                String file = s.substring(idx + 5).trim();
                if (!file.isEmpty()) {
                    return "https://commons.wikimedia.org/wiki/Special:FilePath/" + android.net.Uri.encode(file.replace(' ', '_'))
                            + "?width=" + Math.max(320, widthPx);
                }
            }
        }

        // Already a URL (keep as-is)
        if (lower.startsWith("http")) return s;

        return null;
    }
}

