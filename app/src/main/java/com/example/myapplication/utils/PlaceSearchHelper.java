package com.example.myapplication.utils;

import android.util.Log;
import com.example.myapplication.model.PlaceSuggestion;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PlaceSearchHelper {
    private static final String TAG = "PlaceSearchHelper";
    private static final String YANDEX_GEOCODER_API = "https://geocode-maps.yandex.ru/1.x/";
    private static final String YANDEX_API_KEY = "34afc545-e0c4-4be9-a15c-5372f2b85691";
    private final OkHttpClient client;

    public PlaceSearchHelper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public List<PlaceSuggestion> searchPlaces(String query) {
        List<PlaceSuggestion> suggestions = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return suggestions;
        }

        try {
            String url = YANDEX_GEOCODER_API + "?apikey=" + YANDEX_API_KEY + 
                        "&geocode=" + java.net.URLEncoder.encode(query, "UTF-8") + 
                        "&format=json&results=10&lang=ru_RU";
            
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    suggestions = parseGeocoderResponse(jsonResponse);
                } else {
                    Log.e(TAG, "Geocoder API error: " + response.code());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error searching places", e);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing response", e);
        }

        return suggestions;
    }

    private List<PlaceSuggestion> parseGeocoderResponse(String jsonResponse) {
        List<PlaceSuggestion> suggestions = new ArrayList<>();
        
        try {
            JSONObject json = new JSONObject(jsonResponse);
            JSONObject response = json.getJSONObject("response");
            JSONObject geoObjectCollection = response.getJSONObject("GeoObjectCollection");
            JSONArray featureMembers = geoObjectCollection.getJSONArray("featureMember");

            for (int i = 0; i < featureMembers.length(); i++) {
                JSONObject featureMember = featureMembers.getJSONObject(i);
                JSONObject geoObject = featureMember.getJSONObject("GeoObject");
                
                String name = geoObject.getString("name");
                String description = geoObject.optString("description", "");
                
                JSONObject point = geoObject.getJSONObject("Point");
                String pos = point.getString("pos");
                String[] coords = pos.split(" ");
                String lon = coords[0];
                String lat = coords[1];

                suggestions.add(new PlaceSuggestion(name, description, lat, lon));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing geocoder response", e);
        }

        return suggestions;
    }
}

