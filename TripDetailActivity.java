package com.example.myapplication;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.EntertainmentAdapter;
import com.example.myapplication.databinding.ActivityTripDetailBinding;
import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.EntertainmentSearchHelper;
import com.example.myapplication.utils.TripStorage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripDetailActivity extends AppCompatActivity {

    private ActivityTripDetailBinding binding;
    private Trip trip;
    private TripStorage tripStorage;
    private Calendar selectedDate;
    private EntertainmentSearchHelper entertainmentSearchHelper;
    private EntertainmentAdapter entertainmentAdapter;
    private EntertainmentAdapter favoritesAdapter;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tripStorage = new TripStorage(this);
        selectedDate = Calendar.getInstance();
        entertainmentSearchHelper = new EntertainmentSearchHelper();
        executorService = Executors.newSingleThreadExecutor();

        String tripId = getIntent().getStringExtra("trip_id");
        if (tripId != null) {
            loadTrip(tripId);
        } else {
            Toast.makeText(this, "Ошибка загрузки поездки", Toast.LENGTH_SHORT).show();
            finish();
        }

        setupDatePicker();
        setupEntertainmentList();
        setupFavoritesList();
        setupEntertainmentSection();
    }

    private void loadTrip(String tripId) {
        for (Trip t : tripStorage.getAllTrips()) {
            if (t.getId().equals(tripId)) {
                trip = t;
                break;
            }
        }

        if (trip != null) {
            binding.tripNameText.setText(trip.getName());
            binding.destinationText.setText(trip.getDestination());
            
            if (trip.getDate() != null && !trip.getDate().isEmpty()) {
                binding.dateText.setText(trip.getDate());
            } else {
                binding.dateText.setText("Дата не выбрана");
            }
            
            // Обновляем список избранных после загрузки поездки
            if (favoritesAdapter != null) {
                updateFavoritesList();
            }
        }
    }

    private void setupDatePicker() {
        binding.selectDateButton.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (trip.getDate() != null && !trip.getDate().isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    calendar.setTime(sdf.parse(trip.getDate()));
                } catch (Exception e) {
                    calendar = Calendar.getInstance();
                }
            }

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        selectedDate.set(year, month, dayOfMonth);
                        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                        String dateString = sdf.format(selectedDate.getTime());
                        trip.setDate(dateString);
                        tripStorage.updateTrip(trip);
                        binding.dateText.setText(dateString);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
    }

    private void setupEntertainmentList() {
        RecyclerView recyclerView = binding.entertainmentRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        entertainmentAdapter = new EntertainmentAdapter(new ArrayList<>(), place -> {
            // Можно добавить действие при клике на развлечение
            Toast.makeText(this, "Выбрано: " + place.getName(), Toast.LENGTH_SHORT).show();
        });
        
        // Устанавливаем обработчик избранного
        entertainmentAdapter.setFavoriteListener((place, isFavorite) -> {
            if (trip != null) {
                if (isFavorite) {
                    trip.addFavoritePlace(place);
                    Toast.makeText(this, "Добавлено в избранное", Toast.LENGTH_SHORT).show();
                } else {
                    trip.removeFavoritePlace(place.getId());
                    Toast.makeText(this, "Удалено из избранного", Toast.LENGTH_SHORT).show();
                }
                tripStorage.updateTrip(trip);
                updateFavoritePlacesSet();
                updateFavoritesList();
                updateMapWithFavorites();
            }
        });
        
        recyclerView.setAdapter(entertainmentAdapter);
        updateFavoritePlacesSet();
    }

    private void setupFavoritesList() {
        RecyclerView recyclerView = binding.favoritesRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        favoritesAdapter = new EntertainmentAdapter(new ArrayList<>(), place -> {
            Toast.makeText(this, "Выбрано: " + place.getName(), Toast.LENGTH_SHORT).show();
        });
        
        // Устанавливаем обработчик избранного для списка избранных
        favoritesAdapter.setFavoriteListener((place, isFavorite) -> {
            if (trip != null && !isFavorite) {
                trip.removeFavoritePlace(place.getId());
                Toast.makeText(this, "Удалено из избранного", Toast.LENGTH_SHORT).show();
                tripStorage.updateTrip(trip);
                updateFavoritePlacesSet();
                updateFavoritesList();
                updateMapWithFavorites();
            }
        });
        
        recyclerView.setAdapter(favoritesAdapter);
        updateFavoritesList();
    }

    private void updateFavoritesList() {
        if (trip != null && favoritesAdapter != null) {
            List<EntertainmentPlace> favorites = trip.getFavoritePlaces();
            if (favorites != null && !favorites.isEmpty()) {
                binding.favoritesEmptyText.setVisibility(View.GONE);
                binding.favoritesRecyclerView.setVisibility(View.VISIBLE);
                favoritesAdapter.updatePlaces(favorites);
                Set<String> favoriteIds = new HashSet<>();
                for (EntertainmentPlace place : favorites) {
                    favoriteIds.add(place.getId());
                }
                favoritesAdapter.setFavoritePlaceIds(favoriteIds);
            } else {
                binding.favoritesEmptyText.setVisibility(View.VISIBLE);
                binding.favoritesRecyclerView.setVisibility(View.GONE);
            }
        }
    }

    private void updateFavoritePlacesSet() {
        if (trip != null && entertainmentAdapter != null) {
            Set<String> favoriteIds = new HashSet<>();
            for (EntertainmentPlace place : trip.getFavoritePlaces()) {
                favoriteIds.add(place.getId());
            }
            entertainmentAdapter.setFavoritePlaceIds(favoriteIds);
        }
    }

    private void setupEntertainmentSection() {
        if (trip == null || trip.getDestinationLat() == null || trip.getDestinationLon() == null) {
            return;
        }

        // Упрощенная карта с метками избранных достопримечательностей
        binding.entertainmentMapWebView.getSettings().setJavaScriptEnabled(true);
        binding.entertainmentMapWebView.getSettings().setDomStorageEnabled(true);
        binding.entertainmentMapWebView.setWebViewClient(new android.webkit.WebViewClient());

        // Обновляем карту с избранными достопримечательностями
        updateMapWithFavorites();

        // Загружаем развлечения в фоновом потоке
        loadEntertainmentPlaces(trip.getDestinationLat(), trip.getDestinationLon());
    }

    private void loadEntertainmentPlaces(String lat, String lon) {
        executorService.execute(() -> {
            List<EntertainmentPlace> places = entertainmentSearchHelper.searchEntertainment(lat, lon);
            runOnUiThread(() -> {
                if (places.isEmpty()) {
                    binding.entertainmentEmptyText.setVisibility(View.VISIBLE);
                    binding.entertainmentRecyclerView.setVisibility(View.GONE);
                } else {
                    binding.entertainmentEmptyText.setVisibility(View.GONE);
                    binding.entertainmentRecyclerView.setVisibility(View.VISIBLE);
                    entertainmentAdapter.updatePlaces(places);
                    updateFavoritePlacesSet();
                    updateFavoritesList();
                    updateMapWithFavorites();
                }
            });
        });
    }

    private void updateMapWithFavorites() {
        if (trip == null || trip.getDestinationLat() == null || trip.getDestinationLon() == null) {
            return;
        }
        
        String html = generateMapHtml(trip.getDestinationLat(), trip.getDestinationLon());
        binding.entertainmentMapWebView.loadDataWithBaseURL(
                "https://api-maps.yandex.ru/",
                html,
                "text/html",
                "UTF-8",
                null
        );
    }

    private String generateMapHtml(String lat, String lon) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
            .append("<html><head><meta name='viewport' content='initial-scale=1.0, width=device-width'/>")
            .append("<script src='https://api-maps.yandex.ru/2.1/?apikey=34afc545-e0c4-4be9-a15c-5372f2b85691&lang=ru_RU' type='text/javascript'></script>")
            .append("<style>html, body, #map { width:100%; height:100%; margin:0; padding:0; }</style>")
            .append("</head><body>")
            .append("<div id='map'></div>")
            .append("<script type='text/javascript'>")
            .append("ymaps.ready(function() {")
            .append("  var map = new ymaps.Map('map', {")
            .append("    center: [").append(lat).append(", ").append(lon).append("],")
            .append("    zoom: 13,")
            .append("    controls: ['zoomControl']")
            .append("  });");

        // Добавляем метку точки назначения
        html.append("  var destinationPlacemark = new ymaps.Placemark([")
            .append(lat).append(", ").append(lon).append("], {")
            .append("    balloonContent: '").append(escapeJs(trip.getDestination())).append("'")
            .append("  }, {")
            .append("    preset: 'islands#blueDotIcon'")
            .append("  });")
            .append("  map.geoObjects.add(destinationPlacemark);");

        // Добавляем метки избранных достопримечательностей
        List<EntertainmentPlace> favorites = trip.getFavoritePlaces();
        if (favorites != null && !favorites.isEmpty()) {
            for (EntertainmentPlace place : favorites) {
                if (place.getLat() != null && place.getLon() != null) {
                    html.append("  var placemark").append(place.getId().replaceAll("[^a-zA-Z0-9]", "_"))
                        .append(" = new ymaps.Placemark([")
                        .append(place.getLat()).append(", ").append(place.getLon()).append("], {")
                        .append("    balloonContent: '").append(escapeJs(place.getName())).append("'")
                        .append("  }, {")
                        .append("    preset: 'islands#redIcon'")
                        .append("  });")
                        .append("  map.geoObjects.add(placemark").append(place.getId().replaceAll("[^a-zA-Z0-9]", "_")).append(");");
                }
            }
        }

        html.append("});")
            .append("</script>")
            .append("</body></html>");

        return html.toString();
    }

    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (binding != null && binding.entertainmentMapWebView != null) {
            binding.entertainmentMapWebView.destroy();
        }
        binding = null;
    }
}

