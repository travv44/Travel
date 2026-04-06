package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.model.EntertainmentPlace;

import java.util.List;

public class FavoritePlacesAdapter extends RecyclerView.Adapter<FavoritePlacesAdapter.FavViewHolder> {

    public interface Listener {
        void onClick(EntertainmentPlace place);
        void onRemove(EntertainmentPlace place);
    }

    private List<EntertainmentPlace> places;
    private final Listener listener;

    public FavoritePlacesAdapter(List<EntertainmentPlace> places, Listener listener) {
        this.places = places;
        this.listener = listener;
    }

    public void update(List<EntertainmentPlace> newPlaces) {
        this.places = newPlaces;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FavViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity, parent, false);
        return new FavViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FavViewHolder holder, int position) {
        EntertainmentPlace place = places.get(position);
        holder.nameText.setText(place.getName());

        if (place.getDescription() != null && !place.getDescription().isEmpty()) {
            holder.descriptionText.setText(place.getDescription());
            holder.descriptionText.setVisibility(View.VISIBLE);
        } else {
            holder.descriptionText.setVisibility(View.GONE);
        }

        if (place.getAddress() != null && !place.getAddress().isEmpty()) {
            holder.addressText.setText(place.getAddress());
            holder.addressText.setVisibility(View.VISIBLE);
        } else {
            holder.addressText.setVisibility(View.GONE);
        }

        if (place.getImageUrl() != null && !place.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(place.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(holder.imageView);
        } else {
            holder.imageView.setImageResource(R.drawable.ic_launcher_background);
        }

        holder.removeButton.setImageResource(android.R.drawable.ic_delete);
        holder.removeButton.setContentDescription("Удалить из избранного");
        holder.removeButton.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(place);
        });

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(place);
        });
    }

    @Override
    public int getItemCount() {
        return places != null ? places.size() : 0;
    }

    static class FavViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView imageView;
        ImageButton removeButton;
        TextView nameText;
        TextView descriptionText;
        TextView addressText;

        FavViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.activityCard);
            imageView = itemView.findViewById(R.id.activityImage);
            removeButton = itemView.findViewById(R.id.addToTripButton);
            nameText = itemView.findViewById(R.id.activityName);
            descriptionText = itemView.findViewById(R.id.activityDescription);
            addressText = itemView.findViewById(R.id.activityAddress);
        }
    }
}

