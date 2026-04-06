package com.example.myapplication.adapter;

import android.content.Intent;
import android.net.Uri;
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
import com.example.myapplication.model.RecommendedPlace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.Holder> {

    private final List<RecommendedPlace> items = new ArrayList<>();

    public void submit(List<RecommendedPlace> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation_place, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        RecommendedPlace rp = items.get(position);
        EntertainmentPlace p = rp.getPlace();
        holder.name.setText(p.getName() != null ? p.getName() : "—");
        holder.reason.setText(rp.getReason() != null ? rp.getReason() : "");

        Double dKm = rp.getDistanceKm();
        if (dKm != null && !dKm.isNaN()) {
            holder.distanceBadge.setVisibility(View.VISIBLE);
            holder.distanceBadge.setText(formatDistanceKm(dKm));
        } else {
            holder.distanceBadge.setVisibility(View.GONE);
        }

        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
            Glide.with(holder.image.getContext())
                    .load(p.getImageUrl())
                    .centerCrop()
                    .placeholder(R.color.gray_200)
                    .into(holder.image);
        } else {
            holder.image.setImageResource(R.color.gray_200);
        }

        holder.itemView.setOnClickListener(v -> openOnMap(v.getContext(), p));
    }

    private static String formatDistanceKm(double km) {
        if (km < 1.0) {
            int m = (int) Math.round(km * 1000);
            return m + " м";
        }
        if (km < 10) {
            return String.format(Locale.getDefault(), "%.1f км", km);
        }
        return String.format(Locale.getDefault(), "%.0f км", km);
    }

    private static void openOnMap(android.content.Context context, EntertainmentPlace p) {
        String lat = p.getLat();
        String lon = p.getLon();
        if (lat == null || lon == null || lat.isEmpty() || lon.isEmpty()) {
            return;
        }
        String label = p.getName() != null ? p.getName() : "";
        Uri uri = Uri.parse("geo:" + lat + "," + lon + "?q=" + Uri.encode(lat + "," + lon + "(" + label + ")"));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView reason;
        final TextView distanceBadge;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.recImage);
            name = itemView.findViewById(R.id.recName);
            reason = itemView.findViewById(R.id.recReason);
            distanceBadge = itemView.findViewById(R.id.recDistanceBadge);
        }
    }
}
