package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.TripAdapter;
import com.example.myapplication.databinding.FragmentExploreBinding;
import com.example.myapplication.model.Trip;
import com.example.myapplication.utils.FirebaseTripHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ExploreFragment extends Fragment {

    private FragmentExploreBinding binding;
    private FirebaseTripHelper firebaseTripHelper;
    private TripAdapter tripAdapter;
    private final java.util.Set<String> favoriteTripIds = new java.util.HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        firebaseTripHelper = FirebaseTripHelper.getInstance();

        setupCreateButton();
        hidePersonalizationBlock();
        setupTripsList();

        return binding.getRoot();
    }

    private void setupCreateButton() {
        binding.createTripButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), CreateTripActivity.class);
            startActivity(intent);
        });
    }

    private void setupTripsList() {
        RecyclerView recyclerView = binding.tripsRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        tripAdapter = new TripAdapter(new ArrayList<>(), trip -> {
            Intent intent = new Intent(getContext(), TripDetailActivity.class);
            intent.putExtra("trip_id", trip.getId());
            startActivity(intent);
        });
        tripAdapter.setOnDeleteTripListener(trip -> {
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
                                // также удалить из избранных (если была)
                                FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/")
                                        .getReference("users").child(uid).child("favoriteTrips").child(trip.getId())
                                        .removeValue();
                                if (!isAdded() || binding == null) return;
                                requireActivity().runOnUiThread(() -> updateTripsList());
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
        tripAdapter.setOnFavoriteTripListener((trip, favorite) -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            com.google.firebase.database.DatabaseReference ref = FirebaseDatabase
                    .getInstance("https://nosql-562de-default-rtdb.firebaseio.com/")
                    .getReference("users").child(uid).child("favoriteTrips").child(trip.getId());
            if (favorite) favoriteTripIds.add(trip.getId()); else favoriteTripIds.remove(trip.getId());
            tripAdapter.setFavoriteTripIds(new java.util.HashSet<>(favoriteTripIds));

            if (favorite) {
                ref.setValue(System.currentTimeMillis())
                        .addOnFailureListener(e -> {
                            favoriteTripIds.remove(trip.getId());
                            if (isAdded() && binding != null) {
                                requireActivity().runOnUiThread(() -> tripAdapter.setFavoriteTripIds(new java.util.HashSet<>(favoriteTripIds)));
                            }
                        });
            } else {
                ref.removeValue()
                        .addOnFailureListener(e -> {
                            favoriteTripIds.add(trip.getId());
                            if (isAdded() && binding != null) {
                                requireActivity().runOnUiThread(() -> tripAdapter.setFavoriteTripIds(new java.util.HashSet<>(favoriteTripIds)));
                            }
                        });
            }
        });

        recyclerView.setAdapter(tripAdapter);
        updateTripsList();
    }

    private void updateTripsList() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.tripsRecyclerView.setVisibility(View.GONE);
            hidePersonalizationBlock();
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firebaseTripHelper.loadUserTrips(uid, new FirebaseTripHelper.OnTripsLoadedCallback() {
            @Override
            public void onTripsLoaded(List<Trip> trips) {
                if (!isAdded() || binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    tripAdapter.updateTrips(trips);
                    loadFavoriteTripIds(uid);
                    if (trips.isEmpty()) {
                        binding.emptyStateText.setVisibility(View.VISIBLE);
                        binding.tripsRecyclerView.setVisibility(View.GONE);
                    } else {
                        binding.emptyStateText.setVisibility(View.GONE);
                        binding.tripsRecyclerView.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded() || binding == null) return;
                requireActivity().runOnUiThread(() -> {
                    binding.emptyStateText.setVisibility(View.VISIBLE);
                    binding.tripsRecyclerView.setVisibility(View.GONE);
                });
            }
        });
    }

    private void hidePersonalizationBlock() {
        if (binding == null) return;
        binding.recommendationsHeader.setVisibility(View.GONE);
        binding.recommendationsHint.setVisibility(View.GONE);
        binding.recommendationsRecyclerView.setVisibility(View.GONE);
        binding.recommendationsEmpty.setVisibility(View.GONE);
    }

    private void loadFavoriteTripIds(String uid) {
        FirebaseDatabase.getInstance("https://nosql-562de-default-rtdb.firebaseio.com/")
                .getReference("users").child(uid).child("favoriteTrips")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        java.util.Set<String> ids = new java.util.HashSet<>();
                        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                            ids.add(child.getKey());
                        }
                        favoriteTripIds.clear();
                        favoriteTripIds.addAll(ids);
                        if (!isAdded() || binding == null) return;
                        requireActivity().runOnUiThread(() -> tripAdapter.setFavoriteTripIds(ids));
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tripAdapter != null) {
            updateTripsList();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
