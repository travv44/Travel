package com.example.myapplication.utils;

import android.util.Log;

import com.example.myapplication.model.EntertainmentPlace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Бесплатный поиск POI через Overpass API (OpenStreetMap).
 * Возвращает места с opening_hours / website / phone / wikidata / wikipedia, если они есть в OSM.
 */
public class OsmOverpassPoiSearchHelper {

    private static final String TAG = "OsmOverpass";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final MediaType MEDIA_TEXT = MediaType.parse("text/plain; charset=utf-8");

    private final OkHttpClient client;

    public OsmOverpassPoiSearchHelper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(18, TimeUnit.SECONDS)
                .build();
    }

    public enum Category {
        SIGHTS,
        FOOD,
        PARKS
    }

    public List<EntertainmentPlace> search(double lat, double lon, int radiusMeters, int limit, Category category) {
        try {
            String q = buildQuery(lat, lon, radiusMeters, limit, category);
            RequestBody body = RequestBody.create(q, MEDIA_TEXT);
            Request request = new Request.Builder().url(OVERPASS_URL).post(body).build();
            try (Response resp = client.newCall(request).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    Log.w(TAG, "Overpass not successful: " + resp.code());
                    return new ArrayList<>();
                }
                String json = resp.body().string();
                return parseOverpass(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Overpass search failed", e);
            return new ArrayList<>();
        }
    }

    private String buildQuery(double lat, double lon, int radiusMeters, int limit, Category category) {
        // nwr = node/way/relation. For way/relation we ask for center.
        String filters = buildTagFilters(category);
        return "[out:json][timeout:25];"
                + "("
                + "nwr(around:" + radiusMeters + "," + lat + "," + lon + ")" + filters + ";"
                + ");"
                + "out tags center " + limit + ";";
    }

    private String buildTagFilters(Category category) {
        // Keep it simple but useful. You can expand later.
        if (category == Category.FOOD) {
            return "[amenity~\"^(cafe|restaurant|fast_food|bar|pub)$\"]";
        }
        if (category == Category.PARKS) {
            return "[leisure~\"^(park|garden|nature_reserve)$\"]";
        }
        // SIGHTS
        return "[tourism~\"^(attraction|museum|gallery|viewpoint|theme_park|zoo)$\"][name]";
    }

    private List<EntertainmentPlace> parseOverpass(String json) {
        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            Log.w(TAG, "Invalid Overpass JSON", e);
            return new ArrayList<>();
        }
        JSONArray elements = root.optJSONArray("elements");
        if (elements == null) return new ArrayList<>();

        // Dedup by id (type+id)
        Set<String> seen = new HashSet<>();
        List<EntertainmentPlace> out = new ArrayList<>();

        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.optJSONObject(i);
            if (el == null) continue;

            String type = el.optString("type", "");
            long id = el.optLong("id", -1);
            if (id <= 0 || type.isEmpty()) continue;
            String key = type + ":" + id;
            if (!seen.add(key)) continue;

            JSONObject tags = el.optJSONObject("tags");
            if (tags == null) continue;

            String name = tags.optString("name", "").trim();
            if (name.isEmpty()) continue;

            Double lat = null;
            Double lon = null;
            if (el.has("lat") && el.has("lon")) {
                lat = el.optDouble("lat");
                lon = el.optDouble("lon");
            } else {
                JSONObject center = el.optJSONObject("center");
                if (center != null) {
                    lat = center.optDouble("lat");
                    lon = center.optDouble("lon");
                }
            }
            if (lat == null || lon == null) continue;

            Map<String, String> t = flattenTags(tags);

            EntertainmentPlace p = new EntertainmentPlace(
                    "osm_" + type + "_" + id,
                    name,
                    buildShortDescription(t),
                    String.format(Locale.US, "%.6f", lat),
                    String.format(Locale.US, "%.6f", lon),
                    firstNonEmpty(t.get("image"), t.get("contact:image")),
                    buildAddress(t)
            );
            p.setOpeningHours(t.get("opening_hours"));
            p.setWebsite(firstNonEmpty(t.get("contact:website"), t.get("website")));
            p.setPhone(firstNonEmpty(t.get("contact:phone"), t.get("phone")));
            p.setWikidataId(t.get("wikidata"));
            p.setWikipediaTag(t.get("wikipedia"));

            out.add(p);
        }
        return out;
    }

    private Map<String, String> flattenTags(JSONObject tags) {
        Map<String, String> out = new HashMap<>();
        JSONArray names = tags.names();
        if (names == null) return out;
        for (int i = 0; i < names.length(); i++) {
            String k = names.optString(i, null);
            if (k == null) continue;
            String v = tags.optString(k, null);
            if (v != null) out.put(k, v);
        }
        return out;
    }

    private String buildShortDescription(Map<String, String> tags) {
        // Prefer a short category hint.
        String tourism = tags.get("tourism");
        String amenity = tags.get("amenity");
        String leisure = tags.get("leisure");
        String historic = tags.get("historic");

        String kind = firstNonEmpty(tourism, amenity, leisure, historic);
        if (kind == null) return "";
        return kind.replace('_', ' ');
    }

    private String buildAddress(Map<String, String> tags) {
        String full = tags.get("addr:full");
        if (full != null && !full.trim().isEmpty()) return full.trim();

        String street = tags.get("addr:street");
        String house = tags.get("addr:housenumber");
        String city = tags.get("addr:city");
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.trim().isEmpty()) sb.append(street.trim());
        if (house != null && !house.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(house.trim());
        }
        if (city != null && !city.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append(city.trim());
        }
        return sb.toString();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }
}

