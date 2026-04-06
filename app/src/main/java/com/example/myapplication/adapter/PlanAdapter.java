package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myapplication.R;
import com.example.myapplication.model.EntertainmentPlace;

import java.util.List;

public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PlanViewHolder> {

    public interface OnPickDateTimeListener {
        void onPick(EntertainmentPlace place);
    }

    private List<EntertainmentPlace> places;
    private final OnPickDateTimeListener listener;

    public PlanAdapter(List<EntertainmentPlace> places, OnPickDateTimeListener listener) {
        this.places = places;
        this.listener = listener;
    }

    public void update(List<EntertainmentPlace> newPlaces) {
        this.places = newPlaces;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plan_place, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        EntertainmentPlace place = places.get(position);
        holder.title.setText(place.getName());

        String dateTime = "";
        if (place.getPlannedDate() != null) {
            dateTime += place.getPlannedDate();
        }
        if (place.getPlannedTime() != null) {
            if (!dateTime.isEmpty()) dateTime += " ";
            dateTime += place.getPlannedTime();
        }
        holder.date.setText(dateTime.isEmpty() ? "Назначить дату и время" : dateTime);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(place);
        });

        if (place.getImageUrl() != null && !place.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(place.getImageUrl())
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(holder.image);
        } else {
            holder.image.setImageResource(R.drawable.ic_launcher_background);
        }
    }

    @Override
    public int getItemCount() {
        return places != null ? places.size() : 0;
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView date;

        PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.planPlaceImage);
            title = itemView.findViewById(R.id.planPlaceTitle);
            date = itemView.findViewById(R.id.planPlaceDate);
        }
    }
}

