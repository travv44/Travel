package com.example.myapplication.utils;

import android.util.Log;
import com.example.myapplication.model.EntertainmentPlace;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EntertainmentSearchHelper {
    private static final String TAG = "EntertainmentSearchHelper";
    private static final String YANDEX_GEOCODER_API = "https://geocode-maps.yandex.ru/1.x/";
    private static final String YANDEX_API_KEY = "34afc545-e0c4-4be9-a15c-5372f2b85691";
    private final OkHttpClient client;

    public EntertainmentSearchHelper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public List<EntertainmentPlace> searchEntertainment(String lat, String lon) {
        List<EntertainmentPlace> places = new ArrayList<>();
        
        if (lat == null || lon == null || lat.trim().isEmpty() || lon.trim().isEmpty()) {
            return places;
        }

        // Поиск достопримечательностей и интересных мест поблизости
        // Используем поиск по координатам с различными категориями
        String[] queries = {
            "достопримечательности",
            "музей",
            "парк",
            "театр",
            "кинотеатр",
            "ресторан",
            "кафе",
            "галерея",
            "памятник",
            "собор",
            "церковь"
        };

        // Сначала получаем название города/места по координатам
        String locationName = getLocationName(lat, lon);
        Log.d(TAG, "Location name: " + locationName);

        for (String query : queries) {
            try {
                // Пробуем разные форматы поиска
                String[] searchFormats = {
                    locationName != null ? locationName + " " + query : null,
                    query + " " + locationName,
                    query
                };

                for (String searchQuery : searchFormats) {
                    if (searchQuery == null) continue;
                    
                    String url = YANDEX_GEOCODER_API + "?apikey=" + YANDEX_API_KEY + 
                                "&geocode=" + java.net.URLEncoder.encode(searchQuery, "UTF-8") + 
                                "&format=json&results=10&lang=ru_RU";
                    
                    Log.d(TAG, "Searching: " + searchQuery);
                    
                    Request request = new Request.Builder()
                            .url(url)
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String jsonResponse = response.body().string();
                            List<EntertainmentPlace> foundPlaces = parseGeocoderResponse(jsonResponse, lat, lon);
                            if (!foundPlaces.isEmpty()) {
                                Log.d(TAG, "Found " + foundPlaces.size() + " places for query: " + searchQuery);
                                places.addAll(foundPlaces);
                                break; // Если нашли результаты, переходим к следующей категории
                            }
                        } else {
                            Log.w(TAG, "API response not successful: " + response.code() + " for query: " + searchQuery);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error searching entertainment: " + query, e);
            } catch (Exception e) {
                Log.e(TAG, "Error encoding query: " + query, e);
            }
        }

        // Если не нашли через поиск, попробуем поиск по городу/месту
        if (places.isEmpty()) {
            places = searchByLocation(lat, lon);
        }

        // Ограничиваем количество результатов и удаляем дубликаты
        List<EntertainmentPlace> uniquePlaces = removeDuplicates(places);
        return uniquePlaces.size() > 20 ? uniquePlaces.subList(0, 20) : uniquePlaces;
    }

    private String getLocationName(String lat, String lon) {
        try {
            // Обратный геокодинг для получения названия места
            String reverseGeocodeUrl = YANDEX_GEOCODER_API + "?apikey=" + YANDEX_API_KEY + 
                    "&geocode=" + lon + "," + lat + 
                    "&format=json&results=1&lang=ru_RU";
            
            Request request = new Request.Builder()
                    .url(reverseGeocodeUrl)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    return extractLocationName(jsonResponse);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting location name", e);
        }
        return null;
    }

    private String extractLocationName(String jsonResponse) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            JSONObject response = json.getJSONObject("response");
            JSONObject geoObjectCollection = response.getJSONObject("GeoObjectCollection");
            JSONArray featureMembers = geoObjectCollection.getJSONArray("featureMember");
            
            if (featureMembers.length() > 0) {
                JSONObject featureMember = featureMembers.getJSONObject(0);
                JSONObject geoObject = featureMember.getJSONObject("GeoObject");
                
                // Пробуем получить название города из метаданных
                JSONObject metaDataProperty = geoObject.optJSONObject("metaDataProperty");
                if (metaDataProperty != null) {
                    JSONObject geocoderMetaData = metaDataProperty.optJSONObject("GeocoderMetaData");
                    if (geocoderMetaData != null) {
                        JSONObject address = geocoderMetaData.optJSONObject("Address");
                        if (address != null) {
                            JSONObject components = address.optJSONObject("Components");
                            if (components != null) {
                                JSONArray componentsArray = components.optJSONArray("Component");
                                if (componentsArray != null) {
                                    for (int i = 0; i < componentsArray.length(); i++) {
                                        JSONObject component = componentsArray.getJSONObject(i);
                                        String kind = component.optString("kind", "");
                                        if ("locality".equals(kind) || "city".equals(kind)) {
                                            return component.optString("name", "");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Если не нашли в метаданных, используем название объекта
                return geoObject.getString("name");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting location name", e);
        }
        return null;
    }

    private List<EntertainmentPlace> searchByLocation(String lat, String lon) {
        List<EntertainmentPlace> places = new ArrayList<>();
        String locationName = getLocationName(lat, lon);
        
        if (locationName == null || locationName.isEmpty()) {
            return places;
        }
        
        // Ищем достопримечательности в этом месте
        String[] searchTerms = {"достопримечательности", "музеи", "парки", "театры", "памятники"};
        for (String term : searchTerms) {
            try {
                String searchUrl = YANDEX_GEOCODER_API + "?apikey=" + YANDEX_API_KEY + 
                        "&geocode=" + java.net.URLEncoder.encode(locationName + " " + term, "UTF-8") + 
                        "&format=json&results=5&lang=ru_RU";
                
                Request searchRequest = new Request.Builder()
                        .url(searchUrl)
                        .build();
                
                try (Response searchResponse = client.newCall(searchRequest).execute()) {
                    if (searchResponse.isSuccessful() && searchResponse.body() != null) {
                        String searchJson = searchResponse.body().string();
                        List<EntertainmentPlace> found = parseGeocoderResponse(searchJson, lat, lon);
                        if (!found.isEmpty()) {
                            places.addAll(found);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error searching by location term: " + term, e);
            }
        }
        
        return places;
    }

    private List<EntertainmentPlace> parseGeocoderResponse(String jsonResponse, String centerLat, String centerLon) {
        List<EntertainmentPlace> places = new ArrayList<>();
        
        try {
            JSONObject json = new JSONObject(jsonResponse);
            JSONObject response = json.getJSONObject("response");
            JSONObject geoObjectCollection = response.getJSONObject("GeoObjectCollection");
            JSONArray featureMembers = geoObjectCollection.getJSONArray("featureMember");

            Log.d(TAG, "Found " + featureMembers.length() + " feature members");

            for (int i = 0; i < featureMembers.length(); i++) {
                try {
                    JSONObject featureMember = featureMembers.getJSONObject(i);
                    JSONObject geoObject = featureMember.getJSONObject("GeoObject");
                    
                    String name = geoObject.optString("name", "");
                    if (name.isEmpty()) {
                        continue; // Пропускаем объекты без названия
                    }
                    
                    String description = geoObject.optString("description", "");
                    
                    // Получаем адрес
                    String address = "";
                    JSONObject metaDataProperty = geoObject.optJSONObject("metaDataProperty");
                    if (metaDataProperty != null) {
                        JSONObject geocoderMetaData = metaDataProperty.optJSONObject("GeocoderMetaData");
                        if (geocoderMetaData != null) {
                            JSONObject addressDetails = geocoderMetaData.optJSONObject("Address");
                            if (addressDetails != null) {
                                address = addressDetails.optString("formatted", "");
                                if (address.isEmpty()) {
                                    // Пробуем собрать адрес из компонентов
                                    JSONObject components = addressDetails.optJSONObject("Components");
                                    if (components != null) {
                                        JSONArray componentsArray = components.optJSONArray("Component");
                                        if (componentsArray != null) {
                                            StringBuilder addrBuilder = new StringBuilder();
                                            for (int j = 0; j < componentsArray.length(); j++) {
                                                JSONObject component = componentsArray.getJSONObject(j);
                                                String componentName = component.optString("name", "");
                                                if (!componentName.isEmpty()) {
                                                    if (addrBuilder.length() > 0) {
                                                        addrBuilder.append(", ");
                                                    }
                                                    addrBuilder.append(componentName);
                                                }
                                            }
                                            address = addrBuilder.toString();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Получаем координаты
                    JSONObject point = geoObject.optJSONObject("Point");
                    if (point == null) {
                        continue; // Пропускаем объекты без координат
                    }
                    
                    String pos = point.optString("pos", "");
                    if (pos.isEmpty()) {
                        continue;
                    }
                    
                    String[] coords = pos.split(" ");
                    if (coords.length < 2) {
                        continue;
                    }
                    
                    String lon = coords[0];
                    String lat = coords[1];

                    // Проверяем, что координаты валидны
                    try {
                        double latDouble = Double.parseDouble(lat);
                        double lonDouble = Double.parseDouble(lon);
                        double centerLatDouble = Double.parseDouble(centerLat);
                        double centerLonDouble = Double.parseDouble(centerLon);
                        
                        // Фильтруем объекты, которые слишком далеко (более 50 км)
                        double distance = calculateDistance(latDouble, lonDouble, centerLatDouble, centerLonDouble);
                        if (distance > 50) {
                            continue; // Пропускаем слишком далекие объекты
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid coordinates: " + lat + ", " + lon);
                        continue;
                    }

                    // Генерируем URL изображения через Yandex Static API
                    String imageUrl = generateStaticMapUrl(lat, lon);

                    String id = name + "_" + lat + "_" + lon; // Более уникальный ID
                    places.add(new EntertainmentPlace(id, name, description, lat, lon, imageUrl, address));
                    Log.d(TAG, "Added place: " + name);
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing feature member " + i, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing geocoder response", e);
            Log.e(TAG, "Response: " + jsonResponse.substring(0, Math.min(500, jsonResponse.length())));
        }

        return places;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Формула гаверсинуса для расчета расстояния между двумя точками
        final int R = 6371; // Радиус Земли в километрах
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String generateStaticMapUrl(String lat, String lon) {
        // Генерируем URL для статической карты с меткой места
        return "https://static-maps.yandex.ru/1.x/?ll=" + lon + "," + lat + 
               "&z=15&size=400,300&pt=" + lon + "," + lat + ",pm2rdm&l=map";
    }

    private List<EntertainmentPlace> removeDuplicates(List<EntertainmentPlace> places) {
        List<EntertainmentPlace> uniquePlaces = new ArrayList<>();
        List<String> seenNames = new ArrayList<>();
        
        for (EntertainmentPlace place : places) {
            String name = place.getName().toLowerCase().trim();
            if (!seenNames.contains(name)) {
                seenNames.add(name);
                uniquePlaces.add(place);
            }
        }
        
        return uniquePlaces;
    }
}

