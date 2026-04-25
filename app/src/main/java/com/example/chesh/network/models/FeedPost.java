package com.example.chesh.network.models;

import java.util.List;

public class FeedPost {
    public long id;
    public String caption;
    public String status;
    public String createdAt;
    public UserDto user;
    // Feed endpoint maps media as List<String>
    public List<String> media;
    // Single-post endpoint returns Sequelize association PostMedia as objects
    public List<PostMediaItem> PostMedia;
    public int likesCount;
    public int commentsCount;
    public boolean hasLiked;
}
