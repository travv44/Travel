package com.example.myapplication.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.entity.PlaceEntity;
import com.example.myapplication.model.EntertainmentPlace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OpenTripMap (бесплатный API) — отдаёт POI, часто с фото (preview.source) и wiki.
 * Мы возвращаем только места с РЕАЛЬНЫМИ фото.
 */
public class OpenTripMapRepository {

    private static final String TAG = "OpenTripMapRepo";
    private static final String BASE = "https://api.opentripmap.com/0.1/en/places/";
    private static final String META_KEY = "opentripmap.apikey";

    private final OkHttpClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String apiKey;
    private final AppDatabase db;

    public interface Callback {
        void onResult(List<EntertainmentPlace> places);
        void onError(String message);
    }

    public OpenTripMapRepository(Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(18, TimeUnit.SECONDS)
                .build();
        this.apiKey = readApiKey(context);
        this.db = AppDatabase.getInstance(context.getApplicationContext());
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.trim().isEmpty() && !"PUT_YOUR_OPENTRIPMAP_KEY_HERE".equals(apiKey.trim());
    }

    public void searchTop10WithPhotos(double lat, double lon, Callback callback) {
        executor.execute(() -> {
            if (!hasKey()) {
                if (callback != null) callback.onError("OpenTripMap API key not set");
                return;
            }
            try {
                // Expand radius progressively until we collect enough with photos.
                int[] radii = new int[]{20000, 30000, 45000, 60000, 80000};
                int[] limits = new int[]{60, 90, 120, 160, 220};

                List<EntertainmentPlace> best = new ArrayList<>();
                for (int i = 0; i < radii.length; i++) {
                    List<EntertainmentPlace> got = fetchWithPhotos(lat, lon, radii[i], limits[Math.min(i, limits.length - 1)]);
                    if (got.size() > best.size()) best = got;
                    if (best.size() >= 10) break;
                }

                // Sort by distance and take 10
                Collections.sort(best, (a, b) -> Double.compare(distanceKmSafe(lat, lon, a), distanceKmSafe(lat, lon, b)));
                if (best.size() > 10) best = new ArrayList<>(best.subList(0, 10));

                // Cache in Room (optional)
                saveCache(best);

                if (callback != null) callback.onResult(best);
            } catch (Exception e) {
                Log.e(TAG, "searchTop10WithPhotos failed", e);
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "OpenTripMap error");
            }
        });
    }

    private List<EntertainmentPlace> fetchWithPhotos(double lat, double lon, int radiusMeters, int limit) throws Exception {
        // We focus on "interesting_places" and related kinds.
        String kinds = "interesting_places,cultural,architecture,historic,museums,foods,amusements,natural,parks";
        String url = BASE + "radius"
                + "?radius=" + radiusMeters
                + "&lon=" + lon
                + "&lat=" + lat
                + "&kinds=" + URLEncoder.encode(kinds, StandardCharsets.UTF_8.name())
                + "&limit=" + limit
                + "&format=json"
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());

        JSONArray list = httpJsonArray(url);
        if (list == null) return new ArrayList<>();

        // Collect XIDs (dedupe)
        Set<String> xids = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String xid = o.optString("xid", null);
            if (xid != null && !xid.trim().isEmpty()) xids.add(xid.trim());
        }

        // Fetch details for each xid until enough photos
        List<EntertainmentPlace> out = new ArrayList<>();
        Map<String, EntertainmentPlace> merged = new HashMap<>();
        for (String xid : xids) {
            if (out.size() >= 14) {
                // keep extra buffer for distance sort
                break;
            }
            EntertainmentPlace p = fetchDetails(xid);
            if (p == null) continue;
            if (!isRealPhotoUrl(p.getImageUrl())) continue;
            if (!merged.containsKey(p.getId())) {
                merged.put(p.getId(), p);
                out.add(p);
            }
        }
        return out;
    }

    private EntertainmentPlace fetchDetails(String xid) {
        try {
            String url = BASE + "xid/" + URLEncoder.encode(xid, StandardCharsets.UTF_8.name())
                    + "?apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());
            JSONObject root = httpJsonObject(url);
            if (root == null) return null;

            String name = root.optString("name", "").trim();
            if (name.isEmpty()) return null;

            JSONObject point = root.optJSONObject("point");
            if (point == null) return null;
            double lat = point.optDouble("lat", 0);
            double lon = point.optDouble("lon", 0);
            if (lat == 0 && lon == 0) return null;

            String imageUrl = null;
            JSONObject preview = root.optJSONObject("preview");
            if (preview != null) {
                imageUrl = preview.optString("source", null);
            }

            String desc = "";
            JSONObject wikipediaExtracts = root.optJSONObject("wikipedia_extracts");
            if (wikipediaExtracts != null) {
                desc = wikipediaExtracts.optString("text", "");
            }
            if (desc == null) desc = "";

            String address = "";
            JSONObject addr = root.optJSONObject("address");
            if (addr != null) {
                String road = addr.optString("road", "");
                String house = addr.optString("house_number", "");
                String city = addr.optString("city", "");
                StringBuilder sb = new StringBuilder();
                if (!road.isEmpty()) sb.append(road);
                if (!house.isEmpty()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(house);
                }
                if (!city.isEmpty()) {
                    if (sb.length() > 0) sb.append(" — ");
                    sb.append(city);
                }
                address = sb.toString();
            }

            EntertainmentPlace p = new EntertainmentPlace(
                    "otm_" + xid,
                    name,
                    desc,
                    String.format(Locale.US, "%.6f", lat),
                    String.format(Locale.US, "%.6f", lon),
                    imageUrl,
                    address
            );
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private JSONArray httpJsonArray(String url) {
        try {
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                String body = resp.body().string();
                return new JSONArray(body);
            }
        } catch (Exception e) {
            Log.w(TAG, "httpJsonArray failed", e);
            return null;
        }
    }

    private JSONObject httpJsonObject(String url) {
        try {
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                String body = resp.body().string();
                return new JSONObject(body);
            }
        } catch (Exception e) {
            Log.w(TAG, "httpJsonObject failed", e);
            return null;
        }
    }

    private boolean isRealPhotoUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.ROOT);
        if (!u.startsWith("http")) return false;
        if (u.contains("static-maps.yandex.ru")) return false;
        return true;
    }

    private double distanceKmSafe(double centerLat, double centerLon, EntertainmentPlace p) {
        try {
            if (p == null || p.getLat() == null || p.getLon() == null) return 1e9;
            double la = Double.parseDouble(p.getLat().replace(',', '.').trim());
            double lo = Double.parseDouble(p.getLon().replace(',', '.').trim());
            return haversineKm(centerLat, centerLon, la, lo);
        } catch (Exception e) {
            return 1e9;
        }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void saveCache(List<EntertainmentPlace> places) {
        try {
            long now = System.currentTimeMillis();
            List<PlaceEntity> entities = new ArrayList<>();
            if (places != null) {
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
            }
            db.placeDao().upsertAll(entities);
        } catch (Exception e) {
            Log.w(TAG, "saveCache failed", e);
        }
    }

    private String readApiKey(Context context) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            if (ai.metaData == null) return null;
            Object v = ai.metaData.get(META_KEY);
            return v != null ? String.valueOf(v) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

