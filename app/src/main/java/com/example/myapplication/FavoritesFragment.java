package com.example.myapplication;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.databinding.FragmentFavoritesBinding;
import com.example.myapplication.adapter.TripAdapter;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.FirebaseTripHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FavoritesFragment extends Fragment {

    private FragmentFavoritesBinding binding;
    private TripAdapter adapter;
    private FirebaseTripHelper firebaseTripHelper;
    private final java.util.List<Trip> allTrips = new java.util.ArrayList<>();
    private boolean sortAsc = true;
    private final java.util.Set<String> favoriteTripIds = new java.util.HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false);
        firebaseTripHelper = FirebaseTripHelper.getInstance();

        adapter = new TripAdapter(new java.util.ArrayList<>(), trip -> {
            android.content.Intent intent = new android.content.Intent(getContext(), TripDetailActivity.class);
            intent.putExtra("trip_id", trip.getId());
            startActivity(intent);
        });
        // В избранных нельзя управлять звёздочкой — только просмотр + удаление поездки
        adapter.setShowFavoriteButton(false);
        adapter.setOnDeleteTripListener(trip -> {
            if (trip == null || trip.getId() == null) return;
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Удалить поездку?")
                    .setMessage("Поездка будет удалена безвозвратно.")
                    .setPositiveButton("Удалить", (d, which) -> {
                        firebaseTripHelper.deleteTrip(trip.getId(), new FirebaseTripHelper.OnDeleteCallback() {
                            @Override
                            public void onSuccess() {
                                // также удалим из избранных, если была
                                FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/")
                                        .getReference("users").child(uid).child("favoriteTrips").child(trip.getId())
                                        .removeValue();
                                favoriteTripIds.remove(trip.getId());
                                for (int i = 0; i < allTrips.size(); i++) {
                                    Trip t = allTrips.get(i);
                                    if (t != null && trip.getId().equals(t.getId())) {
                                        allTrips.remove(i);
                                        break;
                                    }
                                }
                                if (!isAdded() || binding == null) return;
                                requireActivity().runOnUiThread(() ->
                                        applyFilterAndSort(binding.favSearch.getText() != null ? binding.favSearch.getText().toString() : ""));
                            }

                            @Override
                            public void onError(String error) {
                                if (!isAdded() || binding == null) return;
                                requireActivity().runOnUiThread(() ->
                                        android.widget.Toast.makeText(requireContext(), "Не удалось удалить: " + error, android.widget.Toast.LENGTH_SHORT).show());
                            }
                        });
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        binding.favRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.favRecycler.setAdapter(adapter);
        binding.favTitle.setOnClickListener(v -> {
            sortAsc = !sortAsc;
            applyFilterAndSort(binding.favSearch.getText() != null ? binding.favSearch.getText().toString() : "");
        });
        binding.favSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilterAndSort(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            reload();
        }
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Если избранное изменили на главной вкладке — обновим список
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            reload();
        }
    }

    public void refreshNow() {
        if (!isAdded() || binding == null) return;
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            reload();
        } else {
            render(new java.util.ArrayList<>());
        }
    }

    private void reload() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            render(new java.util.ArrayList<>());
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // 1) загрузим избранные id
        FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/")
                .getReference("users").child(uid).child("favoriteTrips")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        favoriteTripIds.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            favoriteTripIds.add(child.getKey());
                        }
                        if (!isAdded() || binding == null) return;
                        requireActivity().runOnUiThread(() -> adapter.setFavoriteTripIds(new java.util.HashSet<>(favoriteTripIds)));

                        // 2) загрузим все поездки пользователя и отфильтруем по favoriteTripIds
                        firebaseTripHelper.loadUserTrips(uid, new FirebaseTripHelper.OnTripsLoadedCallback() {
                            @Override
                            public void onTripsLoaded(java.util.List<Trip> trips) {
                                if (!isAdded() || binding == null) return;
                                requireActivity().runOnUiThread(() -> {
                                    allTrips.clear();
                                    if (trips != null) {
                                        for (Trip t : trips) {
                                            if (favoriteTripIds.contains(t.getId())) {
                                                allTrips.add(t);
                                            }
                                        }
                                    }
                                    applyFilterAndSort(binding.favSearch.getText() != null ? binding.favSearch.getText().toString() : "");
                                });
                            }

                            @Override
                            public void onError(String error) {
                                if (!isAdded() || binding == null) return;
                                requireActivity().runOnUiThread(() -> render(new java.util.ArrayList<>()));
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (!isAdded() || binding == null) return;
                        requireActivity().runOnUiThread(() -> render(new java.util.ArrayList<>()));
                    }
                });
    }

    private void render(java.util.List<Trip> trips) {
        adapter.updateTrips(trips);
        boolean empty = trips == null || trips.isEmpty();
        binding.favEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.favRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void applyFilterAndSort(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        java.util.List<Trip> filtered = new java.util.ArrayList<>();
        for (Trip t : allTrips) {
            String name = t.getName() != null ? t.getName().toLowerCase() : "";
            String dest = t.getDestination() != null ? t.getDestination().toLowerCase() : "";
            if (q.isEmpty() || name.contains(q) || dest.contains(q)) {
                filtered.add(t);
            }
        }
        java.util.Collections.sort(filtered, (a, b) -> {
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            return sortAsc ? an.compareToIgnoreCase(bn) : bn.compareToIgnoreCase(an);
        });
        render(filtered);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

