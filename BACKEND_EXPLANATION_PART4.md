# Backend Integration Guide — Part 4: Android ↔ Server Connection (Every Java File)

> This part explains every Java file added to the Android app to connect it with the backend server, and how hardcoded data was replaced with real API data.

---

## Table of Contents (Part 4)
1. [Step 23: `strings.xml` — Storing Backend URL & Google Client ID](#step-23)
2. [Step 24: `ApiService.java` — Defining All API Calls](#step-24)
3. [Step 25: `ApiClient.java` — Creating the Retrofit Instance](#step-25)
4. [Step 26: Network Model Classes (DTOs)](#step-26)
5. [Step 27: `SessionStore.java` — Saving Login State](#step-27)
6. [Step 28: The Auth Flow in `MainActivity.java`](#step-28)
7. [Step 29: Hardcoded Data → Real API Data (Before & After)](#step-29)
8. [Step 30: Backend Discovery — Finding the Server Automatically](#step-30)
9. [Step 31: Complete Request-Response Flow (End to End)](#step-31)
10. [Step 32: Running Everything — The Complete Setup Checklist](#step-32)

---

## Step 23: `strings.xml` — Storing Backend URL & Google Client ID <a name="step-23"></a>

**File**: `app/src/main/res/values/strings.xml`
```xml
<resources>
    <string name="app_name">chesh</string>
    <string name="backend_base_url">http://10.7.24.61:8080/</string>
    <string name="google_web_client_id">750649718921-5utcek3vkl10inrnubgupshoeu1tjuh9.apps.googleusercontent.com</string>
</resources>
```

**`backend_base_url`**: The IP address of the computer running the Node.js server. 
- `10.0.2.2` = Use when running on Android Emulator (maps to host PC's localhost)
- `192.168.x.x` = Use when running on a physical phone (your PC's WiFi IP)
- `10.7.24.61` = The specific IP used in this project

**`google_web_client_id`**: The Web client ID from Firebase Console (type 3 from `google-services.json`). This is NOT the Android client ID — it's the web one. Android needs the *server's* client ID to request an ID token that the server can verify.

**Why store in strings.xml?** So you can easily change these values without modifying Java code. In Java, you access them with `getString(R.string.backend_base_url)`.

---

## Step 24: `ApiService.java` — Defining All API Calls <a name="step-24"></a>

**File**: `app/src/main/java/com/example/chesh/network/ApiService.java`

This is a **Java interface** — it's like a contract that defines what methods exist but doesn't implement them. Retrofit generates the implementation at runtime.

```java
package com.example.chesh.network;

import retrofit2.Call;          // Represents an HTTP call that can be executed
import retrofit2.http.*;        // Annotations: @GET, @POST, @DELETE, etc.
import okhttp3.RequestBody;     // Raw request body (for JSON)
import okhttp3.ResponseBody;    // Raw response body

public interface ApiService {

    // ── Health Check ──
    @GET("health")                                  // HTTP GET request to /health
    Call<HealthResponse> getHealth();                // Returns HealthResponse { ok: true }
    // No @Header("Authorization") → this endpoint doesn't require login

    // ── Authentication ──
    @POST("auth/google")                            // HTTP POST to /auth/google
    Call<AuthGoogleResponse> authenticateGoogle(
        @Body AuthGoogleRequest request             // @Body = send this object as JSON body
    );
    // Sends: { "idToken": "eyJ..." }
    // Receives: { "accessToken": "...", "user": { "id": 1, ... } }

    // ── User Profile ──
    @GET("me")
    Call<MeResponse> getMe(
        @Header("Authorization") String authHeader  // @Header = add this HTTP header
    );
    // Header value: "Bearer eyJhbGci..."
    // The server reads this header in authMiddleware.js

    // ── Feed ──
    @GET("posts/feed")
    Call<FeedResponse> getFeed(
        @Header("Authorization") String authHeader,
        @Query("sort") String sort                  // @Query = URL parameter: ?sort=new
    );
    // Full URL becomes: GET /posts/feed?sort=new

    // ── Single Post ──
    @GET("posts/{id}")
    Call<PostResponse> getPost(
        @Header("Authorization") String authHeader,
        @Path("id") long postId                     // @Path = replaces {id} in URL
    );
    // If postId = 42 → GET /posts/42

    // ── Create Post ──
    @POST("posts")
    Call<ResponseBody> createPost(
        @Header("Authorization") String authHeader,
        @Body RequestBody requestBody               // Raw JSON body
    );

    // ── Like/Unlike ──
    @POST("posts/{id}/likes")
    Call<ResponseBody> likePost(@Header("Authorization") String auth, @Path("id") long postId);

    @DELETE("posts/{id}/likes")                     // DELETE = remove the like
    Call<ResponseBody> unlikePost(@Header("Authorization") String auth, @Path("id") long postId);

    // ── Comments ──
    @GET("posts/{id}/comments")
    Call<CommentsResponse> getPostComments(@Header("Authorization") String auth, @Path("id") long postId);

    @POST("posts/{id}/comments")
    Call<ResponseBody> postComment(@Header("Authorization") String auth, @Path("id") long postId, @Body RequestBody body);

    // ── User Stats ──
    @GET("users/{id}")
    Call<UserStatsResponse> getUserStats(@Header("Authorization") String auth, @Path("id") long userId);

    // ── Follow ──
    @POST("users/{id}/follow")
    Call<ResponseBody> followUser(@Header("Authorization") String auth, @Path("id") long userId);

    @DELETE("users/{id}/follow")
    Call<ResponseBody> unfollowUser(@Header("Authorization") String auth, @Path("id") long userId);

    // ── Profile Update ──
    @PATCH("me")                                    // PATCH = partial update
    Call<ResponseBody> updateMe(@Header("Authorization") String auth, @Body RequestBody body);

    // ── Conversations & Messages ──
    @GET("conversations")
    Call<ConversationsResponse> getConversations(@Header("Authorization") String auth);

    @POST("conversations/direct")
    Call<ResponseBody> createDirectConversation(@Header("Authorization") String auth, @Body RequestBody body);

    @GET("conversations/{id}/messages")
    Call<MessagesResponse> getConversationMessages(@Header("Authorization") String auth, @Path("id") long convId);

    @POST("conversations/{id}/messages")
    Call<ResponseBody> postMessage(@Header("Authorization") String auth, @Path("id") long convId, @Body RequestBody body);

    // ── Leaderboard ──
    @GET("leaderboard/current")
    Call<LeaderboardResponse> getCurrentLeaderboard(@Header("Authorization") String auth);

    // ── Notifications ──
    @GET("notifications")
    Call<NotificationsResponse> getNotifications(@Header("Authorization") String auth, @Query("cursor") Long cursor);
}
```

### Key Retrofit Annotations Explained:

| Annotation | Meaning | Example |
|------------|---------|---------|
| `@GET("path")` | HTTP GET request | `@GET("posts/feed")` |
| `@POST("path")` | HTTP POST request (create) | `@POST("posts")` |
| `@PATCH("path")` | HTTP PATCH request (update) | `@PATCH("me")` |
| `@DELETE("path")` | HTTP DELETE request (remove) | `@DELETE("posts/{id}/likes")` |
| `@Path("x")` | Replaces `{x}` in the URL | `@Path("id") long postId` |
| `@Query("x")` | Adds `?x=value` to URL | `@Query("sort") String sort` |
| `@Header("X")` | Adds HTTP header | `@Header("Authorization") String auth` |
| `@Body` | Sends object as JSON request body | `@Body AuthGoogleRequest req` |

### What is `Call<T>`?
`Call<T>` represents a pending HTTP request. `T` is the expected response type. You execute it with:
- `.enqueue(callback)` — asynchronous (non-blocking, preferred for Android)
- `.execute()` — synchronous (blocks the thread, not recommended on main thread)

---

## Step 25: `ApiClient.java` — Creating the Retrofit Instance <a name="step-25"></a>

```java
package com.example.chesh.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ApiClient {
    private ApiClient() {}    // Private constructor — prevents creating instances
                              // This is a utility class with only static methods

    public static ApiService createApiService(String baseUrl) {
        // 1. Create a logging interceptor (prints all HTTP traffic in Logcat)
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);
        // Level.BODY = log everything: headers, request body, response body
        // Use Level.NONE in production to avoid logging sensitive data

        // 2. Build an OkHttp client with the logger
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logger)       // Interceptor = code that runs on every HTTP request
                .build();

        // 3. Create a Gson instance for JSON parsing
        Gson gson = new GsonBuilder().create();

        // 4. Build the Retrofit instance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)             // e.g., "http://10.0.2.2:8080/"
                .addConverterFactory(GsonConverterFactory.create(gson))  // Use Gson for JSON conversion
                .client(client)               // Use our OkHttp client with logging
                .build();

        // 5. Generate the ApiService implementation from the interface
        return retrofit.create(ApiService.class);
        // Retrofit uses reflection to create a concrete class that implements ApiService
        // Every method annotated with @GET/@POST etc. becomes a real HTTP call
    }
}
```

### What Happens Under the Hood:
When you call `apiService.getFeed(token, "new")`, Retrofit:
1. Reads the `@GET("posts/feed")` annotation
2. Combines it with baseUrl: `http://10.0.2.2:8080/posts/feed`
3. Adds query parameter: `?sort=new`
4. Adds header: `Authorization: Bearer eyJ...`
5. Makes the HTTP GET request using OkHttp
6. Gets the JSON response body
7. Uses Gson to parse it into a `FeedResponse` Java object
8. Calls your `onResponse()` callback with the result

---

## Step 26: Network Model Classes (DTOs) <a name="step-26"></a>

**DTO** = Data Transfer Object. These are simple Java classes whose field names match the JSON keys. Gson automatically maps JSON ↔ Java objects by matching field names.

### `UserDto.java`:
```java
public class UserDto {
    public long id;              // matches JSON: "id": 1
    public String firebaseUid;   // matches JSON: "firebaseUid": "abc123"
    public String email;         // matches JSON: "email": "user@example.com"
    public String name;
    public String avatarUrl;
    public String bio;
    public String pronouns;
}
```

### `AuthGoogleRequest.java` (what Android sends):
```java
public class AuthGoogleRequest {
    public String idToken;       // The Firebase ID token
    public AuthGoogleRequest(String idToken) { this.idToken = idToken; }
}
// Gson serializes this to: { "idToken": "eyJhbG..." }
```

### `AuthGoogleResponse.java` (what server returns):
```java
public class AuthGoogleResponse {
    public String accessToken;   // Our JWT token to use for future requests
    public UserDto user;         // The user's profile data
}
// Gson deserializes from: { "accessToken": "...", "user": { "id": 1, ... } }
```

### `FeedPost.java`:
```java
public class FeedPost {
    public long id;
    public String caption;
    public String createdAt;
    public UserDto user;               // Nested object — Gson handles this automatically
    public List<String> media;         // Array of image URLs
    public int likesCount;
    public int commentsCount;
    public boolean hasLiked;           // Whether the current user has liked this post
}
```

### `FeedResponse.java`:
```java
public class FeedResponse {
    public List<FeedPost> feed;        // Array of feed posts
}
// Matches server JSON: { "feed": [ { "id": 1, ... }, { "id": 2, ... } ] }
```

**The naming must match EXACTLY**: If the server sends `"avatarUrl"` but your Java field is `avatar_url`, Gson won't map it. Use the same casing. With `@SerializedName("avatar_url")` you could override this, but we keep them consistent.

---

## Step 27: `SessionStore.java` — Saving Login State <a name="step-27"></a>

```java
public class SessionStore {
    private static final String PREF_NAME = "chesh_session";  // SharedPreferences file name
    private static final String KEY_TOKEN = "access_token";
    // ... other keys for user fields

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        // SharedPreferences = Android's simple key-value storage
        // Saved as an XML file on the device, persists across app restarts
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveAccessToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
        // .edit() → start editing
        // .putString() → store the value
        // .apply() → save asynchronously (doesn't block UI)
    }

    public String getAccessToken() {
        return prefs.getString(KEY_TOKEN, null);  // null = default if not found
    }

    public void saveUser(UserDto user) {
        if (user == null) return;
        prefs.edit()
                .putLong("user_id", user.id)
                .putString("user_name", user.name)
                .putString("user_email", user.email)
                .putString("user_avatar_url", user.avatarUrl)
                .putString("user_bio", user.bio)
                .putString("user_pronouns", user.pronouns)
                .putString("firebase_uid", user.firebaseUid)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();   // Removes all saved data (logout)
    }
}
```

**Why SharedPreferences?** When the user logs in, we save their JWT token. When the app is reopened later, we check if a token exists — if yes, skip the login screen. Without this, users would have to log in every single time they open the app.

---

## Step 28: The Auth Flow in `MainActivity.java` <a name="step-28"></a>

### 28.1 Google Sign-In Button Tap → Google Account Picker:
```java
private void startGoogleSignInFlow() {
    googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    // Shows the Google account picker dialog
}
```

### 28.2 User Picks Account → Firebase Auth:
```java
private void firebaseAuthWithGoogle(String idToken) {
    AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
    // Create a Firebase credential from the Google ID token
    
    firebaseAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
        // Firebase verifies the Google token and creates/finds a Firebase user
        FirebaseUser user = firebaseAuth.getCurrentUser();
        user.getIdToken(true).addOnCompleteListener(tokenTask -> {
            // Get a Firebase ID token (different from the Google token)
            exchangeTokenWithBackend(tokenTask.getResult().getToken());
        });
    });
}
```

### 28.3 Exchange Firebase Token with Our Backend:
```java
private void exchangeTokenWithBackend(String firebaseIdToken) {
    apiService.authenticateGoogle(new AuthGoogleRequest(firebaseIdToken))
            .enqueue(new Callback<AuthGoogleResponse>() {
                @Override
                public void onResponse(Call<AuthGoogleResponse> call, Response<AuthGoogleResponse> response) {
                    // Server verified the Firebase token, created/found the user,
                    // and returned our JWT access token
                    sessionStore.saveAccessToken(response.body().accessToken);
                    sessionStore.saveUser(response.body().user);
                    validateSessionAndEnterApp();
                }
            });
}
```

### Complete Auth Flow Diagram:
```
User taps "Sign in with Google"
        │
        ▼
Google Account Picker appears → User selects account
        │
        ▼
Google gives → Google ID Token
        │
        ▼
Firebase signInWithCredential(googleIdToken)
        │
        ▼
Firebase creates/finds user → Firebase ID Token
        │
        ▼
Android POST /auth/google { idToken: firebaseIdToken }
        │
        ▼
Server verifies with Firebase Admin SDK
        │
        ▼
Server creates/updates User in MySQL
        │
        ▼
Server creates JWT → sends { accessToken, user }
        │
        ▼
Android saves accessToken in SharedPreferences
        │
        ▼
Android navigates to Home screen ✓
```

---

## Step 29: Hardcoded Data → Real API Data <a name="step-29"></a>

### BEFORE (Frontend-Only — Hardcoded):
```java
// Profile screen — ALL data is fake, typed by the developer
tvUsername.setText("john_doe");
tvPostCount.setText("42");
tvFollowers.setText("128");
tvBio.setText("Photography enthusiast");
```

### AFTER (With Backend — Real Data):
```java
// Profile screen — data comes from the MySQL database via API
UserDto me = sessionStore.getUser();
tvUsername.setText(me.email.split("@")[0]);
tvBio.setText(me.bio != null ? me.bio : "");

apiService.getUserStats(bearer(), me.id).enqueue(new Callback<UserStatsResponse>() {
    @Override
    public void onResponse(Call<UserStatsResponse> call, Response<UserStatsResponse> response) {
        UserStats s = response.body().stats;
        tvPostCount.setText(String.valueOf(s.postsCount));       // Real count from DB
        tvFollowers.setText(String.valueOf(s.followersCount));   // Real count from DB
    }
});
```

### What `bearer()` Returns:
```java
private String bearer() {
    String token = sessionStore.getAccessToken();
    return token != null ? "Bearer " + token : null;
}
// Produces: "Bearer eyJhbGciOiJIUzI1NiIs..."
// This is the standard HTTP Authorization header format
```

---

## Step 30: Backend Discovery <a name="step-30"></a>

The app doesn't know which URL the server is running on. It tries multiple candidates:

```java
private void initializeBackendCandidates() {
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    unique.add(getString(R.string.backend_base_url));     // From strings.xml
    unique.add("http://10.0.2.2:8080/");                  // Emulator
    unique.add("http://localhost:8080/");                  // Same device
    unique.add("http://127.0.0.1:8080/");                 // Loopback
    unique.add("http://192.168.137.1:8080/");             // USB tethering
    backendCandidates.addAll(unique);
}
```

It tries each URL by calling `GET /health`. The first one that responds with `{ "ok": true }` is used.

If NO backend is found, the app falls back to mock data, showing placeholder content.

---

## Step 31: Complete Request-Response Flow <a name="step-31"></a>

Here's what happens when the home screen loads the feed:

```
1. Android: apiService.getFeed("Bearer eyJ...", "new").enqueue(callback)
   │
   ├── Retrofit builds: GET http://10.0.2.2:8080/posts/feed?sort=new
   ├── Adds header: Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
   │
2. OkHttp sends HTTP request over the network
   │
3. Express server receives request at GET /posts/feed
   │
   ├── CORS middleware: ✓ origin allowed
   ├── JSON parser middleware: ✓ (no body for GET)
   ├── requireAuth middleware:
   │   ├── Reads "Bearer eyJ..." from Authorization header
   │   ├── jwt.verify(token, secret) → { sub: 1, email: "...", exp: ... }
   │   ├── User.findByPk(1) → loads user from MySQL
   │   ├── Sets req.user = { id: 1, name: "Ayush", ... }
   │   └── Calls next() → proceeds to route handler
   │
4. Route handler executes:
   ├── Post.findAll({ include: [User, PostMedia, PostComment] })
   │   └── Sequelize generates: SELECT ... FROM posts JOIN users ON ...
   ├── PostLike.findAll({ where: { postId: [1,2,3,...] } })
   │   └── Sequelize generates: SELECT * FROM post_likes WHERE post_id IN (1,2,3,...)
   ├── Builds response JSON: { feed: [ { id: 1, caption: "...", user: {...}, ... } ] }
   └── res.status(200).json({ feed })
   │
5. OkHttp receives HTTP response (200 OK + JSON body)
   │
6. Retrofit passes response to GsonConverterFactory
   ├── Gson parses JSON into FeedResponse object
   ├── FeedResponse.feed = List<FeedPost> with real data
   │
7. Your callback.onResponse() is called
   ├── You read response.body().feed
   ├── Create FeedAdapter with the posts list
   └── recyclerView.setAdapter(adapter)
   │
8. RecyclerView renders each post on screen ✓
```

---

## Step 32: Complete Setup Checklist <a name="step-32"></a>

### To replicate this for your own app:

**Firebase Setup:**
- [ ] Create Firebase project at console.firebase.google.com
- [ ] Add Android app with your package name + SHA-1
- [ ] Enable Google Sign-In in Authentication → Sign-in method
- [ ] Download `google-services.json` → put in `app/`
- [ ] Download service account key → put in `server/`
- [ ] Add Web client ID to `strings.xml`

**Android Setup:**
- [ ] Add `google-services` plugin to both Gradle files
- [ ] Add dependencies: Firebase Auth, Play Services Auth, Retrofit, OkHttp, Gson, Glide
- [ ] Add `INTERNET` permission in AndroidManifest
- [ ] Add `usesCleartextTraffic="true"` for local dev
- [ ] Create `ApiService.java` interface with all endpoints
- [ ] Create `ApiClient.java` with Retrofit builder
- [ ] Create DTO classes matching server JSON responses
- [ ] Create `SessionStore.java` for saving tokens
- [ ] Implement Google Sign-In + Firebase Auth + token exchange flow

**Server Setup:**
- [ ] Install Node.js
- [ ] Create `server/` folder with `npm init`
- [ ] Install packages: express, cors, dotenv, mysql2, sequelize, firebase-admin, jsonwebtoken, zod
- [ ] Create `.env` with MySQL credentials, JWT secret, Firebase config
- [ ] Create config files (env.js, database.js, firebase.js)
- [ ] Create User model and all other models
- [ ] Define model relationships (hasMany, belongsTo, etc.)
- [ ] Create auth middleware (JWT verification)
- [ ] Create auth routes (Firebase token → JWT exchange)
- [ ] Create all feature routes
- [ ] Create SQL schema files
- [ ] Run `npm run db:init` to create tables

**MySQL Setup:**
- [ ] Install MySQL Server
- [ ] Create database: `CREATE DATABASE gallery_v2;`
- [ ] Set credentials in `.env`

**Running Everything:**
1. Start MySQL server
2. `cd server && npm run db:init` (first time only)
3. `cd server && npm run dev` (starts backend)
4. Run Android app (emulator or physical device)
5. App discovers backend → login → real data!

---

> **This completes the full explanation.** With these 4 parts, you have everything needed to understand and replicate the backend integration from scratch.
