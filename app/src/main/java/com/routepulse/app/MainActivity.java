package com.routepulse.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FirebaseAuth mAuth;
    private DatabaseReference usersDataRef;
    private RelativeLayout preloaderView;
    private LinearLayout bottomNavigationView;
    private String currentFragmentTag = "home";
    private String uid;
    // new field for subscription dialog
    private AlertDialog subscriptionDialog;

    private String email;
    private String name;
    private AlertDialog appCloseDialog;
    private AlertDialog reviewDialog;
    private int selectedRating = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fade_in_fast, R.anim.no_animation);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
        } else {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
        }

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        usersDataRef = FirebaseDatabase.getInstance().getReference("users");

        preloaderView = findViewById(R.id.preloader_view);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        Intent intent = getIntent();
        uid = intent.getStringExtra("uid");
        email = intent.getStringExtra("email");

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified() && (uid == null || currentUser.getUid().equals(uid))) {
            Log.d(TAG, "User is authenticated and email verified: " + currentUser.getUid());
            uid = currentUser.getUid();
            email = currentUser.getEmail();
            preloaderView.setVisibility(View.VISIBLE);
            fetchUserDataAndLoadFragment();
        } else {
            Log.d(TAG, "No user is signed in or email not verified or UID mismatch: " +
                    "currentUser=" + (currentUser != null ? currentUser.getUid() : "null") +
                    ", isEmailVerified=" + (currentUser != null ? currentUser.isEmailVerified() : false) +
                    ", intentUid=" + uid);
            navigateToLogin();
            return;
        }

        setupNavigationListeners();
        setupDialogs();
    }

    private void setupDialogs() {
        // Setup App Close Dialog
        AlertDialog.Builder appCloseBuilder = new AlertDialog.Builder(this);
        View appCloseView = getLayoutInflater().inflate(R.layout.layout_appclose, null);
        appCloseBuilder.setView(appCloseView);
        appCloseDialog = appCloseBuilder.create();
        appCloseDialog.setCancelable(false);

        Button btnLogout = appCloseView.findViewById(R.id.btnLogout);
        Button btnCancel = appCloseView.findViewById(R.id.btnCancel);

        btnLogout.setOnClickListener(v -> {
            appCloseDialog.dismiss();
            finish();
        });

        btnCancel.setOnClickListener(v -> appCloseDialog.dismiss());

        // Setup Review Dialog
        AlertDialog.Builder reviewBuilder = new AlertDialog.Builder(this);
        View reviewView = getLayoutInflater().inflate(R.layout.layout_review, null);
        reviewBuilder.setView(reviewView);
        reviewDialog = reviewBuilder.create();
        reviewDialog.setCancelable(false);

        ImageView[] stars = {
                reviewView.findViewById(R.id.star1),
                reviewView.findViewById(R.id.star2),
                reviewView.findViewById(R.id.star3),
                reviewView.findViewById(R.id.star4),
                reviewView.findViewById(R.id.star5)
        };

        for (int i = 0; i < stars.length; i++) {
            final int rating = i + 1;
            stars[i].setOnClickListener(v -> {
                selectedRating = rating;
                updateStarDisplay(stars, rating);
            });
        }

        Button btnSubmit = reviewView.findViewById(R.id.btnSubmit);
        EditText etFeedback = reviewView.findViewById(R.id.etFeedback);

        btnSubmit.setOnClickListener(v -> {
            String feedback = etFeedback.getText().toString();
            // Here you can add code to save the rating and feedback to Firebase
            Log.d(TAG, "Review submitted: Rating=" + selectedRating + ", Feedback=" + feedback);
            reviewDialog.dismiss();
            showAppCloseDialog();
        });
    }

    private void updateStarDisplay(ImageView[] stars, int rating) {
        for (int i = 0; i < stars.length; i++) {
            if (i < rating) {
                stars[i].setImageResource(R.drawable.ic_star_filled);
            } else {
                stars[i].setImageResource(R.drawable.ic_star_outline);
            }
        }
    }

    private void fetchUserDataAndLoadFragment() {
        usersDataRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    name = dataSnapshot.child("name").getValue(String.class);
                    email = dataSnapshot.child("email").getValue(String.class);
                    Log.d(TAG, "Fetched user data: name=" + name + ", email=" + email);
                } else {
                    Log.w(TAG, "No user data found for UID: " + uid);
                    name = "User";
                    email = email != null ? email : "Unknown";
                }
                // Instead of immediately loading home, show subscription dialog first
                showSubscriptionDialog();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Failed to fetch user data: " + databaseError.getMessage());
                name = "User";
                email = email != null ? email : "Unknown";
                // still show subscription dialog even if fetch failed
                showSubscriptionDialog();
            }
        });
    }
    private void showSubscriptionDialog() {
        // prevent duplicates
        if (subscriptionDialog != null && subscriptionDialog.isShowing()) return;

        // hide preloader / fragment container while dialog is visible
        if (preloaderView != null) preloaderView.setVisibility(View.GONE);
        if (findViewById(R.id.fragment_container) != null) findViewById(R.id.fragment_container).setVisibility(View.GONE);
        if (bottomNavigationView != null) bottomNavigationView.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View subscriptionView = getLayoutInflater().inflate(R.layout.subscription, null);
        builder.setView(subscriptionView);
        subscriptionDialog = builder.create();
        subscriptionDialog.setCancelable(false);

        // find action views inside the custom layout
        View btnSubscribe = subscriptionView.findViewById(R.id.btnSubscribe);
        View btnMaybeLater = subscriptionView.findViewById(R.id.btnMaybeLater);

        // Maybe later -> go to next content
        btnMaybeLater.setOnClickListener(v -> {
            try {
                if (subscriptionDialog != null && subscriptionDialog.isShowing()) subscriptionDialog.dismiss();
            } catch (Exception e) { Log.w(TAG, "Failed to dismiss subscriptionDialog", e); }
            // load the normal home fragment / app content
            loadHomeFragment();
        });

        // Subscribe now -> run subscription flow (placeholder) then proceed to app
        btnSubscribe.setOnClickListener(v -> {
            Log.d(TAG, "Subscribe clicked - implement payment/subscription flow here");
            // TODO: launch payment/subscription activity / API. For now proceed to app.
            try {
                if (subscriptionDialog != null && subscriptionDialog.isShowing()) subscriptionDialog.dismiss();
            } catch (Exception e) { Log.w(TAG, "Failed to dismiss subscriptionDialog", e); }
            loadHomeFragment();
        });

        // show the dialog
        subscriptionDialog.show();
    }


    private void loadHomeFragment() {
        preloaderView.setVisibility(View.GONE);
        bottomNavigationView.setVisibility(View.VISIBLE);
        findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment existingFragment = fragmentManager.findFragmentByTag("home");
        Fragment fragment;
        if (existingFragment instanceof HomeFragment) {
            fragment = existingFragment;
            Log.d(TAG, "Reusing existing HomeFragment with tag: home");
        } else {
            fragment = new HomeFragment();
            Bundle bundle = new Bundle();
            bundle.putString("name", name != null ? name : "User");
            bundle.putString("email", email);
            fragment.setArguments(bundle);
            Log.d(TAG, "Creating new HomeFragment");
        }
        loadFragment(fragment, "home");
        currentFragmentTag = "home";
        overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
    }

    private void setupNavigationListeners() {
        LinearLayout navHome = findViewById(R.id.nav_home);
        LinearLayout navAlerts = findViewById(R.id.nav_alerts);
        LinearLayout navProfile = findViewById(R.id.nav_profile);

        navHome.setOnClickListener(v -> selectFragment("home"));
        navAlerts.setOnClickListener(v -> selectFragment("alerts"));
        navProfile.setOnClickListener(v -> selectFragment("profile"));
    }

    private void selectFragment(String tag) {
        if (tag.equals(currentFragmentTag)) {
            Log.d(TAG, "Already on fragment: " + tag + ", skipping");
            return;
        }

        resetNavigationState();

        int activeColor = ContextCompat.getColor(this, R.color.nav_active);
        int inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive);

        LinearLayout selectedNav;
        ImageView selectedIcon;
        TextView selectedText;
        Fragment fragment;

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment existingFragment = fragmentManager.findFragmentByTag(tag);

        switch (tag) {
            case "home":
                selectedNav = findViewById(R.id.nav_home);
                selectedIcon = findViewById(R.id.nav_home_icon);
                selectedText = findViewById(R.id.nav_home_text);
                if (existingFragment instanceof HomeFragment) {
                    fragment = existingFragment;
                    Log.d(TAG, "Reusing existing HomeFragment with tag: " + tag);
                } else {
                    fragment = new HomeFragment();
                    Bundle homeBundle = new Bundle();
                    homeBundle.putString("name", name != null ? name : "User");
                    homeBundle.putString("email", email);
                    fragment.setArguments(homeBundle);
                    Log.d(TAG, "Creating new HomeFragment");
                }
                break;
            case "alerts":
                selectedNav = findViewById(R.id.nav_alerts);
                selectedIcon = findViewById(R.id.nav_alerts_icon);
                selectedText = findViewById(R.id.nav_alerts_text);
                if (existingFragment instanceof AlertsFragment) {
                    fragment = existingFragment;
                    Log.d(TAG, "Reusing existing AlertsFragment with tag: " + tag);
                } else {
                    fragment = new AlertsFragment();
                    Log.d(TAG, "Creating new AlertsFragment");
                }
                break;
            case "profile":
                selectedNav = findViewById(R.id.nav_profile);
                selectedIcon = findViewById(R.id.nav_profile_icon);
                selectedText = findViewById(R.id.nav_profile_text);
                if (existingFragment instanceof ProfileFragment) {
                    fragment = existingFragment;
                    Log.d(TAG, "Reusing existing ProfileFragment with tag: " + tag);
                } else {
                    fragment = new ProfileFragment();
                    Bundle profileBundle = new Bundle();
                    profileBundle.putString("name", name != null ? name : "User");
                    profileBundle.putString("email", email);
                    fragment.setArguments(profileBundle);
                    Log.d(TAG, "Creating new ProfileFragment");
                }
                break;
            default:
                Log.w(TAG, "Unknown fragment tag: " + tag);
                return;
        }

        selectedIcon.setColorFilter(activeColor);
        selectedText.setTextColor(activeColor);

        loadFragment(fragment, tag);
        currentFragmentTag = tag;
        Log.d(TAG, "Switched to fragment: " + tag);
    }

    private void resetNavigationState() {
        int inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive);
        int[] navIds = {R.id.nav_home, R.id.nav_alerts, R.id.nav_profile};
        int[] iconIds = {R.id.nav_home_icon, R.id.nav_alerts_icon, R.id.nav_profile_icon};
        int[] textIds = {R.id.nav_home_text, R.id.nav_alerts_text, R.id.nav_profile_text};

        for (int i = 0; i < navIds.length; i++) {
            ImageView icon = findViewById(iconIds[i]);
            TextView text = findViewById(textIds[i]);
            if (icon != null && text != null) {
                icon.setColorFilter(inactiveColor);
                text.setTextColor(inactiveColor);
            }
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        Log.d(TAG, "Navigating to LoginActivity");
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

    private void loadFragment(Fragment fragment, String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(R.anim.fade_in_fast, R.anim.fade_out_fast);

        for (Fragment existingFragment : fragmentManager.getFragments()) {
            if (existingFragment != null && !existingFragment.equals(fragment)) {
                transaction.hide(existingFragment);
                Log.d(TAG, "Hiding fragment: " + existingFragment.getTag());
            }
        }

        if (fragment.isAdded()) {
            transaction.show(fragment);
            Log.d(TAG, "Showing existing fragment: " + tag);
        } else {
            transaction.add(R.id.fragment_container, fragment, tag);
            Log.d(TAG, "Adding new fragment: " + tag);
        }

        transaction.commit();
    }

    private void showAppCloseDialog() {
        if (appCloseDialog != null && !appCloseDialog.isShowing()) {
            appCloseDialog.show();
        }
    }

    @Override
    public void onBackPressed() {
        Random random = new Random();
        if (random.nextInt(100) < 30) { // 30% chance to show review dialog
            if (reviewDialog != null && !reviewDialog.isShowing()) {
                selectedRating = 0; // Reset rating
                reviewDialog.show();
            }
        } else {
            showAppCloseDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appCloseDialog != null) {
            appCloseDialog.dismiss();
        }
        if (reviewDialog != null) {
            reviewDialog.dismiss();
        }
        if (subscriptionDialog != null) {
            subscriptionDialog.dismiss();
        }
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

}