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
import com.example.chesh.network.models.FeedPost;

import java.util.List;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {
    public interface FeedInteractionListener {
        void onLikeClicked(FeedPost post, int position);
        void onCommentClicked(FeedPost post, int position);
        void onImageClicked(FeedPost post, int position);
    }

    private final Context context;
    private final List<FeedPost> posts;
    private final FeedInteractionListener listener;

    public FeedAdapter(Context context, List<FeedPost> posts, FeedInteractionListener listener) {
        this.context = context;
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_feed_post, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        FeedPost post = posts.get(position);

        // Username
        String name = (post.user != null && !TextUtils.isEmpty(post.user.name))
                ? post.user.name : "Unknown";
        h.tvUsername.setText(name);

        // Avatar
        if (post.user != null && !TextUtils.isEmpty(post.user.avatarUrl)) {
            Glide.with(context).load(post.user.avatarUrl).circleCrop().into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(android.R.color.darker_gray);
        }

        // Post image
        if (post.media != null && !post.media.isEmpty()) {
            String mediaUrl = post.media.get(0);
            android.util.Log.d("FeedAdapter", "Loading image for post " + post.id + ": " + mediaUrl);
            if (!TextUtils.isEmpty(mediaUrl)) {
                Glide.with(context)
                    .load(mediaUrl)
                    .error(android.R.drawable.ic_menu_report_image)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                                                   com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                                   boolean isFirstResource) {
                            android.util.Log.e("FeedAdapter", "Failed to load image: " + mediaUrl, e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, 
                                                      com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                                      com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            android.util.Log.d("FeedAdapter", "Image loaded successfully: " + mediaUrl);
                            return false;
                        }
                    })
                    .centerCrop()
                    .into(h.ivPhoto);
            } else {
                h.ivPhoto.setImageResource(getFallbackImage(position));
            }
        } else {
            h.ivPhoto.setImageResource(getFallbackImage(position));
        }

        if (post.hasLiked) {
            h.tvLikes.setText("♥ " + post.likesCount);
            h.tvLikes.setTextColor(android.graphics.Color.parseColor("#E91E63")); // Pink
        } else {
            h.tvLikes.setText("♡ " + post.likesCount);
            h.tvLikes.setTextColor(context.getResources().getColor(R.color.pastel_text_primary));
        }
        h.tvComments.setText("💬 " + post.commentsCount);
        h.tvLikes.setOnClickListener(v -> {
            if (listener != null) listener.onLikeClicked(post, position);
        });
        h.tvComments.setOnClickListener(v -> {
            if (listener != null) listener.onCommentClicked(post, position);
        });
        // Tap photo → open post detail
        h.ivPhoto.setOnClickListener(v -> {
            if (listener != null) listener.onImageClicked(post, position);
        });
        // Tap row → open post detail
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onImageClicked(post, position);
        });

        // Caption
        if (!TextUtils.isEmpty(post.caption)) {
            h.tvCaption.setText(name + "  " + post.caption);
            h.tvCaption.setVisibility(View.VISIBLE);
        } else {
            h.tvCaption.setVisibility(View.GONE);
        }

        // Time
        h.tvTime.setText(formatTime(post.createdAt));
    }

    @Override
    public int getItemCount() {
        return posts == null ? 0 : posts.size();
    }

    private String formatTime(String isoTime) {
        if (isoTime == null) return "";
        // Simple display — just show the date portion
        try {
            return isoTime.substring(0, 10);
        } catch (Exception e) {
            return "";
        }
    }

    private int getFallbackImage(int position) {
        int[] drawables = {
                R.drawable.photo_city,
                R.drawable.photo_sunset,
                R.drawable.photo_forest,
                R.drawable.photo_mountain,
                R.drawable.photo_waterfall,
                R.drawable.photo_flower
        };
        return drawables[Math.abs(position) % drawables.length];
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar, ivPhoto;
        TextView tvUsername, tvTime, tvLikes, tvComments, tvCaption;

        ViewHolder(View v) {
            super(v);
            ivAvatar    = v.findViewById(R.id.ivFeedAvatar);
            ivPhoto     = v.findViewById(R.id.ivFeedPhoto);
            tvUsername  = v.findViewById(R.id.tvFeedUsername);
            tvTime      = v.findViewById(R.id.tvFeedTime);
            tvLikes     = v.findViewById(R.id.tvFeedLikes);
            tvComments  = v.findViewById(R.id.tvFeedComments);
            tvCaption   = v.findViewById(R.id.tvFeedCaption);
        }
    }
}
