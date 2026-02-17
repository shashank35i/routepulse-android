package com.routepulse.app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private Handler handler;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private GoogleSignInClient googleSignInClient;
    private ProgressDialog progressDialog;
    private LayoutInflater inflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // Set light status bar for black icons
        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        handler = new Handler(Looper.getMainLooper());
        inflater = getLayoutInflater();

        // Initialize preloader
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);

        initializeViews();
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.emailInput);
        etPassword = findViewById(R.id.passwordInput);
        btnLogin = findViewById(R.id.loginButton);
        Button btnGoogle = findViewById(R.id.googleLogin);
        Button btnApple = findViewById(R.id.appleLogin);
        TextView tvForgotPassword = findViewById(R.id.forgotPassword);
        TextView tvSignUpLink = findViewById(R.id.signUpLink);

        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etEmail.setError(null);
            }
        });
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etPassword.setError(null);
            }
        });

        btnLogin.setEnabled(true);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        showProgressDialog();
                        Intent data = result.getData();
                        try {
                            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
                            if (account != null) {
                                Log.d(TAG, "Google Sign-In successful, ID token: " + account.getIdToken());
                                firebaseAuthWithGoogle(account.getIdToken());
                            } else {
                                hideProgressDialog();
                                showCustomToast("Google Sign-In failed: No account found");
                                Log.e(TAG, "Google Sign-In failed: No account found");
                            }
                        } catch (ApiException e) {
                            hideProgressDialog();
                            showCustomToast("Google Sign-In failed: StatusCode=" + e.getStatusCode());
                            Log.e(TAG, "Google Sign-In ApiException: StatusCode=" + e.getStatusCode() + ", Message=" + e.getMessage(), e);
                        }
                    } else {
                        hideProgressDialog();
                        showCustomToast("Google Sign-In cancelled");
                        Log.w(TAG, "Google Sign-In cancelled, resultCode: " + result.getResultCode());
                    }
                });

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            etEmail.setError(null);
            etPassword.setError(null);

            boolean hasError = false;
            if (email.isEmpty()) {
                etEmail.setError("Please enter email");
                hasError = true;
            }
            if (password.isEmpty()) {
                etPassword.setError("Please enter password");
                hasError = true;
            }
            if (hasError) {
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showCustomToast("Please enter a valid email address");
                return;
            }
            if (password.length() < 6) {
                showCustomToast("Password must be at least 6 characters long");
                return;
            }

            showProgressDialog();
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(signInTask -> {
                        hideProgressDialog();
                        if (signInTask.isSuccessful()) {
                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser != null) {
                                if (currentUser.isEmailVerified()) {
                                    Log.d(TAG, "signInWithEmailAndPassword successful, email verified");
                                    navigateToMain(currentUser.getEmail(), currentUser.getUid());
                                } else {
                                    Log.d(TAG, "signInWithEmailAndPassword successful, email not verified");
                                    showCustomToast("Please verify your email to log in");
                                    mAuth.signOut();
                                }
                            } else {
                                showCustomToast("Authentication failed: No user found");
                                Log.e(TAG, "Authentication failed: No user found");
                            }
                        } else {
                            Exception e = signInTask.getException();
                            if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                showCustomToast("Invalid email or password");
                                Log.e(TAG, "Invalid credentials", e);
                            } else if (e instanceof FirebaseAuthInvalidUserException) {
                                showCustomToast("Email not registered");
                                Log.e(TAG, "Email not registered", e);
                            } else {
                                showCustomToast("Authentication failed: " + e.getMessage());
                                Log.e(TAG, "Authentication failed", e);
                            }
                        }
                    });
        });

        btnGoogle.setOnClickListener(v -> {
            Log.d(TAG, "Google Sign-In button clicked");
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        btnApple.setOnClickListener(v -> {
            showCustomToast("Apple Sign-In is not implemented yet");
            Log.d(TAG, "Apple Sign-In button clicked (not implemented)");
        });

        tvForgotPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                showCustomToast("Please enter your email address");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showCustomToast("Please enter a valid email address");
                return;
            }
            showProgressDialog();
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        hideProgressDialog();
                        if (task.isSuccessful()) {
                            showCustomToast("Password reset email sent");
                            Log.d(TAG, "Password reset email sent to: " + email);
                        } else {
                            showCustomToast("Failed to send password reset email: " + task.getException().getMessage());
                            Log.e(TAG, "Failed to send password reset email", task.getException());
                        }
                    });
        });

        tvSignUpLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
            startActivity(intent);
            Log.d(TAG, "Navigating to SignUpActivity");
        });
    }

    private void showProgressDialog() {
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void hideProgressDialog() {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    hideProgressDialog();
                    if (task.isSuccessful()) {
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        if (currentUser != null) {
                            Log.d(TAG, "Firebase auth with Google successful");
                            navigateToMain(currentUser.getEmail(), currentUser.getUid());
                        } else {
                            showCustomToast("Google Sign-In failed: No user found");
                            Log.e(TAG, "Google Sign-In failed: No user found");
                        }
                    } else {
                        showCustomToast("Google Sign-In failed: " + task.getException().getMessage());
                        Log.e(TAG, "Firebase auth with Google failed", task.getException());
                    }
                });
    }

    private void navigateToMain(String email, String uid) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("uid", uid);
        startActivity(intent);
        Log.d(TAG, "Navigating to MainActivity with email: " + email + ", UID: " + uid);
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

    private void showCustomToast(String message) {
        View toastView = inflater.inflate(R.layout.custom_toast, null);
        TextView textView = toastView.findViewById(R.id.toast_text);
        textView.setText(message);
        View progressLine = toastView.findViewById(R.id.progress_line);
        progressLine.setScaleX(1f);
        progressLine.setVisibility(View.VISIBLE);

        Toast toast = new Toast(this);
        toast.setGravity(Gravity.BOTTOM | Gravity.END, 16, 50);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);

        toastView.clearAnimation();
        progressLine.clearAnimation();

        AnimationSet animationSet = new AnimationSet(true);
        animationSet.setInterpolator(new LinearInterpolator());

        TranslateAnimation openAnim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 1f,
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0f);
        openAnim.setDuration(100);
        animationSet.addAnimation(openAnim);

        ScaleAnimation progressAnim = new ScaleAnimation(
                1f, 0f, 1f, 1f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        progressAnim.setDuration(1500);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                progressLine.setVisibility(View.GONE);
                toastView.setVisibility(View.GONE);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        progressLine.startAnimation(progressAnim);

        toastView.startAnimation(animationSet);

        handler.postDelayed(() -> {
            toast.cancel();
            progressLine.clearAnimation();
            toastView.clearAnimation();
        }, 1600);

        toast.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }
}