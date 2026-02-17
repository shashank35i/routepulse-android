package com.routepulse.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String PREFS_NAME = "RoutePulsePrefs";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PROFILE_IMAGE = "profileImage";
    private static final long CACHE_VALIDITY_MS = 5 * 60 * 1000; // 5 minutes

    private ShapeableImageView profileImageView;
    private TextView nameTextView, emailTextView;
    private LinearLayout logout_layout;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FirebaseUser currentUser;
    private ActivityResultLauncher<Intent> photoPickerLauncher;
    private boolean isImageUploading = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isAdded() || getActivity() == null) {
            Log.w(TAG, "Fragment not attached, skipping initialization");
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            Toast.makeText(getActivity(), "Please log in to access profile", Toast.LENGTH_SHORT).show();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance("https://routepulse-2a17d-default-rtdb.firebaseio.com/")
                .getReference("users");
        prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!isAdded() || getActivity() == null) {
                        Log.w(TAG, "Fragment not attached during image result");
                        return;
                    }
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageToDatabase(imageUri);
                        } else {
                            Bitmap bitmap = (Bitmap) result.getData().getExtras().get("data");
                            if (bitmap != null) {
                                uploadImageFromBitmap(bitmap);
                            } else {
                                Log.e(TAG, "Camera returned null bitmap");
                                Toast.makeText(getActivity(), "Failed to capture image", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else {
                        Log.w(TAG, "Image picker result not OK: " + result.getResultCode());
                        Toast.makeText(getActivity(), "No image selected", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!isAdded() || getActivity() == null) {
            Log.w(TAG, "Fragment not attached or activity is null, skipping view creation");
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        Log.d(TAG, "Inflated layout: " + R.layout.fragment_profile);

        // Initialize views
        profileImageView = view.findViewById(R.id.profile_image);
        nameTextView = view.findViewById(R.id.name_text);
        emailTextView = view.findViewById(R.id.email_text);
        logout_layout = view.findViewById(R.id.logout_layout);
        View editProfileSection = view.findViewById(R.id.edit_profile_layout);
        View changePasswordSection = view.findViewById(R.id.change_password_layout);
        View languageSettingsSection = view.findViewById(R.id.language_settings_layout);
        View notificationPrefsSection = view.findViewById(R.id.notification_prefs_layout);
        View locationServicesSection = view.findViewById(R.id.location_services_layout);
        View dataUsageSection = view.findViewById(R.id.data_usage_layout);
        View privacySettingsSection = view.findViewById(R.id.privacy_settings_layout);
        Switch trafficAlertsSwitch = view.findViewById(R.id.traffic_alerts_switch);
        Switch petrolStationSwitch = view.findViewById(R.id.petrol_station_switch);
        Switch emergencySwitch = view.findViewById(R.id.emergency_notifications_switch);
        Switch soundAlertsSwitch = view.findViewById(R.id.sound_alerts_switch);

        // Verify view initialization
        if (profileImageView == null || nameTextView == null || emailTextView == null) {
            Log.e(TAG, "One or more views (profileImageView, nameTextView, emailTextView) not found in layout");
            Toast.makeText(getActivity(), "Profile view initialization failed", Toast.LENGTH_SHORT).show();
            return view;
        }

        // Load cached data and profile data
        loadCachedData();
        loadProfileData();

        // Set click listeners
        profileImageView.setOnClickListener(v -> openPhotoPicker());
        if (editProfileSection != null) {
            editProfileSection.setOnClickListener(v -> showEditProfileDialog());
        } else {
            Log.w(TAG, "edit_profile_layout not found");
        }
        if (changePasswordSection != null) {
            changePasswordSection.setOnClickListener(v -> showChangePasswordDialog());
        } else {
            Log.w(TAG, "change_password_layout not found");
        }
        if (languageSettingsSection != null) {
            languageSettingsSection.setOnClickListener(v -> showLanguageSettingsDialog());
        } else {
            Log.w(TAG, "language_settings_layout not found");
        }
        if (notificationPrefsSection != null) {
            notificationPrefsSection.setOnClickListener(v -> showNotificationPrefsDialog());
        } else {
            Log.w(TAG, "notification_prefs_layout not found");
        }
        if (locationServicesSection != null) {
            locationServicesSection.setOnClickListener(v -> showLocationServicesDialog());
        } else {
            Log.w(TAG, "location_services_layout not found");
        }
        if (dataUsageSection != null) {
            dataUsageSection.setOnClickListener(v -> showDataUsageDialog());
        } else {
            Log.w(TAG, "data_usage_layout not found");
        }
        if (privacySettingsSection != null) {
            privacySettingsSection.setOnClickListener(v -> showPrivacySettingsDialog());
        } else {
            Log.w(TAG, "privacy_settings_layout not found");
        }
        if (logout_layout != null) {
            logout_layout.setOnClickListener(v -> logout());
        } else {
            Log.e(TAG, "logout_layout not found in fragment_profile.xml");
            Toast.makeText(getActivity(), "Logout feature unavailable", Toast.LENGTH_SHORT).show();
        }

        // Set switch states
        if (trafficAlertsSwitch != null) {
            trafficAlertsSwitch.setChecked(prefs.getBoolean("trafficAlerts", true));
            trafficAlertsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    updateUserSetting("trafficAlerts", isChecked));
        }
        if (petrolStationSwitch != null) {
            petrolStationSwitch.setChecked(prefs.getBoolean("petrolStationNotifications", true));
            petrolStationSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    updateUserSetting("petrolStationNotifications", isChecked));
        }
        if (emergencySwitch != null) {
            emergencySwitch.setChecked(prefs.getBoolean("emergencyNotifications", true));
            emergencySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    updateUserSetting("emergencyNotifications", isChecked));
        }
        if (soundAlertsSwitch != null) {
            soundAlertsSwitch.setChecked(prefs.getBoolean("soundAlerts", false));
            soundAlertsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    updateUserSetting("soundAlerts", isChecked));
        }

        return view;
    }

    private void logout() {
        if (!isAdded() || getActivity() == null) {
            Log.w(TAG, "Fragment not attached, cannot logout");
            return;
        }
        if (prefs == null) {
            prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Log.w(TAG, "prefs was null, initialized in logout");
        }
        Log.d(TAG, "logout called", new Throwable());
        mAuth.signOut();
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear().apply();
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
        Toast.makeText(getActivity(), "Logged out successfully", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User logged out");
    }

    private void openPhotoPicker() {
        if (!isAdded() || getActivity() == null || currentUser == null) {
            Log.w(TAG, "Cannot select image: isAdded=" + isAdded() + ", activity=" + getActivity() + ", user=" + currentUser);
            Toast.makeText(getActivity(), "Unable to select image", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Intent chooserIntent = Intent.createChooser(intent, "Select Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        try {
            Log.d(TAG, "Launching image picker");
            photoPickerLauncher.launch(chooserIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching image picker: " + e.getMessage(), e);
            Toast.makeText(getActivity(), "Failed to open image picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImageToDatabase(Uri imageUri) {
        if (isImageUploading) {
            Log.w(TAG, "Image upload already in progress");
            Toast.makeText(getActivity(), "Image upload in progress, please wait", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAdded() || getActivity() == null || profileImageView == null) {
            Log.w(TAG, "Cannot upload image: Invalid state");
            return;
        }
        isImageUploading = true;

        executor.execute(() -> {
            try {
                InputStream inputStream = getActivity().getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    mainHandler.post(() -> {
                        Log.e(TAG, "Failed to open input stream for URI: " + imageUri);
                        Toast.makeText(getActivity(), "Failed to access image", Toast.LENGTH_SHORT).show();
                        isImageUploading = false;
                    });
                    return;
                }
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                if (bitmap != null) {
                    uploadImageFromBitmap(bitmap);
                } else {
                    mainHandler.post(() -> {
                        Log.e(TAG, "Failed to decode image from URI: " + imageUri);
                        Toast.makeText(getActivity(), "Invalid image format", Toast.LENGTH_SHORT).show();
                        isImageUploading = false;
                    });
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Error processing image: " + e.getMessage(), e);
                    Toast.makeText(getActivity(), "Error processing image", Toast.LENGTH_SHORT).show();
                    isImageUploading = false;
                });
            }
        });
    }

    private void uploadImageFromBitmap(Bitmap bitmap) {
        if (!isAdded() || getActivity() == null || currentUser == null || profileImageView == null) {
            Log.w(TAG, "Invalid state for bitmap upload");
            mainHandler.post(() -> {
                Toast.makeText(getActivity(), "Cannot upload image: App state invalid", Toast.LENGTH_SHORT).show();
                isImageUploading = false;
            });
            return;
        }

        executor.execute(() -> {
            Bitmap resizedBitmap = resizeBitmap(bitmap, 128);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

            if (base64Image.length() > 500_000) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Image size too large: " + base64Image.length() + " bytes");
                    Toast.makeText(getActivity(), "Image too large, select a smaller image", Toast.LENGTH_SHORT).show();
                    isImageUploading = false;
                });
                return;
            }

            String userId = currentUser.getUid();
            mDatabase.child(userId).child("profileImage").setValue(base64Image)
                    .addOnSuccessListener(aVoid -> mainHandler.post(() -> {
                        if (!isAdded() || getActivity() == null || profileImageView == null) {
                            Log.w(TAG, "Fragment detached after upload");
                            isImageUploading = false;
                            return;
                        }
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(KEY_PROFILE_IMAGE, base64Image);
                        editor.putLong("lastFetchTime", System.currentTimeMillis());
                        editor.apply();
                        profileImageView.setImageBitmap(resizedBitmap);
                        Log.d(TAG, "Profile image uploaded successfully, size: " + base64Image.length());
                        Toast.makeText(getActivity(), "Profile image updated", Toast.LENGTH_SHORT).show();
                        isImageUploading = false;
                    }))
                    .addOnFailureListener(e -> mainHandler.post(() -> {
                        if (isAdded() && getActivity() != null && profileImageView != null) {
                            Log.e(TAG, "Error uploading image to Firebase: " + e.getMessage(), e);
                            Toast.makeText(getActivity(), "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            profileImageView.setImageResource(R.drawable.profile_pic);
                        }
                        isImageUploading = false;
                    }));
        });
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxSize && height <= maxSize) return original;

        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        Bitmap resized = Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
        original.recycle();
        Log.d(TAG, "Bitmap resized to " + newWidth + "x" + newHeight);
        return resized;
    }

    private void loadCachedData() {
        if (!isAdded() || getActivity() == null || profileImageView == null || nameTextView == null || emailTextView == null) {
            Log.w(TAG, "Cannot load cached data: Fragment not attached or views not initialized");
            return;
        }

        String name = prefs.getString(KEY_NAME, null);
        String email = prefs.getString(KEY_EMAIL, null);
        String base64Image = prefs.getString(KEY_PROFILE_IMAGE, null);

        Log.d(TAG, "Cache - Name: " + name + ", Email: " + email + ", Image Length: " + (base64Image != null ? base64Image.length() : 0));

        profileImageView.setImageResource(R.drawable.profile_pic);
        nameTextView.setText(name != null && !name.isEmpty() ? name : "Name not set");
        emailTextView.setText(email != null && !email.isEmpty() ? email : "Email not set");

        if (base64Image != null && !base64Image.isEmpty()) {
            executor.execute(() -> {
                try {
                    byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
                    Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    mainHandler.post(() -> {
                        if (isAdded() && getActivity() != null && profileImageView != null) {
                            if (decodedBitmap != null) {
                                profileImageView.setImageBitmap(decodedBitmap);
                                Log.d(TAG, "Cached image loaded successfully");
                            } else {
                                Log.e(TAG, "Decoded bitmap is null");
                                profileImageView.setImageResource(R.drawable.profile_pic);
                            }
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (isAdded() && getActivity() != null && profileImageView != null) {
                            Log.e(TAG, "Error decoding cached image: " + e.getMessage(), e);
                            profileImageView.setImageResource(R.drawable.profile_pic);
                        }
                    });
                }
            });
        }
    }

    private void loadProfileData() {
        if (!isAdded() || getActivity() == null || currentUser == null || profileImageView == null || nameTextView == null || emailTextView == null) {
            Log.w(TAG, "Invalid state for background data load");
            return;
        }

        long lastFetchTime = prefs.getLong("lastFetchTime", 0);
        if (System.currentTimeMillis() - lastFetchTime < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Using fresh cached data");
            loadCachedData();
            return;
        }

        String userId = currentUser.getUid();
        Log.d(TAG, "Fetching data for UserID: " + userId);
        DatabaseReference userRef = mDatabase.child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || getActivity() == null || profileImageView == null || nameTextView == null || emailTextView == null) {
                    Log.w(TAG, "Fragment detached or views unavailable during data fetch");
                    return;
                }
                String name = snapshot.child("name").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String base64Image = snapshot.child("profileImage").getValue(String.class);
                Boolean trafficAlerts = snapshot.child("trafficAlerts").getValue(Boolean.class);
                Boolean petrolStation = snapshot.child("petrolStationNotifications").getValue(Boolean.class);
                Boolean emergency = snapshot.child("emergencyNotifications").getValue(Boolean.class);
                Boolean soundAlerts = snapshot.child("soundAlerts").getValue(Boolean.class);
                String language = snapshot.child("language").getValue(String.class);
                String dataLimit = snapshot.child("dataLimit").getValue(String.class);
                Boolean dataSharing = snapshot.child("dataSharing").getValue(Boolean.class);
                Boolean emailNotifications = snapshot.child("emailNotifications").getValue(Boolean.class);

                Log.d(TAG, "Firebase - Name: " + name + ", Email: " + email + ", Image Length: " + (base64Image != null ? base64Image.length() : 0));

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_NAME, name);
                editor.putString(KEY_EMAIL, email);
                editor.putString(KEY_PROFILE_IMAGE, base64Image);
                editor.putBoolean("trafficAlerts", trafficAlerts != null ? trafficAlerts : true);
                editor.putBoolean("petrolStationNotifications", petrolStation != null ? petrolStation : true);
                editor.putBoolean("emergencyNotifications", emergency != null ? emergency : true);
                editor.putBoolean("soundAlerts", soundAlerts != null ? soundAlerts : false);
                editor.putString("language", language != null ? language : "English");
                editor.putString("dataLimit", dataLimit != null ? dataLimit : "Low");
                editor.putBoolean("dataSharing", dataSharing != null ? dataSharing : false);
                editor.putBoolean("emailNotifications", emailNotifications != null ? emailNotifications : true);
                editor.putLong("lastFetchTime", System.currentTimeMillis());
                editor.apply();

                mainHandler.post(() -> {
                    if (!isAdded() || getActivity() == null || profileImageView == null || nameTextView == null || emailTextView == null) {
                        Log.w(TAG, "Fragment or views not available, skipping UI update");
                        return;
                    }
                    nameTextView.setText(name != null && !name.isEmpty() ? name : "Name not set");
                    emailTextView.setText(email != null && !email.isEmpty() ? email : "Email not set");

                    if (base64Image != null && !base64Image.isEmpty()) {
                        executor.execute(() -> {
                            try {
                                byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
                                Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                                mainHandler.post(() -> {
                                    if (isAdded() && getActivity() != null && profileImageView != null) {
                                        if (decodedBitmap != null) {
                                            profileImageView.setImageBitmap(decodedBitmap);
                                            Log.d(TAG, "Firebase image loaded successfully");
                                        } else {
                                            Log.e(TAG, "Firebase decoded bitmap is null");
                                            profileImageView.setImageResource(R.drawable.profile_pic);
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                mainHandler.post(() -> {
                                    if (isAdded() && getActivity() != null && profileImageView != null) {
                                        Log.e(TAG, "Error decoding Firebase image: " + e.getMessage(), e);
                                        profileImageView.setImageResource(R.drawable.profile_pic);
                                    }
                                });
                            }
                        });
                    } else {
                        profileImageView.setImageResource(R.drawable.profile_pic);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mainHandler.post(() -> {
                    if (isAdded() && getActivity() != null && nameTextView != null && emailTextView != null && profileImageView != null) {
                        Log.e(TAG, "Failed to load profile data: " + error.getMessage(), error.toException());
                        Toast.makeText(getActivity(), "Failed to load profile data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        nameTextView.setText("Name not set");
                        emailTextView.setText("Email not set");
                        profileImageView.setImageResource(R.drawable.profile_pic);
                    }
                });
            }
        });
    }

    private void updateUserSetting(String key, Object value) {
        if (!isAdded() || getActivity() == null || currentUser == null) {
            Log.w(TAG, "Invalid state for updating setting: " + key);
            return;
        }

        mDatabase.child(currentUser.getUid()).child(key).setValue(value)
                .addOnSuccessListener(aVoid -> mainHandler.post(() -> {
                    if (!isAdded() || getActivity() == null) return;
                    SharedPreferences.Editor editor = prefs.edit();
                    if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else {
                        editor.putString(key, value.toString());
                    }
                    editor.putLong("lastFetchTime", System.currentTimeMillis());
                    editor.apply();
                    Toast.makeText(getActivity(), key + " updated", Toast.LENGTH_SHORT).show();
                }))
                .addOnFailureListener(e -> mainHandler.post(() -> {
                    if (isAdded() && getActivity() != null) {
                        Log.e(TAG, "Failed to update " + key + ": " + e.getMessage(), e);
                        Toast.makeText(getActivity(), "Failed to update " + key, Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void showEditProfileDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_edit_profile, null);
        LinearLayout container = view.findViewById(R.id.bottom_sheet_container);
        if (container == null) {
            Log.e(TAG, "bottom_sheet_container not found in bottom_sheet_edit_profile");
            return;
        }
        EditText nameInput = new EditText(getActivity());
        nameInput.setHint("Enter name");
        nameInput.setText(nameTextView != null ? nameTextView.getText() : "");
        EditText emailInput = new EditText(getActivity());
        emailInput.setHint("Enter email");
        emailInput.setText(emailTextView != null ? emailTextView.getText() : "");
        Button saveButton = new Button(getActivity());
        saveButton.setText("Save");
        saveButton.setTextColor(ContextCompat.getColor(getActivity(), R.color.blue));
        saveButton.setBackgroundResource(android.R.drawable.btn_default);

        container.addView(nameInput);
        container.addView(emailInput);
        container.addView(saveButton);

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            if (!name.isEmpty() && !email.isEmpty() && email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                mDatabase.child(currentUser.getUid()).child("name").setValue(name);
                mDatabase.child(currentUser.getUid()).child("email").setValue(email);
                if (nameTextView != null) nameTextView.setText(name);
                if (emailTextView != null) emailTextView.setText(email);
                prefs.edit().putString(KEY_NAME, name).putString(KEY_EMAIL, email).putLong("lastFetchTime", System.currentTimeMillis()).apply();
                dialog.dismiss();
                Toast.makeText(getActivity(), "Profile updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), "Please enter valid name and email", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showChangePasswordDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_change_password, null);
        LinearLayout container = view.findViewById(R.id.bottom_sheet_container);
        if (container == null) {
            Log.e(TAG, "bottom_sheet_container not found in bottom_sheet_change_password");
            return;
        }
        EditText currentPasswordInput = new EditText(getActivity());
        currentPasswordInput.setHint("Current Password");
        currentPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText newPasswordInput = new EditText(getActivity());
        newPasswordInput.setHint("New Password");
        newPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button saveButton = new Button(getActivity());
        saveButton.setText("Save");
        saveButton.setTextColor(ContextCompat.getColor(getActivity(), R.color.blue));
        saveButton.setBackgroundResource(android.R.drawable.btn_default);

        container.addView(currentPasswordInput);
        container.addView(newPasswordInput);
        container.addView(saveButton);

        saveButton.setOnClickListener(v -> {
            String newPassword = newPasswordInput.getText().toString().trim();
            if (!newPassword.isEmpty() && newPassword.length() >= 6) {
                currentUser.updatePassword(newPassword)
                        .addOnSuccessListener(aVoid -> {
                            dialog.dismiss();
                            Toast.makeText(getActivity(), "Password updated", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getActivity(), "Password update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(getActivity(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showLanguageSettingsDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_language_settings, null);
        RecyclerView recyclerView = view.findViewById(R.id.language_recycler_view);
        if (recyclerView == null) {
            Log.e(TAG, "language_recycler_view not found in bottom_sheet_language_settings");
            return;
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        List<String> languages = Arrays.asList("English", "Spanish", "French", "German", "Hindi");
        LanguageAdapter adapter = new LanguageAdapter(languages, language -> {
            updateUserSetting("language", language);
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);

        dialog.setContentView(view);
        dialog.show();
    }

    private void showNotificationPrefsDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_notification_prefs, null);
        LinearLayout container = view.findViewById(R.id.bottom_sheet_container);
        if (container == null) {
            Log.e(TAG, "bottom_sheet_container not found in bottom_sheet_notification_prefs");
            return;
        }
        Switch emailNotificationsSwitch = new Switch(getActivity());
        emailNotificationsSwitch.setText("Email Notifications");
        emailNotificationsSwitch.setChecked(prefs.getBoolean("emailNotifications", true));
        Switch pushNotificationsSwitch = new Switch(getActivity());
        pushNotificationsSwitch.setText("Push Notifications");
        pushNotificationsSwitch.setChecked(prefs.getBoolean("pushNotifications", true));

        container.addView(emailNotificationsSwitch);
        container.addView(pushNotificationsSwitch);

        emailNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                updateUserSetting("emailNotifications", isChecked));
        pushNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                updateUserSetting("pushNotifications", isChecked));

        dialog.setContentView(view);
        dialog.show();
    }

    private void showLocationServicesDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_location_services, null);
        LinearLayout container = view.findViewById(R.id.bottom_sheet_container);
        if (container == null) {
            Log.e(TAG, "bottom_sheet_container not found in bottom_sheet_location_services");
            return;
        }
        TextView locationStatus = new TextView(getActivity());
        boolean isLocationEnabled = isLocationEnabled();
        locationStatus.setText("Location: " + (isLocationEnabled ? "Enabled" : "Disabled"));
        Button toggleLocationButton = new Button(getActivity());
        toggleLocationButton.setText(isLocationEnabled ? "Disable Location" : "Enable Location");
        toggleLocationButton.setTextColor(ContextCompat.getColor(getActivity(), R.color.blue));
        toggleLocationButton.setBackgroundResource(android.R.drawable.btn_default);

        container.addView(locationStatus);
        container.addView(toggleLocationButton);

        toggleLocationButton.setOnClickListener(v -> {
            boolean enabled = !isLocationEnabled();
            updateUserSetting("locationServices", enabled);
            dialog.dismiss();
            Toast.makeText(getActivity(), "Location " + (enabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showDataUsageDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_data_usage, null);
        LinearLayout container = view.findViewById(R.id.bottom_sheet_container);
        if (container == null) {
            Log.e(TAG, "bottom_sheet_container not found in bottom_sheet_data_usage");
            return;
        }
        TextView dataUsageInfo = new TextView(getActivity());
        String currentLimit = prefs.getString("dataLimit", "Low");
        dataUsageInfo.setText("Data Usage Limit: " + currentLimit);
        Button lowLimitButton = new Button(getActivity());
        lowLimitButton.setText("Set Low Limit");
        lowLimitButton.setTextColor(ContextCompat.getColor(getActivity(), R.color.blue));
        lowLimitButton.setBackgroundResource(android.R.drawable.btn_default);
        Button highLimitButton = new Button(getActivity());
        highLimitButton.setText("Set High Limit");
        highLimitButton.setTextColor(ContextCompat.getColor(getActivity(), R.color.blue));
        highLimitButton.setBackgroundResource(android.R.drawable.btn_default);

        container.addView(dataUsageInfo);
        container.addView(lowLimitButton);
        container.addView(highLimitButton);

        lowLimitButton.setOnClickListener(v -> {
            updateUserSetting("dataLimit", "Low");
            dialog.dismiss();
            Toast.makeText(getActivity(), "Data limit set to Low", Toast.LENGTH_SHORT).show();
        });

        highLimitButton.setOnClickListener(v -> {
            updateUserSetting("dataLimit", "High");
            dialog.dismiss();
            Toast.makeText(getActivity(), "Data limit set to High", Toast.LENGTH_SHORT).show();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showPrivacySettingsDialog() {
        if (!isAdded() || getActivity() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_privacy_settings, null);
        LinearLayout container = view.findViewById(R.id.bottom_sheet_container);
        if (container == null) {
            Log.e(TAG, "bottom_sheet_container not found in bottom_sheet_privacy_settings");
            return;
        }
        Switch dataSharingSwitch = new Switch(getActivity());
        dataSharingSwitch.setText("Share Usage Data");
        dataSharingSwitch.setChecked(prefs.getBoolean("dataSharing", false));
        Switch analyticsSwitch = new Switch(getActivity());
        analyticsSwitch.setText("Enable Analytics");
        analyticsSwitch.setChecked(prefs.getBoolean("analytics", false));

        container.addView(dataSharingSwitch);
        container.addView(analyticsSwitch);

        dataSharingSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                updateUserSetting("dataSharing", isChecked));
        analyticsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                updateUserSetting("analytics", isChecked));

        dialog.setContentView(view);
        dialog.show();
    }

    private boolean isLocationEnabled() {
        if (!isAdded() || getActivity() == null) return false;
        return ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private static class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.ViewHolder> {
        private final List<String> languages;
        private final OnLanguageClickListener listener;

        interface OnLanguageClickListener {
            void onLanguageClick(String language);
        }

        LanguageAdapter(List<String> languages, OnLanguageClickListener listener) {
            this.languages = languages;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setPadding(16, 16, 16, 16);
            textView.setTextSize(16);
            textView.setTextColor(ContextCompat.getColor(parent.getContext(), android.R.color.black));
            return new ViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String language = languages.get(position);
            holder.textView.setText(language);
            holder.textView.setOnClickListener(v -> listener.onLanguageClick(language));
        }

        @Override
        public int getItemCount() {
            return languages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(@NonNull TextView itemView) {
                super(itemView);
                textView = itemView;
            }
        }
    }
}