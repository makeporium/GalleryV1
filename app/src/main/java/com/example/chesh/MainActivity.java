package com.example.chesh;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private FrameLayout screenContainer;
    private int currentScreen = R.layout.screen_auth_signup;

    private ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMedia;
    private Uri selectedPhotoUri = null;

    // Mock State Machine Variables
    private boolean mockIsFirstTime = true;
    private boolean mockIsMonday = false;
    private int mockStrikeLevel = 0; // 0: Good, 1: Shadowban/Warning, 2: Block/Warning, 3: Permaban

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        screenContainer = findViewById(R.id.screenContainer);
        
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedPhotoUri = uri;
                if (currentScreen == R.layout.screen_submission) {
                    renderScreen(R.layout.screen_submission);
                }
            }
        });

        renderScreen(currentScreen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void renderScreen(int layoutRes) {
        // Global Interceptor for Strike 3
        if (mockStrikeLevel == 3 && layoutRes != R.layout.screen_banned && layoutRes != R.layout.screen_debug && layoutRes != R.layout.screen_auth_signup && layoutRes != R.layout.screen_auth_login) {
            layoutRes = R.layout.screen_banned;
        }

        currentScreen = layoutRes;
        screenContainer.removeAllViews();
        View screenView = LayoutInflater.from(this).inflate(layoutRes, screenContainer, true);
        wireCurrentScreen(screenView, layoutRes);
    }

    private void wireCurrentScreen(View root, int layoutRes) {
        // --- Auth Screens ---
        if (layoutRes == R.layout.screen_auth_signup) {
            setClickWithAction(root, R.id.btnGoFromSignup, () -> {
                if (mockIsFirstTime) {
                    renderScreen(R.layout.screen_tutorial);
                } else {
                    navigateToHome();
                }
            });
            setClick(root, R.id.btnToLogin, R.layout.screen_auth_login);
        } else if (layoutRes == R.layout.screen_auth_login) {
            setClickWithAction(root, R.id.btnLoginGoHome, this::navigateToHome);
            setClick(root, R.id.btnLoginToSignup, R.layout.screen_auth_signup);
            setClick(root, R.id.btnBackToSignup, R.layout.screen_auth_signup);
        }

        // --- Tutorial Screen ---
        else if (layoutRes == R.layout.screen_tutorial) {
            setClickWithAction(root, R.id.btnFinishTutorial, () -> {
                mockIsFirstTime = false;
                navigateToHome();
            });
        }

        // --- Weekly Wrap-up Screen ---
        else if (layoutRes == R.layout.screen_weekly_wrapup) {
            setClickWithAction(root, R.id.btnWrapupToGallery, () -> {
                mockIsMonday = false; // clear it so we don't loop
                renderScreen(R.layout.screen_home);
            });
        }

        // --- Banned Screen ---
        else if (layoutRes == R.layout.screen_banned) {
            setClickWithAction(root, R.id.btnBannedLogout, () -> renderScreen(R.layout.screen_auth_signup));
        }

        // --- Debug Screen ---
        else if (layoutRes == R.layout.screen_debug) {
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
            setClickWithAction(root, R.id.btnDebugClearStrikes, () -> {
                mockStrikeLevel = 0;
            });
            setClickWithAction(root, R.id.btnDebugBack, () -> renderScreen(R.layout.screen_home));
        }

        // --- Submission & Reward ---
        else if (layoutRes == R.layout.screen_submission) {
            Runnable submitAction = () -> {
                if (mockStrikeLevel == 2) {
                    renderScreen(R.layout.screen_moderation_appeal); // Block upload
                } else {
                    renderScreen(R.layout.screen_interstitial_reward);
                }
            };
            setClickWithAction(root, R.id.btnSubmitPostTop, submitAction);
            setClick(root, R.id.btnSubmissionBack, R.layout.screen_home);
            
            View btnSelectPhoto = root.findViewById(R.id.btnSelectPhoto);
            if (btnSelectPhoto != null) {
                btnSelectPhoto.setOnClickListener(v -> {
                    pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                });
            }
            ImageView ivSelectedPhoto = root.findViewById(R.id.ivSelectedPhoto);
            if (ivSelectedPhoto != null && selectedPhotoUri != null) {
                ivSelectedPhoto.setImageURI(selectedPhotoUri);
                View tvSelectPhotoHint = root.findViewById(R.id.tvSelectPhotoHint);
                if (tvSelectPhotoHint != null) {
                    tvSelectPhotoHint.setVisibility(View.GONE);
                }
            }
        } else if (layoutRes == R.layout.screen_interstitial_reward) {
            setClick(root, R.id.btnReturnToGallery, R.layout.screen_home);
        }

        // --- Settings Screen ---
        else if (layoutRes == R.layout.screen_settings) {
            setClick(root, R.id.btnSettingsBack, R.layout.screen_profile);
            setClick(root, R.id.btnSettingsDebug, R.layout.screen_debug);
            setClick(root, R.id.btnSettingsLogout, R.layout.screen_auth_signup);
            setClickWithAction(root, R.id.btnSettingsEditProfile, () -> Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show());
            setClickWithAction(root, R.id.btnSettingsNotifications, () -> Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show());
        }
        
        // --- Moderation Appeal Screen ---
        else if (layoutRes == R.layout.screen_moderation_appeal) {
            setClick(root, R.id.btnAppealStrike, R.layout.screen_home);
            setClick(root, R.id.btnAcceptStrike, R.layout.screen_home);
        }

        // --- Main Tabs Wiring ---
        else {
            // General clicks for components on other screens
            setClickWithAction(root, R.id.btnHomeDebug, () -> renderScreen(R.layout.screen_debug));
            setClickWithAction(root, R.id.btnProfileSettings, () -> renderScreen(R.layout.screen_settings));
            setClick(root, R.id.btnHomeSubmitEntry, R.layout.screen_submission);
            
            // Profile Screen Specifics that fall here
            setClickWithAction(root, R.id.btnProfileEdit, () -> Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show());
            setClickWithAction(root, R.id.btnProfileShare, () -> Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show());
        }

        // Setup Bottom Nav everywhere it exists
        setupBottomNav(root);
    }

    private void navigateToHome() {
        if (mockIsMonday) {
            renderScreen(R.layout.screen_weekly_wrapup);
        } else {
            renderScreen(R.layout.screen_home);
        }
    }

    private void setupBottomNav(View root) {
        View navGallery = root.findViewById(R.id.navGallery);
        View navLeaderboard = root.findViewById(R.id.navLeaderboard);
        View navUpload = root.findViewById(R.id.navUpload);
        View navSocial = root.findViewById(R.id.navSocial);
        View navProfile = root.findViewById(R.id.navProfile);

        if (navGallery != null) navGallery.setOnClickListener(v -> renderScreen(R.layout.screen_home));
        if (navLeaderboard != null) navLeaderboard.setOnClickListener(v -> renderScreen(R.layout.screen_leaderboard));
        if (navUpload != null) navUpload.setOnClickListener(v -> renderScreen(R.layout.screen_submission));
        if (navSocial != null) navSocial.setOnClickListener(v -> renderScreen(R.layout.screen_social_hub));
        if (navProfile != null) navProfile.setOnClickListener(v -> renderScreen(R.layout.screen_profile));
    }

    private void setClick(View root, int viewId, int layoutRes) {
        View v = root.findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(click -> renderScreen(layoutRes));
        }
    }

    private void setClickWithAction(View root, int viewId, Runnable action) {
        View v = root.findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(click -> action.run());
        }
    }
}