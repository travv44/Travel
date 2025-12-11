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
import com.example.myapplication.utils.TripStorage;

import java.util.List;

public class ExploreFragment extends Fragment {

    private FragmentExploreBinding binding;
    private TripStorage tripStorage;
    private TripAdapter tripAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        
        tripStorage = new TripStorage(requireContext());
        
        setupCreateButton();
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
        
        tripAdapter = new TripAdapter(tripStorage.getAllTrips(), trip -> {
            Intent intent = new Intent(getContext(), TripDetailActivity.class);
            intent.putExtra("trip_id", trip.getId());
            startActivity(intent);
        });
        
        recyclerView.setAdapter(tripAdapter);
        updateTripsList();
    }

    private void updateTripsList() {
        List<Trip> trips = tripStorage.getAllTrips();
        tripAdapter.updateTrips(trips);
        
        if (trips.isEmpty()) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.tripsRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
            binding.tripsRecyclerView.setVisibility(View.VISIBLE);
        }
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

