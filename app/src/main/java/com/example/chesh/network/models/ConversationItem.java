package com.example.chesh.network.models;

public class ConversationItem {
    public long id;
    public String conversationType;
    public String createdAt;
    public String updatedAt;
    public UserDto otherUser; // the OTHER participant's user info
}

