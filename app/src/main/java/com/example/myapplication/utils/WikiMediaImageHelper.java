package com.example.myapplication.utils;

import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Достаёт картинку для места через Wikidata (P18) или Wikipedia summary thumbnail.
 * Все источники бесплатные (но нужно кешировать и не долбить часто).
 */
public class WikiMediaImageHelper {

    private static final String TAG = "WikiMediaImage";
    private final OkHttpClient client;

    public WikiMediaImageHelper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    public String findImageUrl(String wikidataId, String wikipediaTag, int widthPx) {
        // Prefer Wikidata if available
        String fromWikidata = findFromWikidata(wikidataId, widthPx);
        if (fromWikidata != null) return fromWikidata;

        return findFromWikipedia(wikipediaTag, widthPx);
    }

    private String findFromWikidata(String wikidataId, int widthPx) {
        if (wikidataId == null || wikidataId.trim().isEmpty()) return null;
        String q = wikidataId.trim().toUpperCase(Locale.ROOT);
        if (!q.startsWith("Q")) return null;

        try {
            String url = "https://www.wikidata.org/wiki/Special:EntityData/" + Uri.encode(q) + ".json";
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "MyApplication2/1.0 (Android)")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                JSONObject root = new JSONObject(resp.body().string());
                JSONObject entities = root.optJSONObject("entities");
                if (entities == null) return null;
                JSONObject entity = entities.optJSONObject(q);
                if (entity == null) return null;
                JSONObject claims = entity.optJSONObject("claims");
                if (claims == null) return null;
                JSONArray p18 = claims.optJSONArray("P18");
                if (p18 == null || p18.length() == 0) return null;
                JSONObject claim0 = p18.optJSONObject(0);
                if (claim0 == null) return null;
                JSONObject mainsnak = claim0.optJSONObject("mainsnak");
                if (mainsnak == null) return null;
                JSONObject datavalue = mainsnak.optJSONObject("datavalue");
                if (datavalue == null) return null;
                String fileName = datavalue.optString("value", null);
                if (fileName == null || fileName.trim().isEmpty()) return null;
                return commonsFilePathUrl(fileName, widthPx);
            }
        } catch (Exception e) {
            Log.w(TAG, "Wikidata image failed for " + wikidataId, e);
            return null;
        }
    }

    private String findFromWikipedia(String wikipediaTag, int widthPx) {
        // wikipedia tag format in OSM: "lang:Title" (e.g., "ru:Третьяковская_галерея")
        if (wikipediaTag == null || wikipediaTag.trim().isEmpty()) return null;
        String s = wikipediaTag.trim();
        int idx = s.indexOf(':');
        if (idx <= 0 || idx >= s.length() - 1) return null;
        String lang = s.substring(0, idx);
        String title = s.substring(idx + 1);

        // Use REST summary which often has thumbnail.
        try {
            // Title should be URL-encoded (spaces etc.)
            String encTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name());
            String url = "https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + encTitle;
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "MyApplication2/1.0 (Android)")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                JSONObject root = new JSONObject(resp.body().string());
                JSONObject thumb = root.optJSONObject("thumbnail");
                if (thumb == null) return null;
                String src = thumb.optString("source", null);
                if (src == null || src.trim().isEmpty()) return null;
                // Wikipedia already returns a sized URL; accept it.
                return src;
            }
        } catch (Exception e) {
            Log.w(TAG, "Wikipedia thumbnail failed for " + wikipediaTag, e);
            return null;
        }
    }

    private String commonsFilePathUrl(String fileName, int widthPx) {
        // Special:FilePath supports width= query param.
        // Example: https://commons.wikimedia.org/wiki/Special:FilePath/Foo%20bar.jpg?width=800
        String safe = fileName.trim().replace(' ', '_');
        String enc = Uri.encode(safe);
        return "https://commons.wikimedia.org/wiki/Special:FilePath/" + enc + "?width=" + Math.max(320, widthPx);
    }
}

