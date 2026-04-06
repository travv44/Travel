package com.example.myapplication;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.myapplication.adapter.EntertainmentAdapter;
import com.example.myapplication.databinding.ActivityTripDetailBinding;
import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.DgisThingsToDoRepository;
import com.example.myapplication.utils.FirebaseTripHelper;
import com.example.myapplication.utils.VisitHistoryHelper;
import com.google.firebase.auth.FirebaseAuth;

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

    private static final String TAG = "TripDetailActivity";
    private static final int NEARBY_LIMIT = 10;
    private ActivityTripDetailBinding binding;
    private Trip trip;
    private FirebaseTripHelper firebaseTripHelper;
    private Calendar selectedDate;
    private DgisThingsToDoRepository dgisThingsToDoRepository;
    private ExecutorService executorService;
    private List<EntertainmentPlace> nearbyPlaces = new ArrayList<>();
    private EntertainmentAdapter entertainmentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseTripHelper = FirebaseTripHelper.getInstance();
        selectedDate = Calendar.getInstance();
        dgisThingsToDoRepository = new DgisThingsToDoRepository(this);
        executorService = Executors.newSingleThreadExecutor();

        String tripId = getIntent().getStringExtra("trip_id");
        if (tripId != null) {
            loadTrip(tripId);
        } else {
            Toast.makeText(this, "Ошибка загрузки поездки", Toast.LENGTH_SHORT).show();
            finish();
        }

        setupDatePicker();
        setupActivitiesList();
        // setupEntertainmentSection вызывается после загрузки поездки из Firebase
    }

    private void loadTrip(String tripId) {
        firebaseTripHelper.loadTrip(tripId, new FirebaseTripHelper.OnTripLoadedCallback() {
            @Override
            public void onTripLoaded(Trip loadedTrip) {
                runOnUiThread(() -> {
                    trip = loadedTrip;
                    if (trip != null) {
                        binding.tripNameText.setText(trip.getName());
                        binding.destinationText.setText(trip.getDestination());

                        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            VisitHistoryHelper.getInstance().recordTripDestinationVisit(
                                    uid,
                                    trip.getId(),
                                    trip.getDestination(),
                                    trip.getDestinationLat(),
                                    trip.getDestinationLon(),
                                    "trip_open");
                        }

                        if (trip.getDate() != null && !trip.getDate().isEmpty()) {
                            binding.dateText.setText(trip.getDate());
                        } else {
                            binding.dateText.setText("Дата не выбрана");
                        }
                    }
                    if (entertainmentAdapter != null) {
                        entertainmentAdapter.setAddedPlaceIds(getFavoritePlaceIds());
                    }
                    setupEntertainmentSection();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(TripDetailActivity.this, "Ошибка загрузки поездки: " + error, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void setupDatePicker() {
        binding.selectDateButton.setOnClickListener(v -> {
            if (trip == null) return;
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
                        firebaseTripHelper.updateTrip(trip, null);
                        binding.dateText.setText(dateString);
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
    }


    private void setupActivitiesList() {
        // Настраиваем GridLayoutManager с 2 колонками
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        binding.activitiesRecyclerView.setLayoutManager(layoutManager);
        
        // Создаем адаптер с пустым списком
        entertainmentAdapter = new EntertainmentAdapter(new ArrayList<>(), this::openPlaceBottomSheetLike);

        // Устанавливаем обработчик добавления в поездку
        entertainmentAdapter.setAddToTripListener((place, added) -> {
            if (trip != null) {
                if (FirebaseAuth.getInstance().getCurrentUser() != null && place != null) {
                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    VisitHistoryHelper.getInstance().recordPlaceInteraction(
                            uid,
                            place,
                            added ? "place_add_to_trip" : "place_remove_from_trip"
                    );
                }
                if (added) {
                    trip.addFavoritePlace(place);
                    Toast.makeText(this, "Добавлено в поездку: " + place.getName(), Toast.LENGTH_SHORT).show();
                } else {
                    trip.removeFavoritePlace(place.getId());
                    Toast.makeText(this, "Удалено из поездки: " + place.getName(), Toast.LENGTH_SHORT).show();
                }
                firebaseTripHelper.updateTrip(trip, null);
                entertainmentAdapter.setAddedPlaceIds(getFavoritePlaceIds());
                updateMapWithNearbyPlaces();
            }
        });

        // Устанавливаем уже добавленные места
        entertainmentAdapter.setAddedPlaceIds(getFavoritePlaceIds());
        
        // Привязываем адаптер к RecyclerView
        binding.activitiesRecyclerView.setAdapter(entertainmentAdapter);

        binding.planButton.setOnClickListener(v -> {
            if (trip != null) {
                PlanActivity.start(this, trip.getId());
            }
        });
    }

    private void openPlaceBottomSheetLike(EntertainmentPlace place) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null && place != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            VisitHistoryHelper.getInstance().recordPlaceInteraction(uid, place, "place_open");
        }
        // Мини-описание (как в референсе при тапе по карточке)
        StringBuilder message = new StringBuilder();
        if (place.getDescription() != null && !place.getDescription().isEmpty()) {
            message.append(place.getDescription()).append("\n\n");
        }
        if (place.getAddress() != null && !place.getAddress().isEmpty()) {
            message.append(place.getAddress()).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle(place.getName())
                .setMessage(message.length() == 0 ? "Описание недоступно" : message.toString())
                .setPositiveButton("Показать на карте", (d, which) -> focusPlaceOnMap(place))
                .setNegativeButton("Закрыть", null)
                .show();

        // И сразу подсветим на карте метку
        focusPlaceOnMap(place);
    }

    private void focusPlaceOnMap(EntertainmentPlace place) {
        if (place == null || place.getId() == null) return;
        String key = place.getId().replaceAll("[^a-zA-Z0-9]", "_");
        String js = "try{focusPlace('" + key + "');}catch(e){}";
        binding.entertainmentMapWebView.evaluateJavascript(js, null);
    }

    private void setupEntertainmentSection() {
        if (trip == null || trip.getDestinationLat() == null || trip.getDestinationLon() == null) {
            return;
        }

        // Упрощенная карта с метками избранных достопримечательностей
        binding.entertainmentMapWebView.getSettings().setJavaScriptEnabled(true);
        binding.entertainmentMapWebView.getSettings().setDomStorageEnabled(true);
        binding.entertainmentMapWebView.setWebViewClient(new android.webkit.WebViewClient());

        // Обновляем карту с достопримечательностями поблизости
        updateMapWithNearbyPlaces();

        // Загружаем развлечения в фоновом потоке
        loadEntertainmentPlaces(trip.getDestinationLat(), trip.getDestinationLon());
    }

    private void loadEntertainmentPlaces(String lat, String lon) {
        final double clat;
        final double clon;
        try {
            clat = Double.parseDouble(lat.trim().replace(',', '.'));
            clon = Double.parseDouble(lon.trim().replace(',', '.'));
        } catch (Exception e) {
            applyEntertainmentPlacesToUi(new ArrayList<>());
            return;
        }

        Log.d(TAG, "Loading places (2GIS) lat=" + lat + ", lon=" + lon);
        dgisThingsToDoRepository.loadTop10(clat, clon, new DgisThingsToDoRepository.Callback() {
            @Override
            public void onSuccess(List<EntertainmentPlace> places) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (places == null || places.isEmpty()) {
                        applyEntertainmentPlacesToUi(new ArrayList<>());
                        return;
                    }
                    List<EntertainmentPlace> sortedLimited = sortAndLimitByDistance(places, clat, clon, NEARBY_LIMIT);
                    applyEntertainmentPlacesToUi(attachUiDetails(sortedLimited));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    applyEntertainmentPlacesToUi(new ArrayList<>());
                    Toast.makeText(TripDetailActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<EntertainmentPlace> sortAndLimitByDistance(List<EntertainmentPlace> input, double centerLat, double centerLon, int limit) {
        List<EntertainmentPlace> out = new ArrayList<>();
        if (input == null) return out;
        out.addAll(input);
        java.util.Collections.sort(out, (a, b) -> {
            double da = distanceKmSafe(centerLat, centerLon, a);
            double db = distanceKmSafe(centerLat, centerLon, b);
            return Double.compare(da, db);
        });
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
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

    private List<EntertainmentPlace> attachUiDetails(List<EntertainmentPlace> input) {
        if (input == null) return new ArrayList<>();
        List<EntertainmentPlace> out = new ArrayList<>(input.size());
        for (EntertainmentPlace p : input) {
            if (p == null) continue;
            // Put opening hours + website into description for now (UI has only 2 text rows)
            StringBuilder d = new StringBuilder();
            if (p.getDescription() != null && !p.getDescription().trim().isEmpty()) {
                d.append(p.getDescription().trim());
            }
            if (p.getOpeningHours() != null && !p.getOpeningHours().trim().isEmpty()) {
                if (d.length() > 0) d.append(" • ");
                d.append("Часы: ").append(p.getOpeningHours().trim());
            }
            if (p.getWebsite() != null && !p.getWebsite().trim().isEmpty()) {
                if (d.length() > 0) d.append(" • ");
                d.append("Сайт: ").append(p.getWebsite().trim());
            }
            p.setDescription(d.toString());
            out.add(p);
        }
        return out;
    }

    private void applyEntertainmentPlacesToUi(List<EntertainmentPlace> places) {
        nearbyPlaces = places;
        if (entertainmentAdapter != null) {
            entertainmentAdapter.updatePlaces(places);
            entertainmentAdapter.setAddedPlaceIds(getFavoritePlaceIds());
        }
        if (places.isEmpty()) {
            binding.activitiesEmptyText.setVisibility(View.VISIBLE);
            binding.activitiesRecyclerView.setVisibility(View.GONE);
        } else {
            binding.activitiesEmptyText.setVisibility(View.GONE);
            binding.activitiesRecyclerView.setVisibility(View.VISIBLE);
        }
        updateMapWithNearbyPlaces();
    }

    private void updateMapWithNearbyPlaces() {
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
            .append("  window._map = new ymaps.Map('map', {")
            .append("    center: [").append(lat).append(", ").append(lon).append("],")
            .append("    zoom: 13,")
            .append("    controls: ['zoomControl']")
            .append("  });");
        html.append("  window._placemarks = {};"); // id -> placemark

        // Добавляем метку точки назначения
        html.append("  var destinationPlacemark = new ymaps.Placemark([")
            .append(lat).append(", ").append(lon).append("], {")
            .append("    balloonContent: '").append(escapeJs(trip.getDestination())).append("'")
            .append("  }, {")
            .append("    preset: 'islands#blueDotIcon'")
            .append("  });")
            .append("  window._map.geoObjects.add(destinationPlacemark);");

        // Получаем список ID избранных достопримечательностей
        Set<String> favoriteIds = new HashSet<>();
        List<EntertainmentPlace> favorites = trip.getFavoritePlaces();
        if (favorites != null && !favorites.isEmpty()) {
            for (EntertainmentPlace place : favorites) {
                favoriteIds.add(place.getId());
            }
        }

        // Добавляем метки всех достопримечательностей поблизости
        if (nearbyPlaces != null && !nearbyPlaces.isEmpty()) {
            for (EntertainmentPlace place : nearbyPlaces) {
                if (place.getLat() != null && place.getLon() != null) {
                    boolean isFavorite = favoriteIds.contains(place.getId());
                    String key = place.getId().replaceAll("[^a-zA-Z0-9]", "_");
                    String balloon = "<b>" + escapeJs(place.getName()) + "</b>";
                    if (place.getDescription() != null && !place.getDescription().isEmpty()) {
                        balloon += "<br/>" + escapeJs(place.getDescription());
                    }
                    if (place.getAddress() != null && !place.getAddress().isEmpty()) {
                        balloon += "<br/><i>" + escapeJs(place.getAddress()) + "</i>";
                    }

                    html.append("  var pm_").append(key)
                        .append(" = new ymaps.Placemark([")
                        .append(place.getLat()).append(", ").append(place.getLon()).append("], {")
                        .append("    balloonContent: '").append(balloon).append("'")
                        .append("  }, {")
                        .append("    preset: '").append(isFavorite ? "islands#redIcon" : "islands#blueIcon").append("'")
                        .append("  });")
                        .append("  window._map.geoObjects.add(pm_").append(key).append(");")
                        .append("  window._placemarks['").append(key).append("'] = pm_").append(key).append(";");
                }
            }
        }

        // JS API: фокус на конкретной метке из Android
        html.append("  window.focusPlace = function(id) {")
            .append("    var pm = window._placemarks[id];")
            .append("    if(!pm) return;")
            .append("    var coords = pm.geometry.getCoordinates();")
            .append("    window._map.setCenter(coords, 16, {duration: 300});")
            .append("    pm.balloon.open();")
            .append("  };");

        html.append("});")
            .append("</script>")
            .append("</body></html>");

        return html.toString();
    }

    private Set<String> getFavoritePlaceIds() {
        Set<String> ids = new HashSet<>();
        if (trip != null && trip.getFavoritePlaces() != null) {
            for (EntertainmentPlace place : trip.getFavoritePlaces()) {
                ids.add(place.getId());
            }
        }
        return ids;
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

