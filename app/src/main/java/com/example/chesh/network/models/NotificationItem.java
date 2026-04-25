package com.example.chesh.network.models;

public class NotificationItem {
    public long id;
    public String notificationType;
    public String entityType;
    public Long entityId;
    public String readAt;
    public String createdAt;
    public long actorId;
    public long recipientId;
    public UserDto ActorUser;
    public UserDto Recipient;
}
