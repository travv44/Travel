package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.Trip;

import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private List<Trip> trips;
    private OnTripClickListener listener;
    private OnFavoriteTripListener favoriteListener;
    private OnDeleteTripListener deleteListener;
    private java.util.Set<String> favoriteTripIds = new java.util.HashSet<>();
    private boolean showFavoriteButton = true;

    public interface OnTripClickListener {
        void onTripClick(Trip trip);
    }

    public interface OnFavoriteTripListener {
        void onFavoriteTrip(Trip trip, boolean favorite);
    }

    public interface OnDeleteTripListener {
        void onDeleteTrip(Trip trip);
    }

    public TripAdapter(List<Trip> trips, OnTripClickListener listener) {
        this.trips = trips;
        this.listener = listener;
    }

    public void setOnFavoriteTripListener(OnFavoriteTripListener favoriteListener) {
        this.favoriteListener = favoriteListener;
    }

    public void setOnDeleteTripListener(OnDeleteTripListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setShowFavoriteButton(boolean showFavoriteButton) {
        this.showFavoriteButton = showFavoriteButton;
        notifyDataSetChanged();
    }

    public void setFavoriteTripIds(java.util.Set<String> ids) {
        this.favoriteTripIds = ids != null ? ids : new java.util.HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        holder.tripNameText.setText(trip.getName());
        holder.destinationText.setText(trip.getDestination());
        
        if (trip.getDate() != null && !trip.getDate().isEmpty()) {
            holder.dateText.setText(trip.getDate());
            holder.dateText.setVisibility(View.VISIBLE);
        } else {
            holder.dateText.setVisibility(View.GONE);
        }

        boolean isFav = favoriteTripIds.contains(trip.getId());
        if (showFavoriteButton) {
            holder.favoriteTripButton.setVisibility(View.VISIBLE);
            holder.favoriteTripButton.setImageResource(
                    isFav ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off
            );
            holder.favoriteTripButton.setOnClickListener(v -> {
                boolean newFav = !favoriteTripIds.contains(trip.getId());
                if (favoriteListener != null) favoriteListener.onFavoriteTrip(trip, newFav);
            });
        } else {
            holder.favoriteTripButton.setVisibility(View.GONE);
            holder.favoriteTripButton.setOnClickListener(null);
        }

        holder.deleteTripButton.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteTrip(trip);
        });

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTripClick(trip);
            }
        });
    }

    @Override
    public int getItemCount() {
        return trips != null ? trips.size() : 0;
    }

    public void updateTrips(List<Trip> newTrips) {
        this.trips = newTrips;
        notifyDataSetChanged();
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageButton favoriteTripButton;
        ImageButton deleteTripButton;
        TextView tripNameText;
        TextView destinationText;
        TextView dateText;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.tripCard);
            favoriteTripButton = itemView.findViewById(R.id.favoriteTripButton);
            deleteTripButton = itemView.findViewById(R.id.deleteTripButton);
            tripNameText = itemView.findViewById(R.id.tripNameText);
            destinationText = itemView.findViewById(R.id.destinationText);
            dateText = itemView.findViewById(R.id.dateText);
        }
    }
}

