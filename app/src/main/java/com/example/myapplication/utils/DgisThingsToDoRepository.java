package com.example.myapplication.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.example.myapplication.model.EntertainmentPlace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * "Чем заняться" через 2GIS Places API.
 */
public class DgisThingsToDoRepository {

    private static final String TAG = "DgisThingsToDo";
    private static final String BASE = "https://catalog.api.2gis.com/3.0/items";
    private static final String META_KEY = "dgis.apikey";

    private final OkHttpClient client;
    private final ExecutorService ioPool;
    private final String apiKey;

    public interface Callback {
        void onSuccess(List<EntertainmentPlace> places);
        void onError(String message);
    }

    public DgisThingsToDoRepository(Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(14, TimeUnit.SECONDS)
                .build();
        this.ioPool = Executors.newSingleThreadExecutor();
        this.apiKey = readApiKey(context);
    }

    public void loadTop10(double lat, double lon, Callback callback) {
        ioPool.execute(() -> {
            try {
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    if (callback != null) callback.onError("Не задан API ключ 2GIS");
                    return;
                }

                List<EntertainmentPlace> collected = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                // 2GIS demo keys may limit page_size=10, so we page by categories.
                String[] queries = new String[]{
                        "достопримечательности",
                        "музей",
                        "парк",
                        "театр",
                        "кафе"
                };
                int[] radii = new int[]{20000, 30000, 45000};

                for (int radius : radii) {
                    for (String q : queries) {
                        if (collected.size() >= 10) break;
                        List<EntertainmentPlace> part = searchOneQuery(lat, lon, radius, q);
                        for (EntertainmentPlace p : part) {
                            if (p == null || p.getId() == null) continue;
                            if (!seen.add(p.getId())) continue;
                            collected.add(p);
                            if (collected.size() >= 10) break;
                        }
                    }
                    if (collected.size() >= 10) break;
                }

                // final sort by distance
                collected.sort((a, b) -> Double.compare(distanceKmSafe(lat, lon, a), distanceKmSafe(lat, lon, b)));
                if (collected.size() > 10) {
                    collected = new ArrayList<>(collected.subList(0, 10));
                }

                if (callback != null) callback.onSuccess(collected);
            } catch (Exception e) {
                Log.e(TAG, "loadTop10 failed", e);
                if (callback != null) callback.onError("Ошибка 2GIS: " + e.getMessage());
            }
        });
    }

    public void loadByTagsNear(double lat, double lon, List<String> tags, int limit, Callback callback) {
        ioPool.execute(() -> {
            try {
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    if (callback != null) callback.onError("Не задан API ключ 2GIS");
                    return;
                }
                List<String> safeTags = tags != null ? tags : new ArrayList<>();
                if (safeTags.isEmpty()) {
                    safeTags.add("достопримечательности");
                    safeTags.add("музей");
                    safeTags.add("парк");
                    safeTags.add("кафе");
                }

                Set<String> seen = new HashSet<>();
                List<EntertainmentPlace> out = new ArrayList<>();
                int[] radii = new int[]{20000, 30000, 45000};

                for (int radius : radii) {
                    for (String tag : safeTags) {
                        if (out.size() >= limit) break;
                        List<EntertainmentPlace> part = searchOneQuery(lat, lon, radius, tag);
                        for (EntertainmentPlace p : part) {
                            if (p == null || p.getId() == null) continue;
                            if (!seen.add(p.getId())) continue;
                            out.add(p);
                            if (out.size() >= limit) break;
                        }
                    }
                    if (out.size() >= limit) break;
                }

                out.sort((a, b) -> Double.compare(distanceKmSafe(lat, lon, a), distanceKmSafe(lat, lon, b)));
                if (out.size() > limit) {
                    out = new ArrayList<>(out.subList(0, limit));
                }
                if (callback != null) callback.onSuccess(out);
            } catch (Exception e) {
                if (callback != null) callback.onError("Ошибка 2GIS: " + e.getMessage());
            }
        });
    }

    private List<EntertainmentPlace> searchOneQuery(double lat, double lon, int radiusMeters, String query) {
        List<EntertainmentPlace> out = new ArrayList<>();
        try {
            String url = BASE
                    + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                    + "&type=branch"
                    + "&point=" + lon + "," + lat
                    + "&location=" + lon + "," + lat
                    + "&sort=distance"
                    + "&radius=" + radiusMeters
                    + "&has_photos=true"
                    + "&page_size=10"
                    + "&page=1"
                    + "&fields=items.point,items.full_address_name,items.schedule,items.reviews,items.rubrics"
                    + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());

            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return out;
                JSONObject root = new JSONObject(resp.body().string());
                JSONObject result = root.optJSONObject("result");
                if (result == null) return out;
                JSONArray items = result.optJSONArray("items");
                if (items == null) return out;

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "").trim();
                    String name = item.optString("name", "").trim();
                    if (id.isEmpty() || name.isEmpty()) continue;

                    JSONObject point = item.optJSONObject("point");
                    if (point == null) continue;
                    double plon = point.optDouble("lon", 0);
                    double plat = point.optDouble("lat", 0);
                    if (plat == 0 && plon == 0) continue;

                    String address = item.optString("full_address_name", "");
                    String desc = "";

                    JSONObject reviews = item.optJSONObject("reviews");
                    if (reviews != null) {
                        double rating = reviews.optDouble("general_rating", 0.0);
                        if (rating > 0) {
                            desc = "Рейтинг 2GIS: " + String.format(Locale.getDefault(), "%.1f", rating);
                        }
                    }

                    JSONObject schedule = item.optJSONObject("schedule");
                    if (schedule != null) {
                        String txt = schedule.optString("text", "");
                        if (!txt.isEmpty()) {
                            if (!desc.isEmpty()) desc += " • ";
                            desc += "Часы: " + txt;
                        }
                    }

                    String tags = extractRubrics(item.optJSONArray("rubrics"));

                    // 2GIS search API filter has_photos=true ensures photos exist,
                    // but direct photo URL is not exposed in standard response.
                    EntertainmentPlace p = new EntertainmentPlace(
                            "dgis_" + id,
                            name,
                            desc,
                            String.format(Locale.US, "%.6f", plat),
                            String.format(Locale.US, "%.6f", plon),
                            null,
                            address
                    );
                    p.setTags(tags);
                    out.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String extractRubrics(JSONArray rubrics) {
        if (rubrics == null || rubrics.length() == 0) return "";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < rubrics.length(); i++) {
            JSONObject r = rubrics.optJSONObject(i);
            if (r == null) continue;
            String n = r.optString("name", "").trim();
            if (!n.isEmpty()) names.add(n);
        }
        return String.join(", ", names);
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

