package com.routepulse.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private OnboardingAdapter adapter;
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // Set light status bar for black icons
        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();

        // Check if onboarding is already completed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
        if (onboardingCompleted) {
            navigateBasedOnAuthStatus();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        setupViewPager();
    }

    private void setupViewPager() {
        List<OnboardingFragment> fragments = new ArrayList<>();
        fragments.add(OnboardingFragment.newInstance(1));
        fragments.add(OnboardingFragment.newInstance(2));
        fragments.add(OnboardingFragment.newInstance(3));

        adapter = new OnboardingAdapter(this, fragments);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false); // Disable manual scrolling

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // Update dots in all fragments
                for (OnboardingFragment fragment : fragments) {
                    fragment.updateDots(position);
                }
            }
        });
    }

    public void navigateToNextPage() {
        int currentItem = viewPager.getCurrentItem();
        if (currentItem < adapter.getItemCount() - 1) {
            viewPager.setCurrentItem(currentItem + 1, true);
        }
    }

    public void skipToLastPage() {
        viewPager.setCurrentItem(adapter.getItemCount() - 1, true);
    }

    public void finishOnboarding() {
        // Mark onboarding as completed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();

        // Navigate to MainActivity
        navigateBasedOnAuthStatus();
    }

    private void navigateBasedOnAuthStatus() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Intent intent;
        if (currentUser != null && currentUser.isEmailVerified()) {
            intent = new Intent(this, MainActivity.class);
            intent.putExtra("email", currentUser.getEmail());
            intent.putExtra("uid", currentUser.getUid());
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

    public ViewPager2 getViewPager() {
        return viewPager;
    }
}