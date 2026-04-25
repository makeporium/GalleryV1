package com.example.chesh.network;

import com.example.chesh.network.models.AuthGoogleRequest;
import com.example.chesh.network.models.AuthGoogleResponse;
import com.example.chesh.network.models.CommentsResponse;
import com.example.chesh.network.models.ConversationsResponse;
import com.example.chesh.network.models.FeedResponse;
import com.example.chesh.network.models.FollowersResponse;
import com.example.chesh.network.models.FollowingResponse;
import com.example.chesh.network.models.HealthResponse;
import com.example.chesh.network.models.LeaderboardResponse;
import com.example.chesh.network.models.MeResponse;
import com.example.chesh.network.models.MessagesResponse;
import com.example.chesh.network.models.NotificationsResponse;
import com.example.chesh.network.models.PostLikersResponse;
import com.example.chesh.network.models.PostResponse;
import com.example.chesh.network.models.PromptResponse;
import com.example.chesh.network.models.UserPostsResponse;
import com.example.chesh.network.models.UserStatsResponse;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @GET("health")
    Call<HealthResponse> getHealth();

    @POST("auth/google")
    Call<AuthGoogleResponse> authenticateGoogle(@Body AuthGoogleRequest request);

    @GET("me")
    Call<MeResponse> getMe(@Header("Authorization") String authHeader);

    // --- Prompts ---
    @GET("prompts/today")
    Call<PromptResponse> getTodayPrompt(@Header("Authorization") String authHeader);

    @GET("prompts/history")
    Call<ResponseBody> getPromptHistory(@Header("Authorization") String authHeader);

    // --- Posts / Feed ---
    @GET("posts/feed")
    Call<FeedResponse> getFeed(@Header("Authorization") String authHeader, @Query("sort") String sort);

    @GET("posts/{id}")
    Call<PostResponse> getPost(@Header("Authorization") String authHeader, @Path("id") long postId);

    @POST("posts")
    Call<ResponseBody> createPost(@Header("Authorization") String authHeader, @Body RequestBody requestBody);

    @DELETE("posts/{id}")
    Call<ResponseBody> deletePost(@Header("Authorization") String authHeader, @Path("id") long postId);

    @POST("posts/{id}/likes")
    Call<ResponseBody> likePost(@Header("Authorization") String authHeader, @Path("id") long postId);

    @DELETE("posts/{id}/likes")
    Call<ResponseBody> unlikePost(@Header("Authorization") String authHeader, @Path("id") long postId);

    @GET("posts/{id}/likes")
    Call<PostLikersResponse> getPostLikers(@Header("Authorization") String authHeader, @Path("id") long postId);

    // --- Leaderboard ---
    @GET("leaderboard/current")
    Call<LeaderboardResponse> getCurrentLeaderboard(@Header("Authorization") String authHeader);

    // --- Notifications ---
    @GET("notifications")
    Call<NotificationsResponse> getNotifications(@Header("Authorization") String authHeader, @Query("cursor") Long cursor);

    // --- Conversations ---
    @GET("conversations")
    Call<ConversationsResponse> getConversations(@Header("Authorization") String authHeader);

    @POST("conversations/direct")
    Call<ResponseBody> createDirectConversation(@Header("Authorization") String authHeader, @Body RequestBody body);

    // --- Users ---
    @GET("users/{id}")
    Call<UserStatsResponse> getUserStats(@Header("Authorization") String authHeader, @Path("id") long userId);

    @GET("users/{id}/posts")
    Call<UserPostsResponse> getUserPosts(@Header("Authorization") String authHeader, @Path("id") long userId);

    @GET("users/{id}/followers")
    Call<FollowersResponse> getUserFollowers(@Header("Authorization") String authHeader, @Path("id") long userId);

    @GET("users/{id}/following")
    Call<FollowingResponse> getUserFollowing(@Header("Authorization") String authHeader, @Path("id") long userId);

    // --- Profile ---
    @PATCH("me")
    Call<ResponseBody> updateMe(@Header("Authorization") String authHeader, @Body RequestBody requestBody);

    // --- Follow ---
    @POST("users/{id}/follow")
    Call<ResponseBody> followUser(@Header("Authorization") String authHeader, @Path("id") long userId);

    @DELETE("users/{id}/follow")
    Call<ResponseBody> unfollowUser(@Header("Authorization") String authHeader, @Path("id") long userId);

    // --- Comments ---
    @GET("posts/{id}/comments")
    Call<CommentsResponse> getPostComments(@Header("Authorization") String authHeader, @Path("id") long postId);

    @POST("posts/{id}/comments")
    Call<ResponseBody> postComment(@Header("Authorization") String authHeader, @Path("id") long postId, @Body RequestBody requestBody);

    // --- Messages ---
    @GET("conversations/{id}/messages")
    Call<MessagesResponse> getConversationMessages(@Header("Authorization") String authHeader, @Path("id") long conversationId);

    @POST("conversations/{id}/messages")
    Call<ResponseBody> postMessage(@Header("Authorization") String authHeader, @Path("id") long conversationId, @Body RequestBody requestBody);
}
