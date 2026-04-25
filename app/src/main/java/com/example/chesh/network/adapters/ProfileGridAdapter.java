package com.example.chesh.network.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chesh.R;
import com.example.chesh.network.models.UserPost;

import java.util.List;

public class ProfileGridAdapter extends RecyclerView.Adapter<ProfileGridAdapter.ViewHolder> {

    public interface ProfileGridInteractionListener {
        void onImageClicked(UserPost post, int position);
    }

    private final Context context;
    private final List<UserPost> posts;
    private final ProfileGridInteractionListener listener;

    public ProfileGridAdapter(Context context, List<UserPost> posts, ProfileGridInteractionListener listener) {
        this.context = context;
        this.posts = posts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_profile_grid, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        UserPost post = posts.get(position);
        if (post.PostMedia != null && !post.PostMedia.isEmpty()) {
            String url = post.PostMedia.get(0).mediaUrl;
            if (!TextUtils.isEmpty(url)) {
                Glide.with(context).load(url).centerCrop().into(h.ivPhoto);
            } else {
                h.ivPhoto.setImageResource(getFallbackImage(position));
            }
        } else {
            h.ivPhoto.setImageResource(getFallbackImage(position));
        }

        h.ivPhoto.setOnClickListener(v -> {
            if (listener != null) listener.onImageClicked(post, position);
        });
    }

    @Override
    public int getItemCount() {
        return posts == null ? 0 : posts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;

        ViewHolder(View v) {
            super(v);
            ivPhoto = v.findViewById(R.id.ivProfileGridPhoto);
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
