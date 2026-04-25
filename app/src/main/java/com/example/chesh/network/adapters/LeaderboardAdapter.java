package com.example.chesh.network.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chesh.R;
import com.example.chesh.network.models.LeaderboardEntry;

import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private final Context context;
    private final List<LeaderboardEntry> entries;
    private final long currentUserId;

    public LeaderboardAdapter(Context context, List<LeaderboardEntry> entries, long currentUserId) {
        this.context = context;
        this.entries = entries;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_leaderboard_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        LeaderboardEntry entry = entries.get(position);
        int rank = position + 1;

        // Rank medal for top 3
        switch (rank) {
            case 1: h.tvRank.setText("🥇"); break;
            case 2: h.tvRank.setText("🥈"); break;
            case 3: h.tvRank.setText("🥉"); break;
            default: h.tvRank.setText(String.valueOf(rank)); break;
        }

        // Name
        String name = (entry.User != null && !TextUtils.isEmpty(entry.User.name))
                ? entry.User.name : "User #" + (entry.User != null ? entry.User.id : "?");
        h.tvName.setText(name);

        // Highlight current user
        boolean isMe = entry.User != null && entry.User.id == currentUserId;
        h.tvName.setTextColor(isMe
                ? context.getResources().getColor(R.color.pastel_accent_magenta, null)
                : context.getResources().getColor(R.color.pastel_text_primary, null));

        // Avatar
        if (entry.User != null && !TextUtils.isEmpty(entry.User.avatarUrl)) {
            Glide.with(context).load(entry.User.avatarUrl).circleCrop().into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(android.R.color.darker_gray);
        }

        // Points
        h.tvPoints.setText(entry.points + " pts");
    }

    @Override
    public int getItemCount() {
        return entries == null ? 0 : entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvPoints;
        ImageView ivAvatar;

        ViewHolder(View v) {
            super(v);
            tvRank   = v.findViewById(R.id.tvLbRank);
            tvName   = v.findViewById(R.id.tvLbName);
            tvPoints = v.findViewById(R.id.tvLbPoints);
            ivAvatar = v.findViewById(R.id.ivLbAvatar);
        }
    }
}
