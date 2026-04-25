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

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {

    public interface GalleryInteractionListener {
        void onImageClicked(FeedPost post, int position);
    }

    private final Context context;
    private final List<FeedPost> posts;
    private final GalleryInteractionListener listener;

    public GalleryAdapter(Context context, List<FeedPost> posts, GalleryInteractionListener listener) {
        this.context = context;
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_gallery_tile, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        FeedPost post = posts.get(position);

        if (post.media != null && !post.media.isEmpty()) {
            String mediaUrl = post.media.get(0);
            if (!TextUtils.isEmpty(mediaUrl)) {
                Glide.with(context).load(mediaUrl).centerCrop().into(h.ivPhoto);
            } else {
                h.ivPhoto.setImageResource(getFallbackImage(position));
            }
        } else {
            h.ivPhoto.setImageResource(getFallbackImage(position));
        }

        h.ivPhoto.setOnClickListener(v -> {
            if (listener != null) listener.onImageClicked(post, position);
        });

        String name = (post.user != null && !TextUtils.isEmpty(post.user.name))
                ? post.user.name : "";
        h.tvUser.setText(name);
        h.tvUser.setVisibility(TextUtils.isEmpty(name) ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return posts == null ? 0 : posts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvUser;

        ViewHolder(View v) {
            super(v);
            ivPhoto = v.findViewById(R.id.ivGalleryPhoto);
            tvUser  = v.findViewById(R.id.tvGalleryUser);
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
}
