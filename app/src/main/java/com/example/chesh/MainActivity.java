package com.example.chesh;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private FrameLayout screenContainer;
    private int currentScreen = R.layout.screen_auth_signup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        screenContainer = findViewById(R.id.screenContainer);
        renderScreen(currentScreen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void renderScreen(int layoutRes) {
        currentScreen = layoutRes;
        screenContainer.removeAllViews();
        View screenView = LayoutInflater.from(this).inflate(layoutRes, screenContainer, true);
        wireCurrentScreen(screenView);
    }

    private void wireCurrentScreen(View root) {
        setClick(root, R.id.btnToLogin, R.layout.screen_auth_login);
        setClick(root, R.id.btnGoFromSignup, R.layout.screen_home);
        setClick(root, R.id.btnBackToSignup, R.layout.screen_auth_signup);
        setClick(root, R.id.btnLoginToSignup, R.layout.screen_auth_signup);
        setClick(root, R.id.btnLoginGoHome, R.layout.screen_home);
        setClick(root, R.id.btnHomePrevChallenges, R.layout.screen_previous_challenges);
        setClick(root, R.id.btnHomeSubmitEntry, R.layout.screen_submission);
        setClick(root, R.id.btnSubmissionLeaderboard, R.layout.screen_leaderboard);
        setClick(root, R.id.tabNew, R.layout.screen_gallery_new);
        setClick(root, R.id.tabTrending, R.layout.screen_gallery_trending);
        setClick(root, R.id.tabTop, R.layout.screen_gallery_top);
        setClick(root, R.id.btnSaveEditProfile, R.layout.screen_profile);

        View navHome = root.findViewById(R.id.navHome);
        View navGallery = root.findViewById(R.id.navGallery);
        View navPlus = root.findViewById(R.id.navPlus);
        View navTrophy = root.findViewById(R.id.navTrophy);
        View navProfile = root.findViewById(R.id.navProfile);

        if (navHome != null) navHome.setOnClickListener(v -> renderScreen(R.layout.screen_home));
        if (navGallery != null) navGallery.setOnClickListener(v -> renderScreen(R.layout.screen_gallery_new));
        if (navPlus != null) navPlus.setOnClickListener(v -> renderScreen(R.layout.screen_submission));
        if (navTrophy != null) {
            navTrophy.setOnClickListener(v -> {
                if (currentScreen == R.layout.screen_leaderboard) {
                    renderScreen(R.layout.screen_notifications);
                } else {
                    renderScreen(R.layout.screen_leaderboard);
                }
            });
        }
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> {
                if (currentScreen == R.layout.screen_profile) {
                    renderScreen(R.layout.screen_messages);
                } else if (currentScreen == R.layout.screen_messages) {
                    renderScreen(R.layout.screen_edit_profile);
                } else {
                    renderScreen(R.layout.screen_profile);
                }
            });
        }
    }

    private void setClick(View root, int viewId, int layoutRes) {
        View v = root.findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(click -> renderScreen(layoutRes));
        }
    }
}