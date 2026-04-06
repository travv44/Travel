package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.FirebaseTripHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Тестовая активность для демонстрации работы с Firebase Realtime Database
 * Показывает методы загрузки и выгрузки информации
 */
public class FirebaseTestActivity extends AppCompatActivity {

    private TextView outputText;
    private FirebaseTripHelper firebaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firebase_test);

        firebaseHelper = FirebaseTripHelper.getInstance();
        outputText = findViewById(R.id.outputText);

        setupButtons();
    }

    private void setupButtons() {
        // Кнопка сохранения поездки
        Button saveTripBtn = findViewById(R.id.saveTripBtn);
        saveTripBtn.setOnClickListener(v -> saveTripExample());

        // Кнопка загрузки поездок
        Button loadTripsBtn = findViewById(R.id.loadTripsBtn);
        loadTripsBtn.setOnClickListener(v -> loadTripsExample());

        // Кнопка сохранения места
        Button savePlaceBtn = findViewById(R.id.savePlaceBtn);
        savePlaceBtn.setOnClickListener(v -> savePlaceExample());

        // Кнопка загрузки мест
        Button loadPlacesBtn = findViewById(R.id.loadPlacesBtn);
        loadPlacesBtn.setOnClickListener(v -> loadPlacesExample());

        // Кнопка добавления места в поездку
        Button addPlaceToTripBtn = findViewById(R.id.addPlaceToTripBtn);
        addPlaceToTripBtn.setOnClickListener(v -> addPlaceToTripExample());

        // Кнопка просмотра JSON структуры
        Button viewJsonBtn = findViewById(R.id.viewJsonBtn);
        viewJsonBtn.setOnClickListener(v -> viewJsonStructure());

        // Кнопка инициализации базы данных
        Button initializeDbBtn = findViewById(R.id.initializeDbBtn);
        initializeDbBtn.setOnClickListener(v -> initializeDatabase());

        Button healthCheckBtn = findViewById(R.id.savePlaceBtn);
        if (healthCheckBtn != null) {
            healthCheckBtn.setOnClickListener(v -> runDatabaseHealthCheck());
        }
    }

    private void runDatabaseHealthCheck() {
        outputText.setText("Проверка подключения к Firebase Realtime Database...");

        String url = "https://nosql-562de-default-rtdb.firebaseio.com/";
        FirebaseDatabase db = FirebaseDatabase.getInstance(url);
        DatabaseReference ref = db.getReference("healthcheck").child(String.valueOf(System.currentTimeMillis()));

        ref.setValue("ok")
                .addOnSuccessListener(aVoid -> ref.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        runOnUiThread(() -> {
                            outputText.setText("✅ Health-check OK\n\n" +
                                    "URL: " + url + "\n" +
                                    "Path: " + snapshot.getRef().toString() + "\n" +
                                    "Value: " + snapshot.getValue());
                            Toast.makeText(FirebaseTestActivity.this, "Firebase OK", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        runOnUiThread(() -> {
                            outputText.setText("❌ Health-check failed: " + error.getMessage());
                            Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    outputText.setText("❌ Health-check write failed: " + e.getMessage());
                    Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }));
    }

    /**
     * Пример сохранения поездки в Firebase
     */
    private void saveTripExample() {
        outputText.setText("Сохранение поездки...");
        
        Trip trip = new Trip();
        trip.setId("trip_" + System.currentTimeMillis());
        trip.setName("Тестовая поездка в Париж");
        trip.setDestination("Париж, Франция");
        trip.setDestinationLat("48.8566");
        trip.setDestinationLon("2.3522");
        trip.setDate("15.07.2025");
        trip.setCreatedAt(System.currentTimeMillis());

        firebaseHelper.saveTrip(trip, new FirebaseTripHelper.OnSaveCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    outputText.setText("✅ Поездка успешно сохранена!\nID: " + trip.getId());
                    Toast.makeText(FirebaseTestActivity.this, "Поездка сохранена", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    outputText.setText("❌ Ошибка сохранения: " + error);
                    Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Пример загрузки поездок пользователя
     */
    private void loadTripsExample() {
        outputText.setText("Загрузка поездок...");
        
        // Используем тестовый userId
        String userId = "user_1234567890";
        
        firebaseHelper.loadUserTrips(userId, new FirebaseTripHelper.OnTripsLoadedCallback() {
            @Override
            public void onTripsLoaded(List<Trip> trips) {
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ Загружено поездок: ").append(trips.size()).append("\n\n");
                    
                    for (Trip trip : trips) {
                        sb.append(" ").append(trip.getName()).append("\n");
                        sb.append("   Направление: ").append(trip.getDestination()).append("\n");
                        sb.append("   Дата: ").append(trip.getDate() != null ? trip.getDate() : "не выбрана").append("\n");
                        sb.append("   Мест в поездке: ").append(
                                trip.getFavoritePlaces() != null ? trip.getFavoritePlaces().size() : 0
                        ).append("\n\n");
                    }
                    
                    outputText.setText(sb.toString());
                    Toast.makeText(FirebaseTestActivity.this, "Загружено: " + trips.size(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    outputText.setText("❌ Ошибка загрузки: " + error);
                    Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Пример сохранения места в глобальный каталог
     */
    private void savePlaceExample() {
        outputText.setText("Сохранение места...");
        
        EntertainmentPlace place = new EntertainmentPlace(
                "place_Эйфелева_башня_48.8584_2.2945",
                "Эйфелева башня",
                "Знаменитая башня в Париже, символ Франции",
                "48.8584",
                "2.2945",
                "https://static-maps.yandex.ru/1.x/?ll=2.2945,48.8584&z=16&size=600,400&pt=2.2945,48.8584,pm2rdm&l=map",
                "Париж, Франция, Марсово поле"
        );

        firebaseHelper.savePlace(place, new FirebaseTripHelper.OnSaveCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    outputText.setText("✅ Место успешно сохранено!\nНазвание: " + place.getName());
                    Toast.makeText(FirebaseTestActivity.this, "Место сохранено", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    outputText.setText("❌ Ошибка сохранения: " + error);
                    Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Пример загрузки всех мест из каталога
     */
    private void loadPlacesExample() {
        outputText.setText("Загрузка мест...");
        
        firebaseHelper.loadAllPlaces(new FirebaseTripHelper.OnPlacesLoadedCallback() {
            @Override
            public void onPlacesLoaded(List<EntertainmentPlace> places) {
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ Загружено мест: ").append(places.size()).append("\n\n");
                    
                    for (EntertainmentPlace place : places) {
                        sb.append(" ").append(place.getName()).append("\n");
                        if (place.getDescription() != null && !place.getDescription().isEmpty()) {
                            sb.append("   ").append(place.getDescription()).append("\n");
                        }
                        if (place.getAddress() != null && !place.getAddress().isEmpty()) {
                            sb.append("   Адрес: ").append(place.getAddress()).append("\n");
                        }
                        sb.append("\n");
                    }
                    
                    outputText.setText(sb.toString());
                    Toast.makeText(FirebaseTestActivity.this, "Загружено: " + places.size(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    outputText.setText("❌ Ошибка загрузки: " + error);
                    Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Пример добавления места в поездку
     */
    private void addPlaceToTripExample() {
        outputText.setText("Добавление места в поездку...");
        
        // Сначала загружаем первую поездку пользователя
        String userId = "user_1234567890";
        firebaseHelper.loadUserTrips(userId, new FirebaseTripHelper.OnTripsLoadedCallback() {
            @Override
            public void onTripsLoaded(List<Trip> trips) {
                if (trips.isEmpty()) {
                    runOnUiThread(() -> {
                        outputText.setText("❌ Нет поездок для добавления места");
                        Toast.makeText(FirebaseTestActivity.this, "Сначала создайте поездку", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                Trip trip = trips.get(0);
                EntertainmentPlace place = new EntertainmentPlace(
                        "place_Лувр_48.8606_2.3376",
                        "Лувр",
                        "Крупнейший художественный музей мира",
                        "48.8606",
                        "2.3376",
                        "https://static-maps.yandex.ru/1.x/?ll=2.3376,48.8606&z=16&size=600,400&pt=2.3376,48.8606,pm2rdm&l=map",
                        "Париж, Франция, Rue de Rivoli"
                );
                
                firebaseHelper.addPlaceToTrip(trip.getId(), place, new FirebaseTripHelper.OnSaveCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            outputText.setText("✅ Место добавлено в поездку!\n" +
                                    "Поездка: " + trip.getName() + "\n" +
                                    "Место: " + place.getName());
                            Toast.makeText(FirebaseTestActivity.this, "Место добавлено", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            outputText.setText("❌ Ошибка: " + error);
                            Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    outputText.setText("❌ Ошибка загрузки поездок: " + error);
                });
            }
        });
    }

    /**
     * Просмотр структуры базы данных из JSON файла
     */
    private void viewJsonStructure() {
        outputText.setText("Загрузка структуры БД...");
        
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.firebase_database_structure);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }
            reader.close();
            inputStream.close();
            
            // Форматируем JSON для лучшей читаемости
            String formattedJson = formatJson(jsonContent.toString());
            
            outputText.setText("📋 Структура базы данных Firebase:\n\n" + formattedJson);
            Toast.makeText(this, "Структура БД загружена", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            outputText.setText("❌ Ошибка чтения JSON файла: " + e.getMessage());
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Простое форматирование JSON для отображения
     */
    private String formatJson(String json) {
        // Простое форматирование с отступами
        StringBuilder formatted = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        
        for (char c : json.toCharArray()) {
            if (c == '"' && (formatted.length() == 0 || formatted.charAt(formatted.length() - 1) != '\\')) {
                inString = !inString;
                formatted.append(c);
            } else if (!inString) {
                if (c == '{' || c == '[') {
                    formatted.append(c).append("\n");
                    indentLevel++;
                    formatted.append(getIndent(indentLevel));
                } else if (c == '}' || c == ']') {
                    formatted.append("\n");
                    indentLevel--;
                    formatted.append(getIndent(indentLevel));
                    formatted.append(c);
                } else if (c == ',') {
                    formatted.append(c).append("\n").append(getIndent(indentLevel));
                } else if (c == ':') {
                    formatted.append(c).append(" ");
                } else if (!Character.isWhitespace(c)) {
                    formatted.append(c);
                }
            } else {
                formatted.append(c);
            }
        }
        
        return formatted.toString();
    }

    private String getIndent(int level) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < level; i++) {
            indent.append("  ");
        }
        return indent.toString();
    }

    /**
     * Инициализация базы данных начальными данными
     */
    private void initializeDatabase() {
        outputText.setText("Инициализация базы данных...\nПожалуйста, подождите...");
        
        // Пробуем загрузить из JSON файла, если не получится - используем примерные данные
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.firebase_database_structure);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();
            inputStream.close();
            
            // Парсим JSON и преобразуем в Map
            JSONObject jsonObject = new JSONObject(jsonContent.toString());
            Map<String, Object> dataMap = jsonToMap(jsonObject);
            
            // Инициализируем базу данных из JSON
            firebaseHelper.initializeDatabaseFromJson(dataMap, new FirebaseTripHelper.OnSaveCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("✅ База данных успешно инициализирована из JSON!\n\n");
                        sb.append("Добавлено:\n");
                        sb.append("• Пользователи\n");
                        sb.append("• Поездки с местами\n");
                        sb.append("• Достопримечательности в каталоге\n");
                        sb.append("• Записи аудита\n\n");
                        sb.append("Проверьте Firebase Console:\n");
                        sb.append("https://nosql-562de-default-rtdb.firebaseio.com/");

                        outputText.setText(sb.toString());
                        Toast.makeText(FirebaseTestActivity.this, "База данных заполнена из JSON!", Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(String error) {
                    // Если не получилось из JSON, используем примерные данные
                    initializeWithSampleData(error);
                }
            });
            
        } catch (Exception e) {
            // Если не получилось загрузить JSON, используем примерные данные
            initializeWithSampleData("Ошибка загрузки JSON: " + e.getMessage());
        }
    }

    /**
     * Инициализация примерными данными
     */
    private void initializeWithSampleData(String previousError) {
        outputText.setText("Используем примерные данные...\nПожалуйста, подождите...");
        
        firebaseHelper.initializeDatabaseWithSampleData(new FirebaseTripHelper.OnSaveCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ База данных успешно инициализирована!\n\n");
                    if (previousError != null && !previousError.isEmpty()) {
                        sb.append("Предыдущая ошибка: ").append(previousError).append("\n\n");
                    }
                    sb.append("Добавлено:\n");
                    sb.append("• 2 пользователя\n");
                    sb.append("• 1 поездка с местами\n");
                    sb.append("• 2 достопримечательности в каталоге\n\n");
                    sb.append("Проверьте Firebase Console:\n");
                    sb.append("https://nosql-562de-default-rtdb.firebaseio.com/");
                    
                    outputText.setText(sb.toString());
                    Toast.makeText(FirebaseTestActivity.this, "База данных заполнена!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    outputText.setText("❌ Ошибка инициализации: " + error + "\n\n" +
                            "Проверьте:\n" +
                            "1. Подключение к интернету\n" +
                            "2. Правильность URL базы данных\n" +
                            "3. Правила безопасности Firebase");
                    Toast.makeText(FirebaseTestActivity.this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Преобразование JSONObject в Map для Firebase
     */
    private Map<String, Object> jsonToMap(JSONObject json) throws Exception {
        Map<String, Object> map = new HashMap<>();
        
        if (json == null) {
            return map;
        }
        
        JSONArray names = json.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                Object value = json.get(key);
                
                if (value instanceof JSONObject) {
                    map.put(key, jsonToMap((JSONObject) value));
                } else if (value instanceof JSONArray) {
                    map.put(key, jsonArrayToList((JSONArray) value));
                } else {
                    map.put(key, value);
                }
            }
        }
        
        return map;
    }

    /**
     * Преобразование JSONArray в List
     */
    private List<Object> jsonArrayToList(JSONArray array) throws Exception {
        List<Object> list = new java.util.ArrayList<>();
        
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            
            if (value instanceof JSONObject) {
                list.add(jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        
        return list;
    }

}
