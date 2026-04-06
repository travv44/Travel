package com.example.myapplication;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.adapter.PlanAdapter;
import com.example.myapplication.databinding.ActivityPlanBinding;
import com.example.myapplication.model.EntertainmentPlace;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.FirebaseTripHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PlanActivity extends AppCompatActivity {

    public static void start(android.content.Context context, String tripId) {
        android.content.Intent intent = new android.content.Intent(context, PlanActivity.class);
        intent.putExtra("trip_id", tripId);
        context.startActivity(intent);
    }

    private ActivityPlanBinding binding;
    private Trip trip;
    private String tripId;
    private PlanAdapter planAdapter;
    private FirebaseTripHelper firebaseTripHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseTripHelper = FirebaseTripHelper.getInstance();
        tripId = getIntent().getStringExtra("trip_id");
        loadTrip();

        setupList();
        setupToolbar();
        updateEmptyState();
    }

    private void setupToolbar() {
        binding.toolbarTitle.setText(trip != null ? trip.getDestination() : "План поездки");
        binding.backButton.setOnClickListener(v -> finish());
    }

    private void loadTrip() {
        if (tripId == null) return;
        firebaseTripHelper.loadTrip(tripId, new FirebaseTripHelper.OnTripLoadedCallback() {
            @Override
            public void onTripLoaded(Trip loadedTrip) {
                runOnUiThread(() -> {
                    trip = loadedTrip;
                    setupToolbar();
                    refreshList();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(PlanActivity.this, "Поездка не найдена", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void setupList() {
        binding.planRecycler.setLayoutManager(new LinearLayoutManager(this));
        List<EntertainmentPlace> data = new ArrayList<>();
        if (trip != null && trip.getFavoritePlaces() != null) {
            data.addAll(trip.getFavoritePlaces());
        }
        sortByPlannedDateTime(data);

        planAdapter = new PlanAdapter(data, this::onPickDateTime);
        binding.planRecycler.setAdapter(planAdapter);
    }

    private void onPickDateTime(EntertainmentPlace place) {
        // Показываем диалог с описанием достопримечательности
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(place.getName());
        
        // Формируем текст описания
        StringBuilder descriptionText = new StringBuilder();
        if (place.getDescription() != null && !place.getDescription().isEmpty()) {
            descriptionText.append(place.getDescription());
        }
        if (place.getAddress() != null && !place.getAddress().isEmpty()) {
            if (descriptionText.length() > 0) {
                descriptionText.append("\n\n");
            }
            descriptionText.append("📍 ").append(place.getAddress());
        }
        if (descriptionText.length() == 0) {
            descriptionText.append("Достопримечательность в ").append(trip != null ? trip.getDestination() : "выбранном месте");
        }
        
        builder.setMessage(descriptionText.toString());
        
        // Кнопка выбора даты
        builder.setPositiveButton("Выбрать дату и время", (dialog, which) -> {
            showDateTimePicker(place);
        });
        
        // Кнопка отмены
        builder.setNegativeButton("Отмена", null);
        
        builder.show();
    }
    
    private void showDateTimePicker(EntertainmentPlace place) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    TimePickerDialog tp = new TimePickerDialog(
                            this,
                            (timeView, hour, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hour);
                                calendar.set(Calendar.MINUTE, minute);
                                SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                                SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                String plannedDate = df.format(calendar.getTime());
                                String plannedTime = tf.format(calendar.getTime());
                                place.setPlannedDate(plannedDate);
                                place.setPlannedTime(plannedTime);
                                if (tripId != null && place.getId() != null) {
                                    firebaseTripHelper.updatePlaceDateTime(tripId, place.getId(), plannedDate, plannedTime, null);
                                }
                                refreshList();
                                Toast.makeText(this, "Дата и время назначены", Toast.LENGTH_SHORT).show();
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    tp.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    private void refreshList() {
        if (planAdapter == null) return;
        List<EntertainmentPlace> data = new ArrayList<>();
        if (trip != null && trip.getFavoritePlaces() != null) {
            data.addAll(trip.getFavoritePlaces());
        }
        sortByPlannedDateTime(data);
        planAdapter.update(data);
        updateEmptyState();
    }

    private void sortByPlannedDateTime(List<EntertainmentPlace> list) {
        Collections.sort(list, new Comparator<EntertainmentPlace>() {
            @Override
            public int compare(EntertainmentPlace o1, EntertainmentPlace o2) {
                // Без даты — в конец
                boolean d1 = o1.getPlannedDate() != null && o1.getPlannedTime() != null;
                boolean d2 = o2.getPlannedDate() != null && o2.getPlannedTime() != null;
                if (d1 && d2) {
                    String dt1 = o1.getPlannedDate() + " " + o1.getPlannedTime();
                    String dt2 = o2.getPlannedDate() + " " + o2.getPlannedTime();
                    return dt1.compareTo(dt2);
                } else if (d1) {
                    return -1;
                } else if (d2) {
                    return 1;
                } else {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = trip == null || trip.getFavoritePlaces() == null || trip.getFavoritePlaces().isEmpty();
        binding.planEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.planRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}

