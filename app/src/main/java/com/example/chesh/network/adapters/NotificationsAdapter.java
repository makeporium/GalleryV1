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
import com.example.chesh.network.models.NotificationItem;

import java.util.List;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {

    private final Context context;
    private final List<NotificationItem> items;
    private final long myUserId;

    public NotificationsAdapter(Context context, List<NotificationItem> items, long myUserId) {
        this.context = context;
        this.items = items;
        this.myUserId = myUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        NotificationItem item = items.get(position);

        String type = item.notificationType != null ? item.notificationType : "";
        String text;
        
        boolean isOutgoing = (item.actorId == myUserId);
        
        if (isOutgoing) {
            String recipientName = (item.Recipient != null && !TextUtils.isEmpty(item.Recipient.name)) 
                    ? item.Recipient.name : "Someone";
            switch (type) {
                case "like":     text = "You liked " + recipientName + "'s post"; break;
                case "comment":  text = "You commented on " + recipientName + "'s post"; break;
                case "follow":   text = "You started following " + recipientName; break;
                case "message":  text = "You sent a message to " + recipientName; break;
                default:         text = "You — " + type; break;
            }
            // Use recipient avatar for outgoing
            if (item.Recipient != null && !TextUtils.isEmpty(item.Recipient.avatarUrl)) {
                Glide.with(context).load(item.Recipient.avatarUrl).circleCrop().into(h.ivAvatar);
            } else {
                h.ivAvatar.setImageResource(android.R.color.darker_gray);
            }
        } else {
            String actorName = (item.ActorUser != null && !TextUtils.isEmpty(item.ActorUser.name))
                    ? item.ActorUser.name : "Someone";
            switch (type) {
                case "like":     text = actorName + " liked your post"; break;
                case "comment":  text = actorName + " commented on your post"; break;
                case "follow":   text = actorName + " started following you"; break;
                case "message":  text = actorName + " sent you a message"; break;
                default:         text = actorName + " — " + type; break;
            }
            // Use actor avatar for incoming
            if (item.ActorUser != null && !TextUtils.isEmpty(item.ActorUser.avatarUrl)) {
                Glide.with(context).load(item.ActorUser.avatarUrl).circleCrop().into(h.ivAvatar);
            } else {
                h.ivAvatar.setImageResource(android.R.color.darker_gray);
            }
        }
        h.tvText.setText(text);

        // Time
        h.tvTime.setText(formatTime(item.createdAt));
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
        TextView tvText, tvTime;

        ViewHolder(View v) {
            super(v);
            ivAvatar = v.findViewById(R.id.ivNotifAvatar);
            tvText   = v.findViewById(R.id.tvNotifText);
            tvTime   = v.findViewById(R.id.tvNotifTime);
        }
    }
}
