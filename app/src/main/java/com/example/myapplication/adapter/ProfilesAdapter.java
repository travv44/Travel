package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.db.entity.UserEntity;

import java.util.List;

public class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.ProfileVH> {

    public interface Listener {
        void onSelect(UserEntity user);
    }

    private List<UserEntity> users;
    private long activeUserId;
    private final Listener listener;

    public ProfilesAdapter(List<UserEntity> users, long activeUserId, Listener listener) {
        this.users = users;
        this.activeUserId = activeUserId;
        this.listener = listener;
    }

    public void update(List<UserEntity> newUsers, long activeUserId) {
        this.users = newUsers;
        this.activeUserId = activeUserId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProfileVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile, parent, false);
        return new ProfileVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileVH holder, int position) {
        UserEntity u = users.get(position);
        holder.username.setText(u.username);
        boolean active = u.id == activeUserId;
        holder.hint.setText(active ? "Активный профиль" : "Нажмите, чтобы сделать активным");
        holder.hint.setTextColor(holder.itemView.getResources().getColor(active ? R.color.teal_500 : R.color.gray_500));

        holder.card.setOnClickListener(v -> {
            if (listener != null) listener.onSelect(u);
        });
    }

    @Override
    public int getItemCount() {
        return users != null ? users.size() : 0;
    }

    static class ProfileVH extends RecyclerView.ViewHolder {
        CardView card;
        TextView username;
        TextView hint;

        ProfileVH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.profileCard);
            username = itemView.findViewById(R.id.profileUsername);
            hint = itemView.findViewById(R.id.profileHint);
        }
    }
}

