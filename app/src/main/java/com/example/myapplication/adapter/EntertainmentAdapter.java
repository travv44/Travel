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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EntertainmentAdapter extends RecyclerView.Adapter<EntertainmentAdapter.EntertainmentViewHolder> {

    private List<EntertainmentPlace> places;
    private OnEntertainmentClickListener listener;
    private OnAddToTripListener addToTripListener;
    private Set<String> addedPlaceIds;

    public interface OnEntertainmentClickListener {
        void onEntertainmentClick(EntertainmentPlace place);
    }

    public interface OnAddToTripListener {
        void onAddToTrip(EntertainmentPlace place, boolean added);
    }

    public EntertainmentAdapter(List<EntertainmentPlace> places, OnEntertainmentClickListener listener) {
        this.places = places;
        this.listener = listener;
        this.addedPlaceIds = new HashSet<>();
    }

    public void setAddToTripListener(OnAddToTripListener addToTripListener) {
        this.addToTripListener = addToTripListener;
    }

    public void setAddedPlaceIds(Set<String> addedPlaceIds) {
        this.addedPlaceIds = addedPlaceIds != null ? addedPlaceIds : new HashSet<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EntertainmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity, parent, false);
        return new EntertainmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntertainmentViewHolder holder, int position) {
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

        // Загружаем изображение
        if (place.getImageUrl() != null && !place.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(place.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(holder.imageView);
        } else {
            holder.imageView.setImageResource(R.drawable.ic_launcher_background);
        }

        boolean isAdded = addedPlaceIds.contains(place.getId());
        updateAddButton(holder, isAdded);

        holder.addButton.setOnClickListener(v -> {
            boolean newState = !isAdded;
            updateAddButton(holder, newState);
            if (addToTripListener != null) {
                addToTripListener.onAddToTrip(place, newState);
            }
        });

        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEntertainmentClick(place);
            }
        });
    }

    private void updateAddButton(EntertainmentViewHolder holder, boolean isAdded) {
        if (isAdded) {
            // Включено в поездку: галочка
            holder.addButton.setImageResource(android.R.drawable.checkbox_on_background);
            holder.addButton.setContentDescription("Удалить из поездки");
        } else {
            // Не включено: плюс
            holder.addButton.setImageResource(android.R.drawable.ic_input_add);
            holder.addButton.setContentDescription("Включить в поездку");
        }
    }

    @Override
    public int getItemCount() {
        return places != null ? places.size() : 0;
    }

    public void updatePlaces(List<EntertainmentPlace> newPlaces) {
        this.places = newPlaces;
        notifyDataSetChanged();
    }

    static class EntertainmentViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView imageView;
        ImageButton addButton;
        TextView nameText;
        TextView descriptionText;
        TextView addressText;

        EntertainmentViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.activityCard);
            imageView = itemView.findViewById(R.id.activityImage);
            addButton = itemView.findViewById(R.id.addToTripButton);
            nameText = itemView.findViewById(R.id.activityName);
            descriptionText = itemView.findViewById(R.id.activityDescription);
            addressText = itemView.findViewById(R.id.activityAddress);
        }
    }
}

