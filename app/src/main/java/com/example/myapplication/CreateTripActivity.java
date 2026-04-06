package com.example.myapplication;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.databinding.ActivityCreateTripBinding;
import com.example.myapplication.model.PlaceSuggestion;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.FirebaseTripHelper;
import com.example.myapplication.utils.PlaceSearchHelper;
import com.example.myapplication.utils.VisitHistoryHelper;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateTripActivity extends AppCompatActivity {

    private ActivityCreateTripBinding binding;
    private PlaceSearchHelper placeSearchHelper;
    private FirebaseTripHelper firebaseTripHelper;
    private ExecutorService executorService;
    private List<PlaceSuggestion> currentSuggestions;
    private PlaceSuggestion selectedPlace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTripBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        placeSearchHelper = new PlaceSearchHelper();
        firebaseTripHelper = FirebaseTripHelper.getInstance();
        executorService = Executors.newSingleThreadExecutor();
        currentSuggestions = new ArrayList<>();

        setupSearch();
        setupCreateButton();
    }

    private void setupSearch() {
        binding.destinationSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) {
                    searchPlaces(s.toString());
                } else {
                    currentSuggestions.clear();
                    updateSuggestionsAdapter();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.destinationSearch.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentSuggestions.size()) {
                selectedPlace = currentSuggestions.get(position);
                binding.destinationSearch.setText(selectedPlace.getName());
            }
        });
    }

    private void searchPlaces(String query) {
        executorService.execute(() -> {
            List<PlaceSuggestion> suggestions = placeSearchHelper.searchPlaces(query);
            runOnUiThread(() -> {
                currentSuggestions = suggestions;
                updateSuggestionsAdapter();
            });
        });
    }

    private void updateSuggestionsAdapter() {
        List<String> suggestionStrings = new ArrayList<>();
        for (PlaceSuggestion suggestion : currentSuggestions) {
            String display = suggestion.getName();
            if (suggestion.getDescription() != null && !suggestion.getDescription().isEmpty()) {
                display += " - " + suggestion.getDescription();
            }
            suggestionStrings.add(display);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                suggestionStrings
        );
        binding.destinationSearch.setAdapter(adapter);
        if (!suggestionStrings.isEmpty()) {
            binding.destinationSearch.showDropDown();
        }
    }

    private void setupCreateButton() {
        binding.createTripButton.setOnClickListener(v -> {
            String tripName = binding.tripNameInput.getText().toString().trim();
            
            if (tripName.isEmpty()) {
                Toast.makeText(this, "Введите название поездки", Toast.LENGTH_SHORT).show();
                return;
            }

            String destinationText = binding.destinationSearch.getText().toString().trim();
            if (destinationText.isEmpty()) {
                Toast.makeText(this, "Введите место назначения", Toast.LENGTH_SHORT).show();
                return;
            }

            PlaceSuggestion placeToUse = selectedPlace;
            
            // Если место не выбрано из списка, но есть результаты поиска, используем первый
            if (placeToUse == null && !currentSuggestions.isEmpty()) {
                placeToUse = currentSuggestions.get(0);
            }
            
            // Если все еще нет места, выполняем поиск синхронно
            if (placeToUse == null) {
                binding.createTripButton.setEnabled(false);
                binding.createTripButton.setText("Поиск...");
                String finalDestinationText = destinationText;
                String finalTripName = tripName;
                executorService.execute(() -> {
                    List<PlaceSuggestion> suggestions = placeSearchHelper.searchPlaces(finalDestinationText);
                    runOnUiThread(() -> {
                        binding.createTripButton.setEnabled(true);
                        binding.createTripButton.setText("Создать поездку");
                        if (!suggestions.isEmpty()) {
                            PlaceSuggestion foundPlace = suggestions.get(0);
                            createTrip(finalTripName, foundPlace);
                        } else {
                            Toast.makeText(this, "Место не найдено. Попробуйте выбрать из списка.", Toast.LENGTH_LONG).show();
                        }
                    });
                });
                return;
            }

            createTrip(tripName, placeToUse);
        });
    }

    private void createTrip(String tripName, PlaceSuggestion place) {
        Trip trip = new Trip(
                tripName,
                place.getName(),
                place.getLat(),
                place.getLon()
        );
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            trip.setUserId(FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
        binding.createTripButton.setEnabled(false);
        firebaseTripHelper.saveTrip(trip, new FirebaseTripHelper.OnSaveCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    binding.createTripButton.setEnabled(true);
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        VisitHistoryHelper.getInstance().recordTripDestinationVisit(
                                uid,
                                trip.getId(),
                                trip.getDestination(),
                                trip.getDestinationLat(),
                                trip.getDestinationLon(),
                                "create_trip");
                    }
                    Toast.makeText(CreateTripActivity.this, "Поездка создана!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.createTripButton.setEnabled(true);
                    Toast.makeText(CreateTripActivity.this, "Ошибка сохранения: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        binding = null;
    }
}

