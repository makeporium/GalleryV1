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
import com.example.chesh.network.models.UserDto;

import java.util.List;

public class PostLikersAdapter extends RecyclerView.Adapter<PostLikersAdapter.ViewHolder> {

    public interface OnMessageClickListener {
        void onMessageClicked(UserDto user);
    }

    public interface OnProfileClickListener {
        void onProfileClicked(UserDto user);
    }

    private final Context context;
    private final List<UserDto> users;
    private final OnMessageClickListener messageListener;
    private final OnProfileClickListener profileListener;

    public PostLikersAdapter(Context context, List<UserDto> users,
                             OnMessageClickListener messageListener,
                             OnProfileClickListener profileListener) {
        this.context = context;
        this.users = users;
        this.messageListener = messageListener;
        this.profileListener = profileListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Build row programmatically: [avatar] [name] [Message btn]
        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(32, 20, 32, 20);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));

        ImageView avatar = new ImageView(context);
        android.widget.LinearLayout.LayoutParams avatarParams =
                new android.widget.LinearLayout.LayoutParams(88, 88);
        avatar.setLayoutParams(avatarParams);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackgroundResource(R.drawable.bg_button_soft_purple_round);
        avatar.setTag("avatar");
        row.addView(avatar);

        TextView tvName = new TextView(context);
        android.widget.LinearLayout.LayoutParams nameParams =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(24);
        tvName.setLayoutParams(nameParams);
        tvName.setTextSize(15f);
        tvName.setTextColor(context.getResources().getColor(R.color.pastel_text_primary, null));
        tvName.setTag("name");
        row.addView(tvName);

        TextView btnMsg = new TextView(context);
        android.widget.LinearLayout.LayoutParams msgParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnMsg.setLayoutParams(msgParams);
        btnMsg.setText("Message");
        btnMsg.setTextSize(13f);
        btnMsg.setTextColor(android.graphics.Color.WHITE);
        btnMsg.setPadding(28, 14, 28, 14);
        btnMsg.setBackgroundResource(R.drawable.bg_button_purple);
        btnMsg.setTag("msg");
        row.addView(btnMsg);

        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        UserDto user = users.get(position);
        String name = !TextUtils.isEmpty(user.name) ? user.name
                : (!TextUtils.isEmpty(user.email) ? user.email.split("@")[0] : "User");
        h.tvName.setText(name);

        if (!TextUtils.isEmpty(user.avatarUrl)) {
            Glide.with(context).load(user.avatarUrl).circleCrop().into(h.avatar);
        } else {
            h.avatar.setImageResource(android.R.color.darker_gray);
        }

        h.btnMessage.setOnClickListener(v -> {
            if (messageListener != null) messageListener.onMessageClicked(user);
        });
        h.itemView.setOnClickListener(v -> {
            if (profileListener != null) profileListener.onProfileClicked(user);
        });
    }

    @Override
    public int getItemCount() {
        return users == null ? 0 : users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView tvName, btnMessage;

        ViewHolder(View v) {
            super(v);
            avatar = v.findViewWithTag("avatar");
            tvName = v.findViewWithTag("name");
            btnMessage = v.findViewWithTag("msg");
        }
    }
}
