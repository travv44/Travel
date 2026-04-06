package com.example.myapplication.utils;

import android.util.Log;
import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.Trip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс для работы с Firebase Realtime Database
 * Обеспечивает загрузку и выгрузку данных о поездках и достопримечательностях
 */
public class FirebaseTripHelper {
    private static final String TAG = "FirebaseTripHelper";
    private static FirebaseTripHelper instance;
    private final DatabaseReference databaseRef;

    // Пути в базе данных
    private static final String USERS_PATH = "users";
    private static final String TRIPS_PATH = "trips";
    private static final String PLACES_PATH = "places";
    private static final String LOGIN_AUDIT_PATH = "loginAudit";

    private static final String DATABASE_URL = "https://nosql-562de-default-rtdb.firebaseio.com/";

    private FirebaseTripHelper() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(DATABASE_URL);
        databaseRef = database.getReference();
    }

    public static FirebaseTripHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseTripHelper();
        }
        return instance;
    }

    // ============================================
    // МЕТОДЫ ДЛЯ РАБОТЫ С ПОЕЗДКАМИ
    // ============================================

    /**
     * Сохранение поездки в Firebase
     * @param trip Поездка для сохранения
     * @param callback Callback для обработки результата
     */
    public void saveTrip(Trip trip, OnSaveCallback callback) {
        try {
            if (trip.getUserId() == null || trip.getUserId().isEmpty()) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    trip.setUserId(FirebaseAuth.getInstance().getCurrentUser().getUid());
                }
            }
            DatabaseReference tripsRef = databaseRef.child(TRIPS_PATH).child(trip.getId());
            
            // Преобразуем Trip в Map для Firebase
            Map<String, Object> tripMap = tripToMap(trip);
            
            tripsRef.setValue(tripMap)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Trip saved successfully: " + trip.getId());
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error saving trip", e);
                        if (callback != null) {
                            callback.onError(e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error converting trip to map", e);
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    /**
     * Загрузка всех поездок пользователя
     * @param userId ID пользователя
     * @param callback Callback с результатом
     */
    public void loadUserTrips(String userId, OnTripsLoadedCallback callback) {
        DatabaseReference tripsRef = databaseRef.child(TRIPS_PATH);
        
        tripsRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        List<Trip> trips = new ArrayList<>();
                        
                        for (DataSnapshot tripSnapshot : dataSnapshot.getChildren()) {
                            try {
                                Trip trip = mapToTrip(tripSnapshot.getKey(), tripSnapshot.getValue());
                                if (trip != null) {
                                    trips.add(trip);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing trip: " + tripSnapshot.getKey(), e);
                            }
                        }
                        
                        Log.d(TAG, "Loaded " + trips.size() + " trips for user: " + userId);
                        if (callback != null) {
                            callback.onTripsLoaded(trips);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "Error loading trips", databaseError.toException());
                        if (callback != null) {
                            callback.onError(databaseError.getMessage());
                        }
                    }
                });
    }

    /**
     * Загрузка одной поездки по ID
     * @param tripId ID поездки
     * @param callback Callback с результатом
     */
    public void loadTrip(String tripId, OnTripLoadedCallback callback) {
        DatabaseReference tripRef = databaseRef.child(TRIPS_PATH).child(tripId);
        
        tripRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    try {
                        Trip trip = mapToTrip(tripId, dataSnapshot.getValue());
                        if (callback != null) {
                            callback.onTripLoaded(trip);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing trip", e);
                        if (callback != null) {
                            callback.onError(e.getMessage());
                        }
                    }
                } else {
                    Log.w(TAG, "Trip not found: " + tripId);
                    if (callback != null) {
                        callback.onError("Поездка не найдена");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading trip", databaseError.toException());
                if (callback != null) {
                    callback.onError(databaseError.getMessage());
                }
            }
        });
    }

    /**
     * Удаление поездки
     * @param tripId ID поездки
     * @param callback Callback для обработки результата
     */
    public void deleteTrip(String tripId, OnDeleteCallback callback) {
        DatabaseReference tripRef = databaseRef.child(TRIPS_PATH).child(tripId);
        
        tripRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Trip deleted: " + tripId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting trip", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    /**
     * Обновление поездки
     * @param trip Поездка для обновления
     * @param callback Callback для обработки результата
     */
    public void updateTrip(Trip trip, OnSaveCallback callback) {
        saveTrip(trip, callback); // В Firebase setValue обновляет существующие данные
    }

    // ============================================
    // МЕТОДЫ ДЛЯ РАБОТЫ С МЕСТАМИ В ПОЕЗДКЕ
    // ============================================

    /**
     * Добавление места в поездку
     * @param tripId ID поездки
     * @param place Место для добавления
     * @param callback Callback для обработки результата
     */
    public void addPlaceToTrip(String tripId, EntertainmentPlace place, OnSaveCallback callback) {
        DatabaseReference placeRef = databaseRef.child(TRIPS_PATH)
                .child(tripId)
                .child("places")
                .child(place.getId());
        
        Map<String, Object> placeMap = placeToMap(place);
        
        placeRef.setValue(placeMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Place added to trip: " + place.getName());
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding place to trip", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    /**
     * Удаление места из поездки
     * @param tripId ID поездки
     * @param placeId ID места
     * @param callback Callback для обработки результата
     */
    public void removePlaceFromTrip(String tripId, String placeId, OnDeleteCallback callback) {
        DatabaseReference placeRef = databaseRef.child(TRIPS_PATH)
                .child(tripId)
                .child("places")
                .child(placeId);
        
        placeRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Place removed from trip: " + placeId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error removing place from trip", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    /**
     * Обновление даты и времени посещения места
     * @param tripId ID поездки
     * @param placeId ID места
     * @param plannedDate Дата (dd.MM.yyyy)
     * @param plannedTime Время (HH:mm)
     * @param callback Callback для обработки результата
     */
    public void updatePlaceDateTime(String tripId, String placeId, 
                                    String plannedDate, String plannedTime,
                                    OnSaveCallback callback) {
        DatabaseReference placeRef = databaseRef.child(TRIPS_PATH)
                .child(tripId)
                .child("places")
                .child(placeId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("plannedDate", plannedDate);
        updates.put("plannedTime", plannedTime);
        
        placeRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Place date/time updated: " + placeId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating place date/time", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // ============================================
    // МЕТОДЫ ДЛЯ РАБОТЫ С ГЛОБАЛЬНЫМ КАТАЛОГОМ МЕСТ
    // ============================================

    /**
     * Сохранение места в глобальный каталог
     * @param place Место для сохранения
     * @param callback Callback для обработки результата
     */
    public void savePlace(EntertainmentPlace place, OnSaveCallback callback) {
        DatabaseReference placeRef = databaseRef.child(PLACES_PATH).child(place.getId());
        
        Map<String, Object> placeMap = placeToMap(place);
        placeMap.put("createdAt", System.currentTimeMillis());
        
        placeRef.setValue(placeMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Place saved to catalog: " + place.getName());
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving place", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    /**
     * Загрузка всех мест из каталога
     * @param callback Callback с результатом
     */
    public void loadAllPlaces(OnPlacesLoadedCallback callback) {
        DatabaseReference placesRef = databaseRef.child(PLACES_PATH);
        
        placesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<EntertainmentPlace> places = new ArrayList<>();
                
                for (DataSnapshot placeSnapshot : dataSnapshot.getChildren()) {
                    try {
                        EntertainmentPlace place = mapToPlace(placeSnapshot.getValue());
                        if (place != null) {
                            places.add(place);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing place: " + placeSnapshot.getKey(), e);
                    }
                }
                
                Log.d(TAG, "Loaded " + places.size() + " places from catalog");
                if (callback != null) {
                    callback.onPlacesLoaded(places);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading places", databaseError.toException());
                if (callback != null) {
                    callback.onError(databaseError.getMessage());
                }
            }
        });
    }

    // ============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (ПРЕОБРАЗОВАНИЕ)
    // ============================================

    /**
     * Преобразование Trip в Map для Firebase
     */
    private Map<String, Object> tripToMap(Trip trip) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", trip.getUserId());
        map.put("name", trip.getName());
        map.put("destination", trip.getDestination());
        map.put("destinationLat", trip.getDestinationLat());
        map.put("destinationLon", trip.getDestinationLon());
        map.put("date", trip.getDate());
        map.put("createdAt", trip.getCreatedAt());
        
        // Преобразуем список мест в Map
        Map<String, Object> placesMap = new HashMap<>();
        if (trip.getFavoritePlaces() != null) {
            for (EntertainmentPlace place : trip.getFavoritePlaces()) {
                placesMap.put(place.getId(), placeToMap(place));
            }
        }
        map.put("places", placesMap);
        
        return map;
    }

    /**
     * Преобразование Map из Firebase в Trip
     */
    @SuppressWarnings("unchecked")
    private Trip mapToTrip(String tripId, Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        
        Map<String, Object> map = (Map<String, Object>) value;
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setUserId(getString(map, "userId"));
        trip.setName(getString(map, "name"));
        trip.setDestination(getString(map, "destination"));
        trip.setDestinationLat(getString(map, "destinationLat"));
        trip.setDestinationLon(getString(map, "destinationLon"));
        trip.setDate(getString(map, "date"));
        
        Object createdAt = map.get("createdAt");
        if (createdAt instanceof Long) {
            trip.setCreatedAt((Long) createdAt);
        } else if (createdAt instanceof Number) {
            trip.setCreatedAt(((Number) createdAt).longValue());
        }
        
        // Загружаем места
        Object placesObj = map.get("places");
        if (placesObj instanceof Map) {
            Map<String, Object> placesMap = (Map<String, Object>) placesObj;
            List<EntertainmentPlace> places = new ArrayList<>();
            for (Object placeObj : placesMap.values()) {
                EntertainmentPlace place = mapToPlace(placeObj);
                if (place != null) {
                    places.add(place);
                }
            }
            trip.setFavoritePlaces(places);
        }
        
        return trip;
    }

    /**
     * Преобразование EntertainmentPlace в Map для Firebase
     */
    private Map<String, Object> placeToMap(EntertainmentPlace place) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", place.getId());
        map.put("name", place.getName());
        map.put("description", place.getDescription());
        map.put("lat", place.getLat());
        map.put("lon", place.getLon());
        map.put("imageUrl", place.getImageUrl());
        map.put("address", place.getAddress());
        map.put("plannedDate", place.getPlannedDate());
        map.put("plannedTime", place.getPlannedTime());
        return map;
    }

    /**
     * Преобразование Map из Firebase в EntertainmentPlace
     */
    @SuppressWarnings("unchecked")
    private EntertainmentPlace mapToPlace(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        
        Map<String, Object> map = (Map<String, Object>) value;
        EntertainmentPlace place = new EntertainmentPlace();
        place.setId(getString(map, "id"));
        place.setName(getString(map, "name"));
        place.setDescription(getString(map, "description"));
        place.setLat(getString(map, "lat"));
        place.setLon(getString(map, "lon"));
        place.setImageUrl(getString(map, "imageUrl"));
        place.setAddress(getString(map, "address"));
        place.setPlannedDate(getString(map, "plannedDate"));
        place.setPlannedTime(getString(map, "plannedTime"));
        return place;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    // ============================================
    // МЕТОДЫ ДЛЯ ИНИЦИАЛИЗАЦИИ БАЗЫ ДАННЫХ
    // ============================================

    /**
     * Инициализация базы данных данными из JSON объекта
     * @param jsonData JSON данные в виде Map
     * @param callback Callback для обработки результата
     */
    public void initializeDatabaseFromJson(Map<String, Object> jsonData, OnSaveCallback callback) {
        try {
            // Устанавливаем все данные в корневой узел базы данных
            databaseRef.setValue(jsonData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Database initialized successfully from JSON");
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error initializing database from JSON", e);
                        if (callback != null) {
                            callback.onError(e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting database value", e);
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    /**
     * Инициализация базы данных начальными данными (примеры)
     * @param callback Callback для обработки результата
     */
    public void initializeDatabaseWithSampleData(OnSaveCallback callback) {
        try {
            Map<String, Object> data = new HashMap<>();
            
            // Создаем примеры пользователей
            Map<String, Object> users = new HashMap<>();
            
            Map<String, Object> user1 = new HashMap<>();
            user1.put("email", "user1@example.com");
            user1.put("fullName", "Иван Иванов");
            user1.put("passwordHash", "hash_заглушка");
            user1.put("createdAt", System.currentTimeMillis());
            user1.put("isBlocked", false);
            user1.put("failedLoginAttempts", 0);
            user1.put("lastFailedLoginAt", null);
            users.put("user_1234567890", user1);
            
            Map<String, Object> user2 = new HashMap<>();
            user2.put("email", "user2@example.com");
            user2.put("fullName", "Мария Петрова");
            user2.put("passwordHash", "hash_заглушка_2");
            user2.put("createdAt", System.currentTimeMillis());
            user2.put("isBlocked", false);
            user2.put("failedLoginAttempts", 0);
            user2.put("lastFailedLoginAt", null);
            users.put("user_9876543210", user2);
            
            data.put(USERS_PATH, users);
            
            // Создаем примеры поездок
            Map<String, Object> trips = new HashMap<>();
            
            Map<String, Object> trip1 = new HashMap<>();
            trip1.put("userId", "user_1234567890");
            trip1.put("name", "Поездка в Москву");
            trip1.put("destination", "Москва, Россия");
            trip1.put("destinationLat", "55.751244");
            trip1.put("destinationLon", "37.618423");
            trip1.put("date", "01.05.2025");
            trip1.put("createdAt", System.currentTimeMillis());
            
            // Места в поездке
            Map<String, Object> trip1Places = new HashMap<>();
            
            Map<String, Object> place1 = new HashMap<>();
            place1.put("id", "place_Красная_площадь_55.753930_37.620795");
            place1.put("name", "Красная площадь");
            place1.put("description", "Исторический центр Москвы");
            place1.put("lat", "55.753930");
            place1.put("lon", "37.620795");
            place1.put("imageUrl", "https://static-maps.yandex.ru/1.x/?ll=37.620795,55.753930&z=16&size=600,400&pt=37.620795,55.753930,pm2rdm&l=map");
            place1.put("address", "Москва, Красная площадь");
            place1.put("plannedDate", "01.05.2025");
            place1.put("plannedTime", "10:00");
            trip1Places.put("place_Красная_площадь_55.753930_37.620795", place1);
            
            trip1.put("places", trip1Places);
            trips.put("trip_1704067200000", trip1);
            
            data.put(TRIPS_PATH, trips);
            
            // Создаем примеры мест в каталоге
            Map<String, Object> places = new HashMap<>();
            places.put("place_Красная_площадь_55.753930_37.620795", place1);
            
            Map<String, Object> place2 = new HashMap<>();
            place2.put("id", "place_Третьяковская_галерея_55.741394_37.620793");
            place2.put("name", "Третьяковская галерея");
            place2.put("description", "Крупнейший музей русского искусства");
            place2.put("lat", "55.741394");
            place2.put("lon", "37.620793");
            place2.put("imageUrl", "https://static-maps.yandex.ru/1.x/?ll=37.620793,55.741394&z=16&size=600,400&pt=37.620793,55.741394,pm2rdm&l=map");
            place2.put("address", "Москва, Лаврушинский пер., 10");
            place2.put("createdAt", System.currentTimeMillis());
            places.put("place_Третьяковская_галерея_55.741394_37.620793", place2);
            
            data.put(PLACES_PATH, places);
            
            // Инициализируем базу данных
            initializeDatabaseFromJson(data, callback);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating sample data", e);
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }

    // ============================================
    // ИНТЕРФЕЙСЫ CALLBACK
    // ============================================

    public interface OnSaveCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface OnDeleteCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface OnTripsLoadedCallback {
        void onTripsLoaded(List<Trip> trips);
        void onError(String error);
    }

    public interface OnTripLoadedCallback {
        void onTripLoaded(Trip trip);
        void onError(String error);
    }

    public interface OnPlacesLoadedCallback {
        void onPlacesLoaded(List<EntertainmentPlace> places);
        void onError(String error);
    }
}

