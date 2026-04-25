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
import com.example.chesh.network.models.CommentItem;

import java.util.List;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.ViewHolder> {

    public interface OnCommentUserClickListener {
        void onUserClicked(long userId);
    }

    private final Context context;
    private final List<CommentItem> items;
    private final OnCommentUserClickListener listener;

    /** Constructor with click listener (for tapping commenter name → profile) */
    public CommentsAdapter(Context context, List<CommentItem> items, OnCommentUserClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    /** Legacy constructor (no-op listener) */
    public CommentsAdapter(Context context, List<CommentItem> items) {
        this(context, items, null);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        CommentItem item = items.get(position);

        // Author name
        String name = (item.User != null && !TextUtils.isEmpty(item.User.name))
                ? item.User.name
                : (item.User != null && item.User.email != null ? item.User.email.split("@")[0] : "User");
        h.tvAuthor.setText(name);
        h.tvComment.setText(item.commentText != null ? item.commentText : "");

        // Avatar
        if (item.User != null && !TextUtils.isEmpty(item.User.avatarUrl)) {
            Glide.with(context).load(item.User.avatarUrl).circleCrop().into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(android.R.color.darker_gray);
        }

        // Tap author name or avatar → open their profile
        if (listener != null && item.User != null) {
            final long userId = item.User.id;
            View.OnClickListener profileClick = v -> listener.onUserClicked(userId);
            h.tvAuthor.setOnClickListener(profileClick);
            h.ivAvatar.setOnClickListener(profileClick);
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvAuthor, tvComment;

        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar  = itemView.findViewById(R.id.ivCommentAvatar);
            tvAuthor  = itemView.findViewById(R.id.tvCommentAuthor);
            tvComment = itemView.findViewById(R.id.tvCommentText);
        }
    }
}
