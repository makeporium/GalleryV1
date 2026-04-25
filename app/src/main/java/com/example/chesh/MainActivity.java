package com.example.chesh;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chesh.auth.SessionStore;
import com.example.chesh.network.ApiClient;
import com.example.chesh.network.ApiService;
import com.example.chesh.network.adapters.ConversationsAdapter;
import com.example.chesh.network.adapters.FeedAdapter;
import com.example.chesh.network.models.PostMediaItem;
import com.example.chesh.network.adapters.GalleryAdapter;
import com.example.chesh.network.adapters.LeaderboardAdapter;
import com.example.chesh.network.adapters.NotificationsAdapter;
import com.example.chesh.network.adapters.ProfileGridAdapter;
import com.example.chesh.network.models.AuthGoogleRequest;
import com.example.chesh.network.models.AuthGoogleResponse;
import com.example.chesh.network.models.ConversationsResponse;
import com.example.chesh.network.models.FeedResponse;
import com.example.chesh.network.models.HealthResponse;
import com.example.chesh.network.models.LeaderboardResponse;
import com.example.chesh.network.models.MeResponse;
import com.example.chesh.network.models.NotificationsResponse;
import com.example.chesh.network.models.PromptResponse;
import com.example.chesh.network.models.UserDto;
import com.example.chesh.network.models.UserPostsResponse;
import com.example.chesh.network.models.UserStatsResponse;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivityAuth";
    private FrameLayout screenContainer;
    private int currentScreen = R.layout.screen_auth_signup;

    private ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMedia;
    private ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickAvatarMedia;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<Intent> cropResultLauncher;
    private Uri selectedPhotoUri = null;
    private Uri selectedAvatarUri = null;
    private Uri pendingCropUri = null;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private ApiService apiService;
    private SessionStore sessionStore;
    private final List<String> backendCandidates = new ArrayList<>();
    private String activeBackendBaseUrl;
    private final Set<Long> likedPostIds = new HashSet<>();
    private String currentPostMediaUrl = null;
    private long currentViewedUserId = -1;

    // ─── Back Stack ────────────────────────────────────────────────────────────
    private final Deque<Integer> backStack = new ArrayDeque<>();
    private boolean isNavigatingBack = false;

    // Context for post detail and chat screens
    private long currentPostId = -1;
    private long currentConversationId = -1;

    // Debug flags (keep for debug screen)
    private boolean mockIsFirstTime = true;
    private boolean mockIsMonday = false;
    private int mockStrikeLevel = 0;

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        screenContainer = findViewById(R.id.screenContainer);

        sessionStore = new SessionStore(this);
        firebaseAuth = FirebaseAuth.getInstance();
        initializeBackendCandidates();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                showPhotoPreviewDialog(uri);
            }
        });

        pickAvatarMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedAvatarUri = uri;
                if (currentScreen == R.layout.screen_edit_profile) {
                    renderScreen(R.layout.screen_edit_profile);
                }
            }
        });

        cropResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Bundle extras = result.getData().getExtras();
                if (extras != null) {
                    Bitmap cropped = (Bitmap) extras.get("data");
                    if (cropped != null) {
                        try {
                            File tmp = new File(getCacheDir(), "cropped_photo.jpg");
                            FileOutputStream fos = new FileOutputStream(tmp);
                            cropped.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                            fos.close();
                            selectedPhotoUri = Uri.fromFile(tmp);
                            if (currentScreen == R.layout.screen_submission) {
                                renderScreen(R.layout.screen_submission);
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Crop save failed", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                }
                // If return-data didn't give a bitmap, fall back to the pending URI
                if (pendingCropUri != null) {
                    selectedPhotoUri = pendingCropUri;
                    pendingCropUri = null;
                    if (currentScreen == R.layout.screen_submission) {
                        renderScreen(R.layout.screen_submission);
                    }
                }
            }
        });

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        Toast.makeText(this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        GoogleSignInAccount account = GoogleSignIn
                                .getSignedInAccountFromIntent(result.getData())
                                .getResult(ApiException.class);
                        if (account == null || account.getIdToken() == null) {
                            Toast.makeText(this, "Google token missing. Check Firebase SHA + web client ID.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        Log.e(TAG, "Google sign-in intent failed. code=" + e.getStatusCode(), e);
                        Toast.makeText(this, "Google sign-in failed (code " + e.getStatusCode() + ").", Toast.LENGTH_LONG).show();
                    }
                }
        );

        android.content.Intent intent = getIntent();
        if (android.content.Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            android.net.Uri data = intent.getData();
            if (data.getPath() != null && data.getPath().startsWith("/profile/")) {
                try {
                    String idStr = data.getPath().replace("/profile/", "");
                    currentViewedUserId = Long.parseLong(idStr);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse deep link id", e);
                }
            }
        }

        resolveBackendAndContinue(() -> {
            if (sessionStore.getAccessToken() != null) {
                validateSessionAndEnterApp();
            } else {
                renderScreen(currentScreen);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // ─── Screen Rendering ──────────────────────────────────────────────────────

    private void renderScreen(int layoutRes) {
        Log.d(TAG, "Rendering screen: " + getResources().getResourceEntryName(layoutRes));
        if (mockStrikeLevel == 3
                && layoutRes != R.layout.screen_banned
                && layoutRes != R.layout.screen_debug
                && layoutRes != R.layout.screen_auth_signup
                && layoutRes != R.layout.screen_auth_login) {
            layoutRes = R.layout.screen_banned;
        }
        // Push previous screen to back stack (unless we are already navigating back)
        if (!isNavigatingBack && currentScreen != layoutRes) {
            int prev = currentScreen;
            // Don't stack auth screens
            if (prev != R.layout.screen_auth_signup && prev != R.layout.screen_auth_login) {
                backStack.push(prev);
                if (backStack.size() > 25) backStack.pollLast(); // cap depth
            }
        }
        // Clear stack when reaching a root destination
        if (layoutRes == R.layout.screen_auth_signup || layoutRes == R.layout.screen_auth_login) {
            backStack.clear();
        }
        currentScreen = layoutRes;
        screenContainer.removeAllViews();
        View screenView = LayoutInflater.from(this).inflate(layoutRes, screenContainer, true);
        Log.d(TAG, "Screen inflated, wiring current screen");
        wireCurrentScreen(screenView, layoutRes);
        Log.d(TAG, "Screen wiring complete");
    }

    @Override
    public void onBackPressed() {
        if (!backStack.isEmpty()) {
            int previous = backStack.pop();
            isNavigatingBack = true;
            renderScreen(previous);
            isNavigatingBack = false;
        } else {
            super.onBackPressed();
        }
    }

    private void wireCurrentScreen(View root, int layoutRes) {

        // ── Auth ──────────────────────────────────────────────────
        if (layoutRes == R.layout.screen_auth_signup) {
            setClickWithAction(root, R.id.btnGoFromSignup, () -> handleEmailSignup(root));
            setClick(root, R.id.btnToLogin, R.layout.screen_auth_login);
            setClickWithAction(root, R.id.btnGoogleSignup, this::startGoogleSignInFlow);

        } else if (layoutRes == R.layout.screen_auth_login) {
            setClickWithAction(root, R.id.btnLoginGoHome, () -> handleEmailLogin(root));
            setClick(root, R.id.btnLoginToSignup, R.layout.screen_auth_signup);
            setClick(root, R.id.btnBackToSignup, R.layout.screen_auth_signup);
            setClickWithAction(root, R.id.btnGoogleLogin, this::startGoogleSignInFlow);

        // ── Tutorial ──────────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_tutorial) {
            setClickWithAction(root, R.id.btnFinishTutorial, () -> {
                mockIsFirstTime = false;
                navigateToHome();
            });

        // ── Weekly Wrap-up ─────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_weekly_wrapup) {
            setClickWithAction(root, R.id.btnWrapupToGallery, () -> {
                mockIsMonday = false;
                renderScreen(R.layout.screen_home);
            });

        // ── Banned ────────────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_banned) {
            setClickWithAction(root, R.id.btnBannedLogout, () -> renderScreen(R.layout.screen_auth_signup));

        // ── Debug ─────────────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_debug) {
            setClickWithAction(root, R.id.btnDebugFirstTime, () -> {
                mockIsFirstTime = true;
                renderScreen(R.layout.screen_auth_signup);
            });
            setClickWithAction(root, R.id.btnDebugMonday, () -> {
                mockIsMonday = true;
                navigateToHome();
            });
            setClickWithAction(root, R.id.btnDebugStrike1, () -> {
                mockStrikeLevel = 1;
                renderScreen(R.layout.screen_moderation_appeal);
            });
            setClickWithAction(root, R.id.btnDebugStrike2, () -> {
                mockStrikeLevel = 2;
                renderScreen(R.layout.screen_moderation_appeal);
            });
            setClickWithAction(root, R.id.btnDebugStrike3, () -> {
                mockStrikeLevel = 3;
                renderScreen(R.layout.screen_banned);
            });
            setClickWithAction(root, R.id.btnDebugClearStrikes, () -> mockStrikeLevel = 0);
            setClickWithAction(root, R.id.btnDebugBack, () -> renderScreen(R.layout.screen_home));

        // ── Home (real data) ──────────────────────────────────────
        } else if (layoutRes == R.layout.screen_home) {
            setClickWithAction(root, R.id.btnHomeDebug, () -> renderScreen(R.layout.screen_debug));
            setClick(root, R.id.btnHomeSubmitEntry, R.layout.screen_submission);
            loadHomeScreen(root);

        // ── Gallery (real data) ───────────────────────────────────
        } else if (layoutRes == R.layout.screen_gallery_new) {
            setClick(root, R.id.tabNew, R.layout.screen_gallery_new);
            setClick(root, R.id.tabTrending, R.layout.screen_gallery_trending);
            setClick(root, R.id.tabTop, R.layout.screen_gallery_top);
            loadGalleryScreen(root, "new");

        } else if (layoutRes == R.layout.screen_gallery_trending) {
            setClick(root, R.id.tabNew, R.layout.screen_gallery_new);
            setClick(root, R.id.tabTrending, R.layout.screen_gallery_trending);
            setClick(root, R.id.tabTop, R.layout.screen_gallery_top);
            loadGalleryScreen(root, "trending");

        } else if (layoutRes == R.layout.screen_gallery_top) {
            setClick(root, R.id.tabNew, R.layout.screen_gallery_new);
            setClick(root, R.id.tabTrending, R.layout.screen_gallery_trending);
            setClick(root, R.id.tabTop, R.layout.screen_gallery_top);
            loadGalleryScreen(root, "top");

        // ── Profile (real data) ───────────────────────────────────
        } else if (layoutRes == R.layout.screen_profile) {
            if (currentViewedUserId != -1) {
                // Read-only view of another user
                setClickWithAction(root, R.id.btnProfileSettings, () -> {
                    currentViewedUserId = -1;
                    renderScreen(R.layout.screen_home);
                });
                TextView btnEdit = root.findViewById(R.id.btnProfileEdit);
                if (btnEdit != null) {
                    btnEdit.setText("Follow");
                    btnEdit.setOnClickListener(v -> followUserFromProfile(btnEdit));
                }
                TextView btnShare = root.findViewById(R.id.btnProfileShare);
                if (btnShare != null) {
                    btnShare.setText("Message");
                    btnShare.setOnClickListener(v -> openDirectConversation(currentViewedUserId));
                }
                // Followers/following counts tappable
                setClickWithAction(root, R.id.tvFollowersCount, () -> showFollowersList(currentViewedUserId));
                setClickWithAction(root, R.id.tvFollowingCount, () -> showFollowingList(currentViewedUserId));
            } else {
                setClickWithAction(root, R.id.btnProfileSettings, () -> renderScreen(R.layout.screen_settings));
                setClick(root, R.id.btnProfileEdit, R.layout.screen_edit_profile);
                setClickWithAction(root, R.id.btnProfileShare, () ->
                        Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show());
                // Followers count tappable for own profile too
                UserDto _me = sessionStore.getUser();
                if (_me != null) {
                    setClickWithAction(root, R.id.tvFollowersCount, () -> showFollowersList(_me.id));
                    setClickWithAction(root, R.id.tvFollowingCount, () -> showFollowingList(_me.id));
                }
            }
            loadProfileScreen(root);

        // ── Leaderboard (real data) ───────────────────────────────
        } else if (layoutRes == R.layout.screen_leaderboard) {
            setClick(root, R.id.btnLeaderboardHistory, R.layout.screen_previous_challenges);
            loadLeaderboardScreen(root);

        } else if (layoutRes == R.layout.screen_previous_challenges) {
            setClick(root, R.id.btnPreviousChallengesBack, R.layout.screen_leaderboard);

        // ── Social Hub (Activity tab) ──────────────────────────────
        } else if (layoutRes == R.layout.screen_social_hub) {
            setClick(root, R.id.toggleActivity, R.layout.screen_social_hub);
            setClick(root, R.id.toggleMessages, R.layout.screen_messages);
            loadSocialHubScreen(root);

        // ── Notifications (real data) ─────────────────────────────
        } else if (layoutRes == R.layout.screen_notifications) {
            setClick(root, R.id.toggleActivity, R.layout.screen_social_hub);
            setClick(root, R.id.toggleMessages, R.layout.screen_messages);
            loadNotificationsScreen(root);

        // ── Messages (real data) ──────────────────────────────────
        } else if (layoutRes == R.layout.screen_messages) {
            setClick(root, R.id.toggleActivity, R.layout.screen_social_hub);
            setClick(root, R.id.toggleMessages, R.layout.screen_messages);
            loadMessagesScreen(root);

        // ── Submission & Reward ───────────────────────────────────
        } else if (layoutRes == R.layout.screen_submission) {
            setClickWithAction(root, R.id.btnSubmitPostTop, () -> handleSubmitPost(root));
            setClick(root, R.id.btnSubmissionBack, R.layout.screen_home);

            View btnSelectPhoto = root.findViewById(R.id.btnSelectPhoto);
            if (btnSelectPhoto != null) {
                btnSelectPhoto.setOnClickListener(v ->
                        pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()));
            }
            ImageView ivSelectedPhoto = root.findViewById(R.id.ivSelectedPhoto);
            if (ivSelectedPhoto != null && selectedPhotoUri != null) {
                ivSelectedPhoto.setImageURI(selectedPhotoUri);
                View hint = root.findViewById(R.id.tvSelectPhotoHint);
                if (hint != null) hint.setVisibility(View.GONE);
            }
            
            // Fetch prompt title dynamically
            String token = bearer();
            if (apiService != null && token != null) {
                apiService.getTodayPrompt(token).enqueue(new Callback<PromptResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<PromptResponse> call, @NonNull Response<PromptResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().prompt != null) {
                            TextView tvTitle = root.findViewById(R.id.tvSubmissionPromptTitle);
                            if (tvTitle != null) tvTitle.setText(response.body().prompt.title);
                        }
                    }
                    @Override public void onFailure(@NonNull Call<PromptResponse> call, @NonNull Throwable t) {}
                });
            }

        } else if (layoutRes == R.layout.screen_interstitial_reward) {
            setClick(root, R.id.btnReturnToGallery, R.layout.screen_home);

        // ── Settings ──────────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_settings) {
            setClick(root, R.id.btnSettingsBack, R.layout.screen_profile);
            setClick(root, R.id.btnSettingsDebug, R.layout.screen_debug);
            setClickWithAction(root, R.id.btnSettingsLogout, this::logout);
            setClick(root, R.id.btnSettingsEditProfile, R.layout.screen_edit_profile);
            setClick(root, R.id.btnSettingsNotifications, R.layout.screen_notifications);

        // ── Edit Profile (prefilled) ───────────────────────────────
        } else if (layoutRes == R.layout.screen_edit_profile) {
            loadEditProfileScreen(root);
            setClickWithAction(root, R.id.btnSaveEditProfile, () -> handleSaveProfile(root));

        // ── Moderation Appeal ─────────────────────────────────────
        } else if (layoutRes == R.layout.screen_moderation_appeal) {
            setClick(root, R.id.btnAppealStrike, R.layout.screen_home);
            setClick(root, R.id.btnAcceptStrike, R.layout.screen_home);

        // ── Post Detail ───────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_post_detail) {
            setClick(root, R.id.btnPostDetailBack, R.layout.screen_home);
            loadPostDetailScreen(root);

        // ── Chat ──────────────────────────────────────────────────
        } else if (layoutRes == R.layout.screen_chat) {
            setClick(root, R.id.btnChatBack, R.layout.screen_messages);
            loadChatScreen(root);
        }

        // Bottom nav everywhere
        setupBottomNav(root);
    }

    // ─── Real Data Loaders ─────────────────────────────────────────────────────

    private void loadHomeScreen(View root) {
        String token = bearer();
        if (token == null) {
            Log.e(TAG, "No token available for home screen");
            return;
        }

        Log.d(TAG, "Loading home screen with token: " + (token != null ? "present" : "null"));

        // Load today's prompt
        if (apiService != null) {
            apiService.getTodayPrompt(token).enqueue(new Callback<PromptResponse>() {
                @Override
                public void onResponse(@NonNull Call<PromptResponse> call, @NonNull Response<PromptResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().prompt != null) {
                        TextView tv = root.findViewById(R.id.tvPromptTitle);
                        TextView tvDesc = root.findViewById(R.id.tvPromptDescription);
                        if (tv != null) tv.setText(response.body().prompt.title);
                        if (tvDesc != null) tvDesc.setText(response.body().prompt.description);
                        ImageView ivBg = root.findViewById(R.id.ivPromptBackground);
                        if (ivBg != null) {
                            Glide.with(MainActivity.this)
                                .load("https://picsum.photos/seed/" + response.body().prompt.title.hashCode() + "/800/400")
                                .centerCrop()
                                .into(ivBg);
                        }
                    }
                }
                @Override public void onFailure(@NonNull Call<PromptResponse> call, @NonNull Throwable t) {}
            });
        } else {
            // Mock prompt when no backend
            TextView tv = root.findViewById(R.id.tvPromptTitle);
            TextView tvDesc = root.findViewById(R.id.tvPromptDescription);
            if (tv != null) tv.setText("Monochrome Monday");
            if (tvDesc != null) tvDesc.setText("Share a stunning black and white photo");
        }

        // Load feed
        RecyclerView rv = root.findViewById(R.id.rvFeed);
        if (rv == null) {
            Log.e(TAG, "RecyclerView rvFeed not found in home screen");
            return;
        }
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);
        
        Log.d(TAG, "Making feed API call");
        if (apiService != null) {
            apiService.getFeed(token, "new").enqueue(new Callback<FeedResponse>() {
                @Override
                public void onResponse(@NonNull Call<FeedResponse> call, @NonNull Response<FeedResponse> response) {
                    Log.d(TAG, "Feed response: " + response.code());
                    if (response.isSuccessful() && response.body() != null) {
                        List<com.example.chesh.network.models.FeedPost> posts = response.body().feed;
                        if (posts == null) posts = new ArrayList<>();
                        Log.d(TAG, "Feed loaded with " + posts.size() + " posts");
                        if (posts.isEmpty()) {
                            showEmptyState(rv, "No posts yet. Be the first to share! 📸");
                        } else {
                            clearEmptyState(rv);
                            setupFeedAdapter(rv, posts, token);
                        }
                    } else {
                        Log.e(TAG, "Feed response failed: " + response.code());
                        showEmptyState(rv, "Could not load feed. Server response: " + response.code());
                    }
                }
                @Override public void onFailure(@NonNull Call<FeedResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "Feed load failed", t);
                    showEmptyState(rv, "Could not load feed. Is the server running?");
                }
            });
        } else {
            // Show mock data when no backend
            Log.d(TAG, "No backend available, showing mock feed data");
            List<com.example.chesh.network.models.FeedPost> mockPosts = createMockFeedPosts();
            if (mockPosts.isEmpty()) {
                showEmptyState(rv, "No posts yet. Backend server needed for real data. 📸");
            } else {
                clearEmptyState(rv);
                setupFeedAdapter(rv, mockPosts, token);
            }
        }
    }

    private void setupFeedAdapter(RecyclerView rv, List<com.example.chesh.network.models.FeedPost> posts, String token) {
        rv.setAdapter(new FeedAdapter(MainActivity.this, posts, new FeedAdapter.FeedInteractionListener() {
            @Override
            public void onLikeClicked(com.example.chesh.network.models.FeedPost post, int position) {
                Log.d(TAG, "Like clicked for post " + post.id);
                if (apiService != null) {
                    if (likedPostIds.contains(post.id)) {
                        apiService.unlikePost(token, post.id).enqueue(new Callback<okhttp3.ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                                if (response.isSuccessful()) {
                                    likedPostIds.remove(post.id);
                                    post.likesCount = Math.max(0, post.likesCount - 1);
                                    post.hasLiked = false;
                                    RecyclerView.Adapter<?> adapter = rv.getAdapter();
                                    if (adapter != null) adapter.notifyItemChanged(position);
                                    Toast.makeText(MainActivity.this, "Unliked", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Unlike failed", Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                                Toast.makeText(MainActivity.this, "Unlike failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        apiService.likePost(token, post.id).enqueue(new Callback<okhttp3.ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                                if (response.isSuccessful()) {
                                    likedPostIds.add(post.id);
                                    post.likesCount += 1;
                                    post.hasLiked = true;
                                    RecyclerView.Adapter<?> adapter = rv.getAdapter();
                                    if (adapter != null) adapter.notifyItemChanged(position);
                                    Toast.makeText(MainActivity.this, "Liked!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Like failed", Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                                Toast.makeText(MainActivity.this, "Like failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    // Mock like functionality
                    if (likedPostIds.contains(post.id)) {
                        likedPostIds.remove(post.id);
                        post.likesCount = Math.max(0, post.likesCount - 1);
                        post.hasLiked = false;
                        Toast.makeText(MainActivity.this, "Unliked (mock)", Toast.LENGTH_SHORT).show();
                    } else {
                        likedPostIds.add(post.id);
                        post.likesCount += 1;
                        post.hasLiked = true;
                        Toast.makeText(MainActivity.this, "Liked! (mock)", Toast.LENGTH_SHORT).show();
                    }
                    RecyclerView.Adapter<?> adapter = rv.getAdapter();
                    if (adapter != null) adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onCommentClicked(com.example.chesh.network.models.FeedPost post, int position) {
                currentPostId = post.id;
                currentPostMediaUrl = (post.media != null && !post.media.isEmpty()) ? post.media.get(0) : null;
                renderScreen(R.layout.screen_post_detail);
            }

            @Override
            public void onImageClicked(com.example.chesh.network.models.FeedPost post, int position) {
                currentPostId = post.id;
                currentPostMediaUrl = (post.media != null && !post.media.isEmpty()) ? post.media.get(0) : null;
                renderScreen(R.layout.screen_post_detail);
            }
        }));
    }

    private List<com.example.chesh.network.models.FeedPost> createMockFeedPosts() {
        List<com.example.chesh.network.models.FeedPost> mockPosts = new ArrayList<>();
        
        // Create mock posts for testing
        for (int i = 1; i <= 8; i++) {
            com.example.chesh.network.models.FeedPost post = new com.example.chesh.network.models.FeedPost();
            post.id = i;
            post.caption = "Mock post " + i + " - This is a test post to show UI functionality";
            post.likesCount = i * 5;
            post.commentsCount = i * 2;
            post.createdAt = "2024-04-24T10:0" + i + ":00Z";
            
            // Create mock user
            com.example.chesh.network.models.UserDto user = new com.example.chesh.network.models.UserDto();
            user.id = i;
            user.name = "Test User " + i;
            user.email = "user" + i + "@test.com";
            post.user = user;
            
            // Create mock media
            post.media = new ArrayList<>();
            post.media.add("https://picsum.photos/400/400?random=" + i);
            
            mockPosts.add(post);
        }
        
        return mockPosts;
    }

    private List<com.example.chesh.network.models.UserPost> createMockUserPosts() {
        List<com.example.chesh.network.models.UserPost> mockUserPosts = new ArrayList<>();
        
        // Create mock user posts for profile grid
        for (int i = 1; i <= 6; i++) {
            com.example.chesh.network.models.UserPost post = new com.example.chesh.network.models.UserPost();
            post.id = i;
            post.caption = "My post " + i;
            post.createdAt = "2024-04-24T10:0" + i + ":00Z";
            
            // Create mock media
            post.PostMedia = new ArrayList<>();
            com.example.chesh.network.models.PostMediaItem media = new com.example.chesh.network.models.PostMediaItem();
            media.mediaUrl = "https://picsum.photos/400/400?random=" + (i + 10);
            post.PostMedia.add(media);
            
            mockUserPosts.add(post);
        }
        
        return mockUserPosts;
    }

    private List<com.example.chesh.network.models.NotificationItem> createMockNotifications() {
        List<com.example.chesh.network.models.NotificationItem> mockNotifications = new ArrayList<>();
        
        // Create mock notifications for activity
        String[] notificationTypes = {"like", "comment", "follow"};
        String[] messages = {
            "Test User 1 liked your post",
            "Test User 2 commented on your post",
            "Test User 3 started following you",
            "Test User 1 commented: Great photo!",
            "Test User 2 liked your post",
            "Test User 3 liked your post"
        };
        
        for (int i = 0; i < messages.length; i++) {
            com.example.chesh.network.models.NotificationItem notif = new com.example.chesh.network.models.NotificationItem();
            notif.id = i + 1;
            notif.notificationType = notificationTypes[i % notificationTypes.length];
            notif.entityType = "post";
            notif.entityId = (long) ((i % 3) + 1);
            notif.createdAt = "2024-04-24T10:0" + (i + 1) + ":00Z";
            
            // Create mock actor user
            com.example.chesh.network.models.UserDto actor = new com.example.chesh.network.models.UserDto();
            actor.id = (i % 3) + 1;
            actor.name = "Test User " + ((i % 3) + 1);
            notif.ActorUser = actor;
            
            mockNotifications.add(notif);
        }
        
        return mockNotifications;
    }

    private List<com.example.chesh.network.models.ConversationItem> createMockConversations() {
        List<com.example.chesh.network.models.ConversationItem> mockConversations = new ArrayList<>();
        
        // Create mock conversations
        for (int i = 1; i <= 4; i++) {
            com.example.chesh.network.models.ConversationItem conv = new com.example.chesh.network.models.ConversationItem();
            conv.id = i;
            conv.conversationType = "direct";
            conv.updatedAt = "2024-04-24T10:0" + i + ":00Z";
            
            mockConversations.add(conv);
        }
        
        return mockConversations;
    }

    private void loadGalleryScreen(View root, String sort) {
        String token = bearer();
        if (token == null) return;

        RecyclerView rv = root.findViewById(R.id.rvGallery);
        if (rv == null) return;
        GridLayoutManager glm = new GridLayoutManager(this, 2);
        rv.setLayoutManager(glm);
        clearEmptyState(rv);

        apiService.getFeed(token, sort).enqueue(new Callback<FeedResponse>() {
            @Override
            public void onResponse(@NonNull Call<FeedResponse> call, @NonNull Response<FeedResponse> response) {
                Log.d(TAG, "Gallery feed response: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.FeedPost> posts = response.body().feed;
                    if (posts == null) posts = new ArrayList<>();
                    Log.d(TAG, "Gallery loaded with " + posts.size() + " posts");
                    if (posts.isEmpty()) {
                        showEmptyState(rv, "No photos yet. Submit a post to see it here! 🖼️");
                    } else {
                        clearEmptyState(rv);
                        rv.setAdapter(new GalleryAdapter(MainActivity.this, posts, (post, position) -> {
                            Log.d(TAG, "Gallery image clicked: post " + post.id);
                            currentPostId = post.id;
                            currentPostMediaUrl = (post.media != null && !post.media.isEmpty()) ? post.media.get(0) : null;
                            renderScreen(R.layout.screen_post_detail);
                        }));
                    }
                } else {
                    Log.e(TAG, "Gallery response failed: " + response.code());
                    showEmptyState(rv, "Could not load gallery. Server response: " + response.code());
                }
            }
            @Override public void onFailure(@NonNull Call<FeedResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Gallery load failed", t);
                showEmptyState(rv, "Could not load gallery.");
            }
        });
    }

    private void loadProfileScreen(View root) {
        if (currentViewedUserId != -1) {
            loadOtherUserProfileData(root, currentViewedUserId);
            return;
        }
        String token = bearer();
        if (token == null) return;

        UserDto me = sessionStore.getUser();
        if (me == null) {
            apiService.getMe(token).enqueue(new Callback<MeResponse>() {
                @Override
                public void onResponse(@NonNull Call<MeResponse> call, @NonNull Response<MeResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().user != null) {
                        sessionStore.saveUser(response.body().user);
                        loadProfileScreen(root);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<MeResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "Failed to hydrate session user for profile", t);
                }
            });
            return;
        }

        // Fill static fields from session (instant, no network call needed)
        TextView tvUsername = root.findViewById(R.id.tvProfileUsername);
        TextView tvName     = root.findViewById(R.id.tvProfileName);
        TextView tvEmail    = root.findViewById(R.id.tvProfileEmail);
        TextView tvBio      = root.findViewById(R.id.tvProfileBio);
        TextView tvPronouns = root.findViewById(R.id.tvProfilePronouns);
        ImageView ivAvatar  = root.findViewById(R.id.ivProfileAvatar);

        View btnEdit = root.findViewById(R.id.btnProfileEdit);
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> renderScreen(R.layout.screen_edit_profile));
        }

        View btnShare = root.findViewById(R.id.btnProfileShare);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out my profile");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Check out my profile on Snapshot: https://chesh.app/profile/" + me.id);
                startActivity(android.content.Intent.createChooser(shareIntent, "Share via"));
            });
        }

        if (tvUsername != null) tvUsername.setText(me.email != null ? me.email.split("@")[0] : "me");
        if (tvName != null)     tvName.setText(!TextUtils.isEmpty(me.name) ? me.name : me.email);
        if (tvEmail != null)    tvEmail.setText(me.email != null ? me.email : "");
        
        if (tvBio != null) {
            tvBio.setText(me.bio != null ? me.bio : "");
            tvBio.setVisibility(TextUtils.isEmpty(me.bio) ? View.GONE : View.VISIBLE);
        }
        if (tvPronouns != null) {
            tvPronouns.setText(me.pronouns != null ? me.pronouns : "");
            tvPronouns.setVisibility(TextUtils.isEmpty(me.pronouns) ? View.GONE : View.VISIBLE);
        }

        if (ivAvatar != null) {
            if (!TextUtils.isEmpty(me.avatarUrl)) {
                Glide.with(this).load(me.avatarUrl).circleCrop().into(ivAvatar);
            } else {
                ivAvatar.setImageResource(android.R.color.transparent);
                ivAvatar.setBackgroundResource(R.color.pastel_bg_lavender);
            }
        }

        // Fetch real stats
        if (apiService != null) {
            apiService.getUserStats(token, me.id).enqueue(new Callback<UserStatsResponse>() {
                @Override
                public void onResponse(@NonNull Call<UserStatsResponse> call, @NonNull Response<UserStatsResponse> response) {
                    Log.d(TAG, "User stats response: " + response.code());
                    if (response.isSuccessful() && response.body() != null && response.body().stats != null) {
                        com.example.chesh.network.models.UserStats s = response.body().stats;
                        Log.d(TAG, "Stats: posts=" + s.postsCount + ", followers=" + s.followersCount + ", following=" + s.followingCount);
                        setText(root, R.id.tvPostsCount, String.valueOf(s.postsCount));
                        setText(root, R.id.tvFollowersCount, formatCount(s.followersCount));
                        setText(root, R.id.tvFollowingCount, formatCount(s.followingCount));
                    } else {
                        Log.e(TAG, "User stats response failed: " + response.code());
                        // Set default values when API fails
                        setText(root, R.id.tvPostsCount, "0");
                        setText(root, R.id.tvFollowersCount, "0");
                        setText(root, R.id.tvFollowingCount, "0");
                    }
                }
                @Override public void onFailure(@NonNull Call<UserStatsResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "User stats load failed", t);
                    // Set default values when API fails
                    setText(root, R.id.tvPostsCount, "0");
                    setText(root, R.id.tvFollowersCount, "0");
                    setText(root, R.id.tvFollowingCount, "0");
                }
            });
        } else {
            // No backend available, set mock values
            setText(root, R.id.tvPostsCount, "3");
            setText(root, R.id.tvFollowersCount, "12");
            setText(root, R.id.tvFollowingCount, "8");
        }

        // Fetch user's own posts for grid
        RecyclerView rvGrid = root.findViewById(R.id.rvProfilePosts);
        if (rvGrid != null) {
            rvGrid.setLayoutManager(new GridLayoutManager(this, 3));
            clearEmptyState(rvGrid);
            
            if (apiService != null) {
                apiService.getUserPosts(token, me.id).enqueue(new Callback<UserPostsResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<UserPostsResponse> call, @NonNull Response<UserPostsResponse> response) {
                        Log.d(TAG, "User posts response: " + response.code());
                        if (response.isSuccessful() && response.body() != null) {
                            List<com.example.chesh.network.models.UserPost> posts = response.body().posts;
                            if (posts == null) posts = new ArrayList<>();
                            Log.d(TAG, "User posts loaded: " + posts.size() + " posts");
                            if (posts.isEmpty()) {
                                showEmptyState(rvGrid, "No posts yet. Share your first photo! 📷");
                            } else {
                                clearEmptyState(rvGrid);
                                rvGrid.setAdapter(new ProfileGridAdapter(MainActivity.this, posts, (post, position) -> {
                                    currentPostId = post.id;
                                    currentPostMediaUrl = (post.PostMedia != null && !post.PostMedia.isEmpty())
                                            ? post.PostMedia.get(0).mediaUrl : null;
                                    renderScreen(R.layout.screen_post_detail);
                                }));
                            }
                        } else {
                            Log.e(TAG, "User posts response failed: " + response.code());
                            showEmptyState(rvGrid, "Could not load posts. Server response: " + response.code());
                        }
                    }
                    @Override public void onFailure(@NonNull Call<UserPostsResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "User posts load failed", t);
                        showEmptyState(rvGrid, "Could not load posts. Error: " + t.getMessage());
                    }
                });
            } else {
                // No backend available, show mock posts
                List<com.example.chesh.network.models.UserPost> mockUserPosts = createMockUserPosts();
                if (mockUserPosts.isEmpty()) {
                    showEmptyState(rvGrid, "No posts yet. Share your first photo! 📷");
                } else {
                    clearEmptyState(rvGrid);
                    rvGrid.setAdapter(new ProfileGridAdapter(MainActivity.this, mockUserPosts, (post, position) -> {
                        currentPostId = post.id;
                        currentPostMediaUrl = (post.PostMedia != null && !post.PostMedia.isEmpty())
                                ? post.PostMedia.get(0).mediaUrl : null;
                        renderScreen(R.layout.screen_post_detail);
                    }));
                }
            }
        }
    }

    private void loadLeaderboardScreen(View root) {
        String token = bearer();
        if (token == null) return;

        RecyclerView rv = root.findViewById(R.id.rvLeaderboard);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);

        long myId = sessionStore.getUserId();

        apiService.getCurrentLeaderboard(token).enqueue(new Callback<LeaderboardResponse>() {
            @Override
            public void onResponse(@NonNull Call<LeaderboardResponse> call, @NonNull Response<LeaderboardResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().week != null) {
                        setText(root, R.id.tvLeaderboardWeek,
                                response.body().week.weekStart + " – " + response.body().week.weekEnd);
                    } else {
                        setText(root, R.id.tvLeaderboardWeek, "No active week");
                    }
                    List<com.example.chesh.network.models.LeaderboardEntry> entries = response.body().entries;
                    if (entries == null) entries = new ArrayList<>();
                    Log.d(TAG, "Leaderboard entries count: " + entries.size());
                    if (entries.isEmpty()) {
                        showEmptyState(rv, "No scores yet this week. Post photos to earn points! 🏆");
                    } else {
                        clearEmptyState(rv);
                        rv.setAdapter(new LeaderboardAdapter(MainActivity.this, entries, myId));
                    }
                } else {
                    setText(root, R.id.tvLeaderboardWeek, "No active week");
                    showEmptyState(rv, "Leaderboard starts once players earn points 🏆");
                }
            }
            @Override public void onFailure(@NonNull Call<LeaderboardResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Leaderboard load failed", t);
                showEmptyState(rv, "Could not load leaderboard.");
            }
        });
    }

    private void loadNotificationsScreen(View root) {
        String token = bearer();
        if (token == null) return;

        RecyclerView rv = root.findViewById(R.id.rvNotifications);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);

        if (apiService != null) {
            apiService.getNotifications(token, null).enqueue(new Callback<NotificationsResponse>() {
                @Override
                public void onResponse(@NonNull Call<NotificationsResponse> call, @NonNull Response<NotificationsResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<com.example.chesh.network.models.NotificationItem> items = response.body().notifications;
                        if (items == null) items = new ArrayList<>();
                        if (items.isEmpty()) {
                            showEmptyState(rv, "No notifications yet 🔔");
                        } else {
                            clearEmptyState(rv);
                            rv.setAdapter(new NotificationsAdapter(MainActivity.this, items, sessionStore.getUserId()));
                        }
                    } else {
                        Log.e(TAG, "Notifications response failed: " + response.code());
                        showEmptyState(rv, "Could not load notifications.");
                    }
                }
                @Override public void onFailure(@NonNull Call<NotificationsResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "Notifications load failed", t);
                    showEmptyState(rv, "Could not load notifications.");
                }
            });
        } else {
            // No backend available, show mock notifications
            List<com.example.chesh.network.models.NotificationItem> mockNotifications = createMockNotifications();
            if (mockNotifications.isEmpty()) {
                showEmptyState(rv, "No notifications yet 🔔");
            } else {
                clearEmptyState(rv);
                rv.setAdapter(new NotificationsAdapter(MainActivity.this, mockNotifications, sessionStore.getUserId()));
            }
        }
    }

    private void loadSocialHubScreen(View root) {
        String token = bearer();
        if (token == null) return;
        RecyclerView rv = root.findViewById(R.id.rvSocialActivity);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);
        apiService.getNotifications(token, null).enqueue(new Callback<NotificationsResponse>() {
            @Override
            public void onResponse(@NonNull Call<NotificationsResponse> call, @NonNull Response<NotificationsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.NotificationItem> items = response.body().notifications;
                    if (items == null) items = new ArrayList<>();
                    if (items.isEmpty()) {
                        showEmptyState(rv, "No activity yet. Start interacting! 🎉");
                    } else {
                        clearEmptyState(rv);
                        rv.setAdapter(new NotificationsAdapter(MainActivity.this, items, sessionStore.getUserId()));
                    }
                }
            }
            @Override public void onFailure(@NonNull Call<NotificationsResponse> call, @NonNull Throwable t) {
                showEmptyState(rv, "Could not load activity.");
            }
        });
    }

    private void loadMessagesScreen(View root) {
        String token = bearer();
        if (token == null) return;

        RecyclerView rv = root.findViewById(R.id.rvConversations);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);

        // Bind notification bell
        View bell = root.findViewById(R.id.toggleActivity);
        if (bell != null) {
            bell.setOnClickListener(v -> renderScreen(R.layout.screen_social_hub));
        }

        if (apiService != null) {
            apiService.getConversations(token).enqueue(new Callback<ConversationsResponse>() {
                @Override
                public void onResponse(@NonNull Call<ConversationsResponse> call, @NonNull Response<ConversationsResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<com.example.chesh.network.models.ConversationItem> items = response.body().conversations;
                        if (items == null) items = new ArrayList<>();
                        if (items.isEmpty()) {
                            showEmptyState(rv, "No messages yet. Follow someone to start chatting! 💬");
                        } else {
                            clearEmptyState(rv);
                            rv.setAdapter(new ConversationsAdapter(MainActivity.this, items, (item, position) -> {
                                // Navigate to chat screen
                                currentConversationId = item.id;
                                renderScreen(R.layout.screen_chat);
                            }));
                        }
                    } else {
                        Log.e(TAG, "Conversations response failed: " + response.code());
                        showEmptyState(rv, "Could not load messages.");
                    }
                }
                @Override public void onFailure(@NonNull Call<ConversationsResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "Conversations load failed", t);
                    showEmptyState(rv, "Could not load messages.");
                }
            });
        } else {
            // No backend available, show mock conversations
            List<com.example.chesh.network.models.ConversationItem> mockConversations = createMockConversations();
            if (mockConversations.isEmpty()) {
                showEmptyState(rv, "No messages yet. Follow someone to start chatting! 💬");
            } else {
                clearEmptyState(rv);
                rv.setAdapter(new ConversationsAdapter(MainActivity.this, mockConversations, (item, position) -> {
                    // Navigate to chat screen
                    currentConversationId = item.id;
                    renderScreen(R.layout.screen_chat);
                }));
            }
        }
    }

    private void loadEditProfileScreen(View root) {
        UserDto me = sessionStore.getUser();
        if (me == null) return;

        EditText etName     = root.findViewById(R.id.etEditName);
        EditText etUsername = root.findViewById(R.id.etEditUsername);
        EditText etBio      = root.findViewById(R.id.etEditBio);
        EditText etPronouns = root.findViewById(R.id.etEditPronouns);
        ImageView ivAvatar  = root.findViewById(R.id.ivEditAvatar);

        if (etName != null && !TextUtils.isEmpty(me.name))   etName.setText(me.name);
        if (etUsername != null && !TextUtils.isEmpty(me.email)) etUsername.setText(me.email);
        if (etBio != null && !TextUtils.isEmpty(me.bio)) etBio.setText(me.bio);
        if (etPronouns != null && !TextUtils.isEmpty(me.pronouns)) etPronouns.setText(me.pronouns);
        
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> pickAvatarMedia.launch(
                    new androidx.activity.result.PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build()
            ));
            
            if (selectedAvatarUri != null) {
                ivAvatar.setImageURI(selectedAvatarUri);
            } else if (!TextUtils.isEmpty(me.avatarUrl)) {
                Glide.with(this).load(me.avatarUrl).circleCrop().into(ivAvatar);
            }
        }
    }

    private void handleSaveProfile(View root) {
        String token = bearer();
        if (token == null || apiService == null) {
            Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText etName = root.findViewById(R.id.etEditName);
        EditText etBio = root.findViewById(R.id.etEditBio);
        EditText etPronouns = root.findViewById(R.id.etEditPronouns);
        if (etName == null) { renderScreen(R.layout.screen_profile); return; }

        String newName = etName.getText().toString().trim();
        String newBio = etBio != null ? etBio.getText().toString().trim() : "";
        String newPronouns = etPronouns != null ? etPronouns.getText().toString().trim() : "";
        if (newName.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Convert avatar to base64 if selected
        String base64Image = null;
        if (selectedAvatarUri != null) {
            try {
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), selectedAvatarUri);
                int maxSize = 512;
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float scale = Math.min(((float) maxSize / width), ((float) maxSize / height));
                if (scale < 1.0f) {
                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true);
                }
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] imageBytes = baos.toByteArray();
                base64Image = "data:image/jpeg;base64," + android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
            } catch (Exception e) {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_LONG).show();
                return;
            }
        }
        
        String json;
        try { 
            JSONObject p = new JSONObject(); 
            p.put("name", newName); 
            p.put("bio", newBio);
            p.put("pronouns", newPronouns);
            if (base64Image != null) {
                p.put("avatarUrl", base64Image);
            }
            json = p.toString(); 
        } catch (JSONException e) { return; }

        okhttp3.RequestBody body = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json; charset=utf-8"));
        apiService.updateMe(token, body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    // Fetch updated profile from server
                    apiService.getMe(token).enqueue(new Callback<com.example.chesh.network.models.MeResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<com.example.chesh.network.models.MeResponse> call, @NonNull Response<com.example.chesh.network.models.MeResponse> response2) {
                            if (response2.isSuccessful() && response2.body() != null && response2.body().user != null) {
                                sessionStore.saveUser(response2.body().user);
                            }
                            selectedAvatarUri = null; // Clear selected
                            Toast.makeText(MainActivity.this, "Profile updated! ✓", Toast.LENGTH_SHORT).show();
                            renderScreen(R.layout.screen_profile);
                        }
                        @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.MeResponse> call, @NonNull Throwable t) {
                            selectedAvatarUri = null;
                            renderScreen(R.layout.screen_profile);
                        }
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Could not save profile (code " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Could not save profile: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFollowersList(long userId) {
        String token = bearer();
        if (token == null || apiService == null) return;
        apiService.getUserFollowers(token, userId).enqueue(new Callback<com.example.chesh.network.models.FollowersResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.FollowersResponse> call,
                                   @NonNull Response<com.example.chesh.network.models.FollowersResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.UserDto> followers = response.body().followers;
                    if (followers == null || followers.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No followers yet", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RecyclerView rv = new RecyclerView(MainActivity.this);
                    rv.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                    rv.setPadding(0, 16, 0, 16);
                    rv.setAdapter(new com.example.chesh.network.adapters.PostLikersAdapter(
                            MainActivity.this, followers,
                            user -> {
                                openDirectConversation(user.id);
                            },
                            user -> openUserProfile(user.id)
                    ));
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("👥 Followers (" + followers.size() + ")")
                            .setView(rv)
                            .setNegativeButton("Close", null)
                            .show();
                } else {
                    Toast.makeText(MainActivity.this, "Could not load followers", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.FollowersResponse> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Could not load followers", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFollowingList(long userId) {
        String token = bearer();
        if (token == null || apiService == null) return;
        apiService.getUserFollowing(token, userId).enqueue(new Callback<com.example.chesh.network.models.FollowingResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.FollowingResponse> call,
                                   @NonNull Response<com.example.chesh.network.models.FollowingResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.UserDto> following = response.body().following;
                    if (following == null || following.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Not following anyone yet", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RecyclerView rv = new RecyclerView(MainActivity.this);
                    rv.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                    rv.setPadding(0, 16, 0, 16);
                    rv.setAdapter(new com.example.chesh.network.adapters.PostLikersAdapter(
                            MainActivity.this, following,
                            user -> {
                                openDirectConversation(user.id);
                            },
                            user -> openUserProfile(user.id)
                    ));
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("👥 Following (" + following.size() + ")")
                            .setView(rv)
                            .setNegativeButton("Close", null)
                            .show();
                } else {
                    Toast.makeText(MainActivity.this, "Could not load following", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.FollowingResponse> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Could not load following", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPostDetailScreen(View root) {
        String token = bearer();
        if (token == null || currentPostId == -1) return;

        Log.d(TAG, "Loading post detail for post ID: " + currentPostId);

        // Immediately show cached image so there's no blank while API loads
        ImageView ivPhoto = root.findViewById(R.id.ivPostDetailPhoto);
        if (ivPhoto != null && currentPostMediaUrl != null && !currentPostMediaUrl.isEmpty()) {
            Glide.with(this).load(currentPostMediaUrl).centerCrop()
                .error(android.R.drawable.ic_menu_report_image).into(ivPhoto);
        }

        if (apiService == null) return;

        // Fetch full post data
        apiService.getPost(token, currentPostId).enqueue(new Callback<com.example.chesh.network.models.PostResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.PostResponse> call,
                                   @NonNull Response<com.example.chesh.network.models.PostResponse> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().post == null) {
                    Log.e(TAG, "Failed to load post: " + response.code());
                    return;
                }
                com.example.chesh.network.models.FeedPost post = response.body().post;

                // Resolve media URL: PostMedia (single-post) → media list (feed) → cached URL
                String mediaUrl = currentPostMediaUrl;
                if (post.PostMedia != null && !post.PostMedia.isEmpty()) {
                    mediaUrl = post.PostMedia.get(0).mediaUrl;
                } else if (post.media != null && !post.media.isEmpty()) {
                    mediaUrl = post.media.get(0);
                }
                if (ivPhoto != null && mediaUrl != null && !mediaUrl.isEmpty()) {
                    Glide.with(MainActivity.this).load(mediaUrl).centerCrop()
                        .error(android.R.drawable.ic_menu_report_image).into(ivPhoto);
                }

                // User header
                if (post.user != null) {
                    ImageView ivAvatar = root.findViewById(R.id.ivPostDetailAvatar);
                    if (ivAvatar != null && !TextUtils.isEmpty(post.user.avatarUrl)) {
                        Glide.with(MainActivity.this).load(post.user.avatarUrl).circleCrop().into(ivAvatar);
                    }
                    TextView tvUsername = root.findViewById(R.id.tvPostDetailUsername);
                    if (tvUsername != null) {
                        String uname = !TextUtils.isEmpty(post.user.name) ? post.user.name
                                : (post.user.email != null ? post.user.email.split("@")[0] : "User");
                        tvUsername.setText(uname);
                        final long postUserId = post.user.id;
                        android.view.View.OnClickListener profileClick = v -> openUserProfile(postUserId);
                        tvUsername.setOnClickListener(profileClick);
                        if (ivAvatar != null) ivAvatar.setOnClickListener(profileClick);
                    }
                }

                // Caption
                TextView tvCaption = root.findViewById(R.id.tvPostDetailCaption);
                if (tvCaption != null) {
                    String caption = (post.user != null && !TextUtils.isEmpty(post.user.name))
                            ? post.user.name + "  " + (post.caption != null ? post.caption : "")
                            : (post.caption != null ? post.caption : "");
                    tvCaption.setText(caption);
                }

                // Delete Button
                TextView btnDelete = root.findViewById(R.id.btnPostDetailDelete);
                if (btnDelete != null) {
                    if (post.user != null && post.user.id == sessionStore.getUserId()) {
                        btnDelete.setVisibility(View.VISIBLE);
                        btnDelete.setOnClickListener(v -> {
                            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Delete Post")
                                .setMessage("Are you sure you want to delete this post?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    apiService.deletePost(token, currentPostId).enqueue(new Callback<okhttp3.ResponseBody>() {
                                        @Override
                                        public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                                            if (response.isSuccessful()) {
                                                Toast.makeText(MainActivity.this, "Post deleted", Toast.LENGTH_SHORT).show();
                                                onBackPressed();
                                            } else {
                                                Toast.makeText(MainActivity.this, "Could not delete post", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                        @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                                            Toast.makeText(MainActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        });
                    } else {
                        btnDelete.setVisibility(View.GONE);
                    }
                }

                // Like button state
                TextView btnLike = root.findViewById(R.id.btnPostDetailLike);
                if (btnLike != null) {
                    boolean isLiked = likedPostIds.contains(currentPostId);
                    btnLike.setText((isLiked ? "♥" : "♡") + " " + post.likesCount);
                    if (isLiked) {
                        btnLike.setTextColor(android.graphics.Color.parseColor("#E57373"));
                    } else {
                        btnLike.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.pastel_text_primary));
                    }
                }

                // Comments count
                TextView tvCommentsCount = root.findViewById(R.id.tvPostDetailCommentsCount);
                if (tvCommentsCount != null) tvCommentsCount.setText("💬 " + post.commentsCount);

                // "N likes – tap to see who liked" label
                TextView tvLikedBy = root.findViewById(R.id.tvPostDetailLikedBy);
                if (tvLikedBy != null) {
                    if (post.likesCount > 0) {
                        tvLikedBy.setText(post.likesCount + " like" + (post.likesCount == 1 ? "" : "s")
                                + "  •  tap to see who liked");
                        tvLikedBy.setVisibility(View.VISIBLE);
                        tvLikedBy.setOnClickListener(v -> showPostLikers(currentPostId));
                    } else {
                        tvLikedBy.setVisibility(View.GONE);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<com.example.chesh.network.models.PostResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Error loading post", t);
            }
        });

        // Wire like button
        TextView btnLike = root.findViewById(R.id.btnPostDetailLike);
        if (btnLike != null) {
            btnLike.setOnClickListener(v -> {
                if (likedPostIds.contains(currentPostId)) {
                    apiService.unlikePost(token, currentPostId).enqueue(new Callback<okhttp3.ResponseBody>() {
                        @Override public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                            if (response.isSuccessful()) { likedPostIds.remove(currentPostId); Toast.makeText(MainActivity.this, "Unliked", Toast.LENGTH_SHORT).show(); loadPostDetailScreen(root); }
                        }
                        @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) { Toast.makeText(MainActivity.this, "Unlike failed", Toast.LENGTH_SHORT).show(); }
                    });
                } else {
                    apiService.likePost(token, currentPostId).enqueue(new Callback<okhttp3.ResponseBody>() {
                        @Override public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                            if (response.isSuccessful()) { likedPostIds.add(currentPostId); Toast.makeText(MainActivity.this, "Liked! ❤️", Toast.LENGTH_SHORT).show(); loadPostDetailScreen(root); }
                        }
                        @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) { Toast.makeText(MainActivity.this, "Like failed", Toast.LENGTH_SHORT).show(); }
                    });
                }
            });
        }

        // Wire comment send
        TextView btnSend = root.findViewById(R.id.btnPostCommentSend);
        EditText etInput = root.findViewById(R.id.etPostCommentInput);
        if (btnSend != null && etInput != null) {
            btnSend.setOnClickListener(v -> {
                String commentText = etInput.getText().toString().trim();
                if (commentText.isEmpty()) { Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show(); return; }
                String json;
                try { JSONObject p = new JSONObject(); p.put("commentText", commentText); json = p.toString(); }
                catch (JSONException e) { return; }
                okhttp3.RequestBody body = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json; charset=utf-8"));
                apiService.postComment(token, currentPostId, body).enqueue(new Callback<okhttp3.ResponseBody>() {
                    @Override public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                        if (response.isSuccessful()) { etInput.setText(""); Toast.makeText(MainActivity.this, "Comment posted!", Toast.LENGTH_SHORT).show(); loadPostDetailScreen(root); }
                        else Toast.makeText(MainActivity.this, "Failed to post comment", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) { Toast.makeText(MainActivity.this, "Comment failed", Toast.LENGTH_SHORT).show(); }
                });
            });
        }

        // Load comments
        RecyclerView rv = root.findViewById(R.id.rvPostComments);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);
        apiService.getPostComments(token, currentPostId).enqueue(new Callback<com.example.chesh.network.models.CommentsResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.CommentsResponse> call,
                                   @NonNull Response<com.example.chesh.network.models.CommentsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.CommentItem> comments = response.body().comments;
                    if (comments == null) comments = new ArrayList<>();
                    if (comments.isEmpty()) showEmptyState(rv, "No comments yet. Be the first! 💬");
                    else { clearEmptyState(rv); rv.setAdapter(new com.example.chesh.network.adapters.CommentsAdapter(MainActivity.this, comments, userId -> openUserProfile(userId))); }
                }
            }
            @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.CommentsResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Comments load failed", t);
                showEmptyState(rv, "Could not load comments.");
            }
        });
    }

    private void loadChatScreen(View root) {
        String token = bearer();
        if (token == null || currentConversationId == -1) return;

        // Wire send button
        TextView btnSend = root.findViewById(R.id.btnChatSend);
        EditText etInput = root.findViewById(R.id.etChatInput);
        if (btnSend != null && etInput != null) {
            btnSend.setOnClickListener(v -> {
                String message = etInput.getText().toString().trim();
                if (message.isEmpty()) {
                    Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
                    return;
                }

                okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
                String json;
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("body", message);
                    json = payload.toString();
                } catch (JSONException exception) {
                    Toast.makeText(this, "Could not build message payload.", Toast.LENGTH_SHORT).show();
                    return;
                }
                okhttp3.RequestBody body = okhttp3.RequestBody.create(json, JSON);

                apiService.postMessage(token, currentConversationId, body).enqueue(new Callback<okhttp3.ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                        if (response.isSuccessful()) {
                            etInput.setText("");
                            Toast.makeText(MainActivity.this, "Message sent!", Toast.LENGTH_SHORT).show();
                            loadChatScreen(root); // Reload messages
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                        Toast.makeText(MainActivity.this, "Message failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        // Load messages
        RecyclerView rv = root.findViewById(R.id.rvChatMessages);
        if (rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this));
        clearEmptyState(rv);

        apiService.getConversationMessages(token, currentConversationId).enqueue(new Callback<com.example.chesh.network.models.MessagesResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.MessagesResponse> call, @NonNull Response<com.example.chesh.network.models.MessagesResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.MessageItem> messages = response.body().messages;
                    if (messages == null) messages = new ArrayList<>();
                    if (messages.isEmpty()) {
                        showEmptyState(rv, "No messages yet. Start the conversation! 💬");
                    } else {
                        clearEmptyState(rv);
                        rv.setAdapter(new com.example.chesh.network.adapters.ChatMessagesAdapter(MainActivity.this, messages));
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<com.example.chesh.network.models.MessagesResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Messages load failed", t);
                showEmptyState(rv, "Could not load messages.");
            }
        });
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    // ─── Submission ────────────────────────────────────────────────────────────

    private void handleSubmitPost(View root) {
        if (mockStrikeLevel == 2) {
            renderScreen(R.layout.screen_moderation_appeal);
            return;
        }
        String token = bearer();
        if (token == null) { Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show(); return; }
        if (selectedPhotoUri == null) {
            Toast.makeText(this, "Please select a photo first.", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText etCaption = root.findViewById(R.id.etCaption);
        String caption = etCaption != null ? etCaption.getText().toString().trim() : "";
        if (caption.isEmpty()) caption = "📸";

        // Convert image to base64
        String base64Image = null;
        try {
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(), selectedPhotoUri);
            
            // Resize image to max 1024x1024 to reduce size
            int maxSize = 1024;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = Math.min(((float) maxSize / width), ((float) maxSize / height));
            if (scale < 1.0f) {
                int newWidth = Math.round(width * scale);
                int newHeight = Math.round(height * scale);
                bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            }
            
            // Convert to base64
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] imageBytes = baos.toByteArray();
            base64Image = "data:image/jpeg;base64," + android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
            
            Log.d(TAG, "Image converted to base64, size: " + base64Image.length() + " chars");
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to base64", e);
            Toast.makeText(this, "Failed to process image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String finalCaption = caption;
        String finalMediaUrl = base64Image;
        android.util.Log.d(TAG, "Submitting post: caption=" + finalCaption);

        okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
        String json;
        try {
            JSONObject payload = new JSONObject();
            payload.put("caption", finalCaption);
            payload.put("mediaUrl", finalMediaUrl);
            json = payload.toString();
        } catch (JSONException exception) {
            Toast.makeText(this, "Could not build post payload.", Toast.LENGTH_LONG).show();
            return;
        }
        okhttp3.RequestBody body = okhttp3.RequestBody.create(json, JSON);

        apiService.createPost(token, body).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<okhttp3.ResponseBody> call,
                                   @NonNull retrofit2.Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    selectedPhotoUri = null; // reset for next post
                    Toast.makeText(MainActivity.this, "Posted! ✅", Toast.LENGTH_SHORT).show();
                    renderScreen(R.layout.screen_interstitial_reward);
                } else {
                    Toast.makeText(MainActivity.this,
                            "Post failed (HTTP " + response.code() + "). Check server logs.",
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(@NonNull retrofit2.Call<okhttp3.ResponseBody> call,
                                  @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Post failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ─── Empty State Helper ────────────────────────────────────────────────────

    private void showEmptyState(RecyclerView rv, String message) {
        if (!(rv.getParent() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) rv.getParent();

        // Remove any previous empty state view
        View old = parent.findViewWithTag("empty_state");
        if (old != null) parent.removeView(old);

        // Record the RecyclerView's position so we insert at the same slot
        int rvIndex = parent.indexOfChild(rv);
        rv.setVisibility(View.GONE);

        // Create the empty-state label
        TextView tv = new TextView(this);
        tv.setTag("empty_state");
        tv.setText(message);
        tv.setTextColor(getResources().getColor(R.color.pastel_text_secondary, null));
        tv.setTextSize(16f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(48, 96, 48, 96);

        // *** Key fix: copy RV's layoutParams (weight=1) so this fills the same space ***
        tv.setLayoutParams(rv.getLayoutParams());

        // Insert at the RecyclerView's original slot, not at the end of the layout
        parent.addView(tv, rvIndex);
    }

    private void clearEmptyState(RecyclerView rv) {
        if (!(rv.getParent() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) rv.getParent();
        View old = parent.findViewWithTag("empty_state");
        if (old != null) parent.removeView(old);
        rv.setVisibility(View.VISIBLE);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String bearer() {
        String token = sessionStore.getAccessToken();
        return token != null ? "Bearer " + token : null;
    }

    private String formatCount(int n) {
        if (n == 0) return "0";
        if (n >= 1000) return (n / 1000) + "." + ((n % 1000) / 100) + "k";
        return String.valueOf(n);
    }

    private void showFullScreenImage(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            Toast.makeText(this, "No image to display", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Opening full screen image: " + imageUrl);

        // Create a simple overlay view
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setLayoutParams(new android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ));
        overlay.setBackgroundColor(android.graphics.Color.BLACK);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        
        // Create ImageView
        ImageView imageView = new ImageView(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        );
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setPadding(20, 20, 20, 20);
        
        overlay.addView(imageView);
        
        // Add close button
        TextView closeButton = new TextView(this);
        closeButton.setText("✕");
        closeButton.setTextColor(android.graphics.Color.WHITE);
        closeButton.setTextSize(24);
        closeButton.setPadding(20, 20, 20, 20);
        android.widget.FrameLayout.LayoutParams closeParams = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        closeButton.setLayoutParams(closeParams);
        overlay.addView(closeButton);
        
        // Add to current view
        android.view.ViewGroup rootView = (android.view.ViewGroup) findViewById(android.R.id.content);
        rootView.addView(overlay);
        
        // Load image
        Glide.with(this)
            .load(imageUrl)
            .error(android.R.drawable.ic_menu_report_image)
            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                @Override
                public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, 
                                           com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                           boolean isFirstResource) {
                    Log.e(TAG, "Failed to load full screen image: " + imageUrl, e);
                    return false;
                }

                @Override
                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, 
                                              com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                              com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                    Log.d(TAG, "Full screen image loaded successfully: " + imageUrl);
                    return false;
                }
            })
            .into(imageView);
        
        // Click to dismiss
        overlay.setOnClickListener(v -> {
            Log.d(TAG, "Closing full screen image");
            rootView.removeView(overlay);
        });
        
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Close button clicked");
            rootView.removeView(overlay);
        });
    }

    private void setText(View root, int viewId, String text) {
        TextView tv = root.findViewById(viewId);
        if (tv != null) tv.setText(text);
    }

    private void navigateToHome() {
        if (mockIsMonday) {
            renderScreen(R.layout.screen_weekly_wrapup);
        } else {
            renderScreen(R.layout.screen_home);
        }
    }

    private void setupBottomNav(View root) {
        View navHome        = root.findViewById(R.id.navHome);
        View navGallery     = root.findViewById(R.id.navGallery);
        View navLeaderboard = root.findViewById(R.id.navLeaderboard);
        View navUpload      = root.findViewById(R.id.navUpload);
        View navProfile     = root.findViewById(R.id.navProfile);
        // Notification bell (top-right of some screens)
        View btnBell        = root.findViewById(R.id.btnTopNotifications);

        if (navHome        != null) navHome.setOnClickListener(v -> {
            Log.d(TAG, "Home nav clicked");
            backStack.clear();
            renderScreen(R.layout.screen_home);
        });
        if (navGallery     != null) navGallery.setOnClickListener(v -> {
            Log.d(TAG, "Gallery nav clicked");
            renderScreen(R.layout.screen_gallery_new);
        });
        if (navLeaderboard != null) navLeaderboard.setOnClickListener(v -> {
            Log.d(TAG, "Leaderboard nav clicked");
            renderScreen(R.layout.screen_leaderboard);
        });
        if (navUpload      != null) navUpload.setOnClickListener(v -> {
            Log.d(TAG, "Upload nav clicked");
            renderScreen(R.layout.screen_submission);
        });
        if (navProfile     != null) navProfile.setOnClickListener(v -> {
            Log.d(TAG, "Profile nav clicked — own profile");
            currentViewedUserId = -1; // always own profile from nav
            renderScreen(R.layout.screen_profile);
        });
        if (btnBell        != null) btnBell.setOnClickListener(v -> {
            Log.d(TAG, "Notification bell clicked");
            renderScreen(R.layout.screen_social_hub);
        });
    }

    private void setClick(View root, int viewId, int layoutRes) {
        View v = root.findViewById(viewId);
        if (v != null) v.setOnClickListener(click -> renderScreen(layoutRes));
    }

    private void setClickWithAction(View root, int viewId, Runnable action) {
        View v = root.findViewById(viewId);
        if (v != null) v.setOnClickListener(click -> action.run());
    }

    // ─── Backend Discovery ─────────────────────────────────────────────────────

    private void initializeBackendCandidates() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add(normalizeBaseUrl(getString(R.string.backend_base_url)));
        unique.add(normalizeBaseUrl("http://10.0.2.2:8080/"));
        unique.add(normalizeBaseUrl("http://localhost:8080/"));
        unique.add(normalizeBaseUrl("http://127.0.0.1:8080/"));
        unique.add(normalizeBaseUrl("http://192.168.137.1:8080/"));
        backendCandidates.clear();
        backendCandidates.addAll(unique);
    }

    private String normalizeBaseUrl(String url) {
        if (url == null) return "http://10.0.2.2:8080/";
        String normalized = url.trim();
        if (!normalized.endsWith("/")) normalized += "/";
        return normalized;
    }

    private void resolveBackendAndContinue(Runnable onReady) {
        if (apiService != null && activeBackendBaseUrl != null) { onReady.run(); return; }
        tryBackendCandidate(0, onReady);
    }

    private void tryBackendCandidate(int index, Runnable onReady) {
        if (index >= backendCandidates.size()) {
            Log.w(TAG, "No backend found, using mock data for testing");
            apiService = null;
            activeBackendBaseUrl = null;
            Toast.makeText(this,
                    "No backend found. Using mock data for testing. Start server for full functionality.",
                    Toast.LENGTH_LONG).show();
            // Still proceed with the app using mock data
            onReady.run();
            return;
        }
        String candidateBaseUrl = backendCandidates.get(index);
        ApiService candidateService = ApiClient.createApiService(candidateBaseUrl);
        candidateService.getHealth().enqueue(new Callback<HealthResponse>() {
            @Override
            public void onResponse(@NonNull Call<HealthResponse> call, @NonNull Response<HealthResponse> response) {
                HealthResponse body = response.body();
                if (response.isSuccessful() && body != null && body.ok) {
                    Log.d(TAG, "Backend found at: " + candidateBaseUrl);
                    apiService = candidateService;
                    activeBackendBaseUrl = candidateBaseUrl;
                    onReady.run();
                } else {
                    tryBackendCandidate(index + 1, onReady);
                }
            }
            @Override
            public void onFailure(@NonNull Call<HealthResponse> call, @NonNull Throwable throwable) {
                Log.d(TAG, "Backend candidate failed: " + candidateBaseUrl + " - " + throwable.getMessage());
                tryBackendCandidate(index + 1, onReady);
            }
        });
    }

    // ─── Auth Flow ─────────────────────────────────────────────────────────────

    private void startGoogleSignInFlow() {
        if (apiService == null) {
            resolveBackendAndContinue(() -> googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
            return;
        }
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null || idToken.isEmpty() || getString(R.string.google_web_client_id).startsWith("replace_")) {
            Toast.makeText(this, "Set valid Google web client ID first.", Toast.LENGTH_LONG).show();
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Firebase auth with Google failed", task.getException());
                Toast.makeText(this, "Firebase auth failed: " + safeMessage(task.getException()), Toast.LENGTH_LONG).show();
                return;
            }
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user == null) { Toast.makeText(this, "Missing Firebase user.", Toast.LENGTH_SHORT).show(); return; }
            user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                    Toast.makeText(this, "Could not get Firebase token: " + safeMessage(tokenTask.getException()), Toast.LENGTH_LONG).show();
                    return;
                }
                exchangeTokenWithBackend(tokenTask.getResult().getToken());
            });
        });
    }

    private void exchangeTokenWithBackend(String firebaseIdToken) {
        if (apiService == null) { resolveBackendAndContinue(() -> exchangeTokenWithBackend(firebaseIdToken)); return; }
        apiService.authenticateGoogle(new AuthGoogleRequest(firebaseIdToken))
                .enqueue(new Callback<AuthGoogleResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthGoogleResponse> call, @NonNull Response<AuthGoogleResponse> response) {
                        if (!response.isSuccessful() || response.body() == null || response.body().accessToken == null) {
                            Toast.makeText(MainActivity.this, "Backend login failed (HTTP " + response.code() + ").", Toast.LENGTH_LONG).show();
                            return;
                        }
                        sessionStore.saveAccessToken(response.body().accessToken);
                        // *** Save user data to session ***
                        if (response.body().user != null) {
                            sessionStore.saveUser(response.body().user);
                        }
                        validateSessionAndEnterApp();
                    }
                    @Override
                    public void onFailure(@NonNull Call<AuthGoogleResponse> call, @NonNull Throwable throwable) {
                        Log.e(TAG, "Backend /auth call failed", throwable);
                        Toast.makeText(MainActivity.this, explainNetworkFailure(throwable), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void validateSessionAndEnterApp() {
        String token = sessionStore.getAccessToken();
        if (token == null) { renderScreen(R.layout.screen_auth_signup); return; }
        if (apiService == null) { resolveBackendAndContinue(this::validateSessionAndEnterApp); return; }

        apiService.getMe("Bearer " + token).enqueue(new Callback<MeResponse>() {
            @Override
            public void onResponse(@NonNull Call<MeResponse> call, @NonNull Response<MeResponse> response) {
                if (response.isSuccessful()) {
                    if (currentViewedUserId != -1) {
                        renderScreen(R.layout.screen_profile);
                    } else {
                        navigateToHome();
                    }
                } else {
                    sessionStore.clear();
                    renderScreen(R.layout.screen_auth_signup);
                }
            }
            @Override
            public void onFailure(@NonNull Call<MeResponse> call, @NonNull Throwable throwable) {
                Log.e(TAG, "Session validation (/me) failed", throwable);
                Toast.makeText(MainActivity.this,
                        "Could not validate session with backend. Returning to login.\n" + safeMessage(throwable),
                        Toast.LENGTH_LONG).show();
                renderScreen(R.layout.screen_auth_signup);
            }
        });
    }

    private void logout() {
        sessionStore.clear();
        firebaseAuth.signOut();
        googleSignInClient.signOut();
        renderScreen(R.layout.screen_auth_signup);
    }

    // ─── Email Auth ────────────────────────────────────────────────────────────

    private void handleEmailSignup(View root) {
        EditText etEmail    = root.findViewById(R.id.etSignupEmail);
        EditText etPassword = root.findViewById(R.id.etSignupPassword);
        EditText etConfirm  = root.findViewById(R.id.etSignupConfirmPassword);
        if (etEmail == null || etPassword == null || etConfirm == null) {
            Toast.makeText(this, "Signup fields missing.", Toast.LENGTH_SHORT).show(); return;
        }
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirm  = etConfirm.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required.", Toast.LENGTH_SHORT).show(); return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show(); return;
        }
        resolveBackendAndContinue(() -> firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Signup failed: " + friendlyFirebaseMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) { Toast.makeText(this, "User not available.", Toast.LENGTH_SHORT).show(); return; }
                    user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                        if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                            Toast.makeText(this, "Could not get Firebase token: " + safeMessage(tokenTask.getException()), Toast.LENGTH_LONG).show();
                            return;
                        }
                        exchangeTokenWithBackend(tokenTask.getResult().getToken());
                    });
                }));
    }

    private void handleEmailLogin(View root) {
        EditText etEmail    = root.findViewById(R.id.etLoginEmail);
        EditText etPassword = root.findViewById(R.id.etLoginPassword);
        if (etEmail == null || etPassword == null) {
            Toast.makeText(this, "Login fields missing.", Toast.LENGTH_SHORT).show(); return;
        }
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password are required.", Toast.LENGTH_SHORT).show(); return;
        }
        resolveBackendAndContinue(() -> firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Login failed: " + friendlyFirebaseMessage(task.getException()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) { Toast.makeText(this, "User not available.", Toast.LENGTH_SHORT).show(); return; }
                    user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                        if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                            Toast.makeText(this, "Could not get Firebase token: " + safeMessage(tokenTask.getException()), Toast.LENGTH_LONG).show();
                            return;
                        }
                        exchangeTokenWithBackend(tokenTask.getResult().getToken());
                    });
                }));
    }

    // ─── Error Helpers ─────────────────────────────────────────────────────────

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isEmpty()) return "unknown error";
        return throwable.getMessage();
    }

    private String friendlyFirebaseMessage(Exception exception) {
        if (!(exception instanceof FirebaseAuthException)) return safeMessage(exception);
        String code = ((FirebaseAuthException) exception).getErrorCode();
        if ("ERROR_INVALID_EMAIL".equals(code))           return "Invalid email address.";
        if ("ERROR_WEAK_PASSWORD".equals(code))           return "Weak password (min 6 chars).";
        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code))    return "Email already in use.";
        if ("ERROR_USER_NOT_FOUND".equals(code) || "ERROR_WRONG_PASSWORD".equals(code)) return "Wrong email or password.";
        if ("ERROR_OPERATION_NOT_ALLOWED".equals(code))   return "Enable Email/Password in Firebase Auth settings.";
        return code + ": " + safeMessage(exception);
    }

    private String explainNetworkFailure(Throwable throwable) {
        String configuredBaseUrl = activeBackendBaseUrl != null ? activeBackendBaseUrl : getString(R.string.backend_base_url);
        StringBuilder message = new StringBuilder("Server not reachable.\n");
        if (throwable instanceof UnknownHostException || throwable instanceof ConnectException) {
            message.append("Start backend first and confirm it is reachable from Android.\n");
        }
        if (configuredBaseUrl.contains("10.0.2.2") && !isProbablyEmulator()) {
            message.append("You are likely on a physical phone: 10.0.2.2 works only on emulator.\n");
        }
        message.append("Tried backend: ").append(configuredBaseUrl).append("\n");
        message.append(safeMessage(throwable));
        return message.toString();
    }

    private boolean isProbablyEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    // ─── Who Liked ─────────────────────────────────────────────────────────────

    private void showPostLikers(long postId) {
        String token = bearer();
        if (token == null || apiService == null) return;
        apiService.getPostLikers(token, postId).enqueue(new Callback<com.example.chesh.network.models.PostLikersResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.PostLikersResponse> call,
                                   @NonNull Response<com.example.chesh.network.models.PostLikersResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.example.chesh.network.models.UserDto> users = response.body().users;
                    if (users == null || users.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No likes yet", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showLikersDialog(users);
                } else {
                    Toast.makeText(MainActivity.this, "Could not load likes", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.PostLikersResponse> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Could not load likes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLikersDialog(List<com.example.chesh.network.models.UserDto> users) {
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setPadding(0, 16, 0, 16);
        rv.setAdapter(new com.example.chesh.network.adapters.PostLikersAdapter(
                this, users,
                user -> openDirectConversation(user.id),
                user -> openUserProfile(user.id)
        ));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("❤️ Liked by")
                .setView(rv)
                .setNegativeButton("Close", null)
                .show();
    }

    // ─── Direct Conversation ───────────────────────────────────────────────────

    private void openDirectConversation(long targetUserId) {
        String token = bearer();
        if (token == null || apiService == null) {
            Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show();
            return;
        }
        String json;
        try { JSONObject p = new JSONObject(); p.put("targetUserId", targetUserId); json = p.toString(); }
        catch (JSONException e) { return; }
        okhttp3.RequestBody body = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json; charset=utf-8"));
        apiService.createDirectConversation(token, body).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String raw = response.body().string();
                        JSONObject jObj = new JSONObject(raw);
                        currentConversationId = jObj.getLong("conversationId");
                        renderScreen(R.layout.screen_chat);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not open chat", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Could not start conversation", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Could not start conversation", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── User Profile Navigation ───────────────────────────────────────────────

    private void openUserProfile(long userId) {
        if (sessionStore.getUserId() == userId) {
            currentViewedUserId = -1;
        } else {
            currentViewedUserId = userId;
        }
        renderScreen(R.layout.screen_profile);
    }

    private void followUserFromProfile(TextView followBtn) {
        if (currentViewedUserId == -1 || apiService == null) return;
        String token = bearer();
        if (token == null) return;
        apiService.followUser(token, currentViewedUserId).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override public void onResponse(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    if (followBtn != null) {
                        followBtn.setText("Following ✓");
                        followBtn.setEnabled(false);
                    }
                    Toast.makeText(MainActivity.this, "Following! ✓", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Could not follow (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(@NonNull Call<okhttp3.ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Follow failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Show a preview dialog after the user picks a photo; offer Crop / Use / Re-select. */
    private void showPhotoPreviewDialog(Uri imageUri) {
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(this);
        int previewHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        android.widget.FrameLayout.LayoutParams ivLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, previewHeight);
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(ivLp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(imageUri).into(iv);
        wrapper.addView(iv);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Photo Preview")
                .setView(wrapper)
                .setPositiveButton("✅ Use Photo", (d, w) -> {
                    selectedPhotoUri = imageUri;
                    if (currentScreen == R.layout.screen_submission) renderScreen(R.layout.screen_submission);
                })
                .setNeutralButton("✂ Crop", (d, w) -> launchCropIntent(imageUri))
                .setNegativeButton("🔄 Re-select", (d, w) ->
                        pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()))
                .setCancelable(true)
                .show();
    }

    /** Launch system crop intent for the given URI. */
    private void launchCropIntent(Uri sourceUri) {
        pendingCropUri = sourceUri;
        try {
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(sourceUri, "image/*");
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cropIntent.putExtra("crop", "true");
            cropIntent.putExtra("aspectX", 1);
            cropIntent.putExtra("aspectY", 1);
            cropIntent.putExtra("outputX", 800);
            cropIntent.putExtra("outputY", 800);
            cropIntent.putExtra("return-data", true);
            cropResultLauncher.launch(cropIntent);
        } catch (Exception e) {
            // Crop not supported on this device — just use the photo as-is
            selectedPhotoUri = sourceUri;
            pendingCropUri = null;
            Toast.makeText(this, "Crop not supported on this device; using full photo.", Toast.LENGTH_SHORT).show();
            if (currentScreen == R.layout.screen_submission) renderScreen(R.layout.screen_submission);
        }
    }

    private void loadOtherUserProfileData(View root, long userId) {
        String token = bearer();
        if (token == null || apiService == null) return;

        // Load user stats + info
        apiService.getUserStats(token, userId).enqueue(new Callback<com.example.chesh.network.models.UserStatsResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.example.chesh.network.models.UserStatsResponse> call,
                                   @NonNull Response<com.example.chesh.network.models.UserStatsResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                com.example.chesh.network.models.UserStatsResponse body = response.body();
                if (body.user != null) {
                    String uname = !TextUtils.isEmpty(body.user.name) ? body.user.name
                            : (body.user.email != null ? body.user.email.split("@")[0] : "User");
                    setText(root, R.id.tvProfileUsername, uname);
                    setText(root, R.id.tvProfileName, uname);
                    setText(root, R.id.tvProfileEmail, body.user.email != null ? body.user.email : "");
                    
                    TextView tvBio = root.findViewById(R.id.tvProfileBio);
                    TextView tvPronouns = root.findViewById(R.id.tvProfilePronouns);
                    if (tvBio != null) {
                        tvBio.setText(body.user.bio != null ? body.user.bio : "");
                        tvBio.setVisibility(TextUtils.isEmpty(body.user.bio) ? View.GONE : View.VISIBLE);
                    }
                    if (tvPronouns != null) {
                        tvPronouns.setText(body.user.pronouns != null ? body.user.pronouns : "");
                        tvPronouns.setVisibility(TextUtils.isEmpty(body.user.pronouns) ? View.GONE : View.VISIBLE);
                    }
                    
                    ImageView iv = root.findViewById(R.id.ivProfileAvatar);
                    if (iv != null && !TextUtils.isEmpty(body.user.avatarUrl)) {
                        Glide.with(MainActivity.this).load(body.user.avatarUrl).circleCrop().into(iv);
                    }
                }
                if (body.stats != null) {
                    setText(root, R.id.tvPostsCount, String.valueOf(body.stats.postsCount));
                    setText(root, R.id.tvFollowersCount, formatCount(body.stats.followersCount));
                    setText(root, R.id.tvFollowingCount, formatCount(body.stats.followingCount));
                }
            }
            @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.UserStatsResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to load other user profile", t);
            }
        });

        // Load their posts grid
        RecyclerView rvGrid = root.findViewById(R.id.rvProfilePosts);
        if (rvGrid != null) {
            rvGrid.setLayoutManager(new GridLayoutManager(this, 3));
            apiService.getUserPosts(token, userId).enqueue(new Callback<com.example.chesh.network.models.UserPostsResponse>() {
                @Override
                public void onResponse(@NonNull Call<com.example.chesh.network.models.UserPostsResponse> call,
                                       @NonNull Response<com.example.chesh.network.models.UserPostsResponse> response) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    List<com.example.chesh.network.models.UserPost> posts = response.body().posts;
                    if (posts == null || posts.isEmpty()) {
                        showEmptyState(rvGrid, "No posts yet 📷");
                    } else {
                        clearEmptyState(rvGrid);
                        rvGrid.setAdapter(new com.example.chesh.network.adapters.ProfileGridAdapter(
                                MainActivity.this, posts, (post, position) -> {
                                    currentPostId = post.id;
                                    currentPostMediaUrl = (post.PostMedia != null && !post.PostMedia.isEmpty())
                                            ? post.PostMedia.get(0).mediaUrl : null;
                                    renderScreen(R.layout.screen_post_detail);
                                }));
                    }
                }
                @Override public void onFailure(@NonNull Call<com.example.chesh.network.models.UserPostsResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "Failed to load other user posts", t);
                }
            });
        }
    }
}