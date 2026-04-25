package com.example.chesh.network.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chesh.R;
import com.example.chesh.network.models.ConversationItem;

import java.util.List;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ViewHolder> {

    public interface ConversationInteractionListener {
        void onConversationClicked(ConversationItem item, int position);
    }

    private final Context context;
    private final List<ConversationItem> items;
    private final ConversationInteractionListener listener;

    public ConversationsAdapter(Context context, List<ConversationItem> items, ConversationInteractionListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ConversationItem item = items.get(position);

        // Show the other participant's name (like Instagram DM list)
        if (item.otherUser != null && item.otherUser.name != null && !item.otherUser.name.isEmpty()) {
            h.tvTitle.setText(item.otherUser.name);
        } else if (item.otherUser != null && item.otherUser.email != null) {
            h.tvTitle.setText(item.otherUser.email.split("@")[0]);
        } else {
            h.tvTitle.setText("direct".equals(item.conversationType) ? "Direct Message" : "Group");
        }
        h.tvSubtitle.setText(formatTime(item.updatedAt));

        // Show other user's avatar
        if (item.otherUser != null && item.otherUser.avatarUrl != null && !item.otherUser.avatarUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(context).load(item.otherUser.avatarUrl).circleCrop().into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(android.R.color.darker_gray);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClicked(item, position);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    private String formatTime(String isoTime) {
        if (isoTime == null) return "";
        try { return isoTime.substring(0, 10); } catch (Exception e) { return ""; }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvTitle, tvSubtitle;

        ViewHolder(View v) {
            super(v);
            ivAvatar   = v.findViewById(R.id.ivConvAvatar);
            tvTitle    = v.findViewById(R.id.tvConvTitle);
            tvSubtitle = v.findViewById(R.id.tvConvSubtitle);
        }
    }
}
