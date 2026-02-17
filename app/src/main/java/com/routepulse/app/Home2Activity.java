package com.routepulse.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import com.google.maps.model.TravelMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Home2Activity extends AppCompatActivity {
    private static final String TAG = "Home2Activity";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final String PREFS_NAME = "RoutePulsePrefs";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final String KEY_CACHED_PLACES = "cached_places";
    private MapView mapView;
    private GoogleMap googleMap;
    private ImageView backButton, settingsButton;
    private TextView title, alertStatusText, alertDistanceText, alertActiveText;
    private LinearLayout routesList, stationsList, hospitalsList, navigationInfoCard;
    private Button startNavigationButton;
    private NestedScrollView scrollView;
    private String currentAddress, destinationAddress;
    private LatLng currentLatLng, destinationLatLng;
    private List<String> trafficAlerts, petrolBunks, hospitals, notifications;
    private DirectionsResult directionsResult;
    private int selectedRouteIndex = 0;
    private Bundle savedMapState;
    private TextView reachingAtText, distanceText;
    private Handler navigationHandler;
    private Runnable navigationRunnable;
    private boolean isNavigating = false;
    private GeoApiContext geoApiContext;
    private ExecutorService executorService = Executors.newFixedThreadPool(1);
    private SharedPreferences prefs;
    private List<PlacesSearchResult> cachedGasStations;
    private List<PlacesSearchResult> cachedHospitals;
    private long navigationStartTime;
    private LatLng waypointLatLng = null;
    private String waypointName = null;
    private com.google.android.gms.maps.model.Marker waypointMarker = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home2);
        savedMapState = savedInstanceState != null ? savedInstanceState.getBundle("mapView") : null;

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadNotifications();

        NotificationHelper.createNotificationChannel(this);

        backButton = findViewById(R.id.backButton);
        settingsButton = findViewById(R.id.settingsButton);
        title = findViewById(R.id.title);
        alertStatusText = findViewById(R.id.status_text);
        alertDistanceText = findViewById(R.id.distance_text);
        alertActiveText = findViewById(R.id.active_text);
        mapView = findViewById(R.id.mapView);
        scrollView = findViewById(R.id.scrollView);
        routesList = findViewById(R.id.routesList);
        stationsList = findViewById(R.id.stationsList);
        hospitalsList = findViewById(R.id.hospitalsList);
        startNavigationButton = findViewById(R.id.startNavigationButton);
        navigationInfoCard = findViewById(R.id.navigationInfoCard);
        reachingAtText = findViewById(R.id.reachingAtText);
        distanceText = findViewById(R.id.distanceText);

        geoApiContext = new GeoApiContext.Builder()
                .apiKey(getString(R.string.google_maps_key))
                .build();

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
        } else {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
        }

        Intent intent = getIntent();
        currentAddress = intent.getStringExtra("current_address");
        destinationAddress = intent.getStringExtra("destination_address");
        currentLatLng = new LatLng(intent.getDoubleExtra("current_lat", 0), intent.getDoubleExtra("current_lng", 0));
        destinationLatLng = new LatLng(intent.getDoubleExtra("destination_lat", 0), intent.getDoubleExtra("destination_lng", 0));
        trafficAlerts = intent.getStringArrayListExtra("traffic_alerts");
        petrolBunks = new ArrayList<>();
        hospitals = new ArrayList<>();
        directionsResult = (DirectionsResult) intent.getSerializableExtra("directions_result");

        loadCachedData();

        mapView.onCreate(savedMapState);
        mapView.getMapAsync(map -> {
            googleMap = map;
            if (googleMap == null) {
                Toast.makeText(this, "Failed to initialize map", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Map initialization failed");
                return;
            }
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
            }
            updateMap();
        });

        title.setText("Traffic Alert");
        updateAlertCard();
        populateRoutes();

        stationsList.removeAllViews();
        hospitalsList.removeAllViews();
        TextView loadingStations = new TextView(this);
        loadingStations.setText("Loading petrol stations...");
        loadingStations.setTextSize(14);
        loadingStations.setTextColor(Color.parseColor("#555555"));
        loadingStations.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
        stationsList.addView(loadingStations);
        TextView loadingHospitals = new TextView(this);
        loadingHospitals.setText("Loading hospitals...");
        loadingHospitals.setTextSize(14);
        loadingHospitals.setTextColor(Color.parseColor("#555555"));
        loadingHospitals.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
        hospitalsList.addView(loadingHospitals);

        fetchNearbyData(false);

        backButton.setOnClickListener(v -> finish());
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        startNavigationButton.setOnClickListener(v -> {
            if (!isNavigating) {
                startNavigation();
            } else {
                stopNavigation();
            }
        });
    }

    private void loadNotifications() {
        String notificationsJson = prefs.getString(KEY_NOTIFICATIONS, null);
        if (notificationsJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<String>>(){}.getType();
            notifications = gson.fromJson(notificationsJson, type);
        }
        if (notifications == null) {
            notifications = new ArrayList<>();
        }
    }

    private void saveNotifications() {
        Gson gson = new Gson();
        String notificationsJson = gson.toJson(notifications);
        prefs.edit().putString(KEY_NOTIFICATIONS, notificationsJson).apply();
        Log.d(TAG, "Saved notifications: " + notifications.size());
    }

    private double getDistanceToPoint(DirectionsRoute route, LatLng currentPosition, LatLng target) {
        List<com.google.maps.model.LatLng> path = route.overviewPolyline.decodePath();
        double minDist = Double.MAX_VALUE;
        int closestIndex = 0;
        for (int i = 0; i < path.size(); i++) {
            double dist = calculateDistance(path.get(i).lat, path.get(i).lng, target.latitude, target.longitude);
            if (dist < minDist) {
                minDist = dist;
                closestIndex = i;
            }
        }
        double currentDist = Double.MAX_VALUE;
        int currentIndex = 0;
        for (int i = 0; i < path.size(); i++) {
            double dist = calculateDistance(path.get(i).lat, path.get(i).lng, currentPosition.latitude, currentPosition.longitude);
            if (dist < currentDist) {
                currentDist = dist;
                currentIndex = i;
            }
        }
        if (currentIndex >= closestIndex) return 0;
        double routeDist = 0;
        for (int i = currentIndex; i < closestIndex && i < path.size() - 1; i++) {
            routeDist += calculateDistance(path.get(i).lat, path.get(i).lng, path.get(i + 1).lat, path.get(i + 1).lng);
        }
        return Math.round(routeDist * 10.0) / 10.0;
    }

    private void loadCachedData() {
        Gson gson = new Gson();
        String gasStationsJson = prefs.getString(KEY_CACHED_PLACES + "_gas", null);
        String hospitalsJson = prefs.getString(KEY_CACHED_PLACES + "_hospitals", null);
        long gasCacheTime = prefs.getLong(KEY_CACHED_PLACES + "_gas_time", 0);
        long hospCacheTime = prefs.getLong(KEY_CACHED_PLACES + "_hosp_time", 0);
        long currentTime = System.currentTimeMillis();
        long cacheValidity = 3600 * 1000; // 1 hour validity

        Type listType = new TypeToken<List<PlacesSearchResult>>(){}.getType();
        if (gasStationsJson != null && (currentTime - gasCacheTime) < cacheValidity) {
            cachedGasStations = gson.fromJson(gasStationsJson, listType);
            petrolBunks = new ArrayList<>();
            for (PlacesSearchResult result : cachedGasStations) {
                if (result.geometry == null || result.geometry.location == null) {
                    continue;
                }
                String name = result.name != null ? result.name : "Unknown Petrol Station";
                String vicinity = result.vicinity != null ? result.vicinity : "Unknown location";
                String coords = String.format("(%.4f, %.4f)", result.geometry.location.lat, result.geometry.location.lng);
                double distance = calculateDistance(currentLatLng.latitude, currentLatLng.longitude,
                        result.geometry.location.lat, result.geometry.location.lng);
                String status = result.openingHours != null ? (result.openingHours.openNow ? "Open" : "Closed") : "Unknown";
                String gasStation = String.format("%s|%s|%.1f mi|%s|%s|Petrol", name, vicinity, distance, coords, status);
                petrolBunks.add(gasStation);
            }
        } else {
            cachedGasStations = null;
            petrolBunks = new ArrayList<>();
        }

        if (hospitalsJson != null && (currentTime - hospCacheTime) < cacheValidity) {
            cachedHospitals = gson.fromJson(hospitalsJson, listType);
            hospitals = new ArrayList<>();
            for (PlacesSearchResult result : cachedHospitals) {
                if (result.geometry == null || result.geometry.location == null) {
                    continue;
                }
                String name = result.name != null ? result.name : "Unknown Hospital";
                String vicinity = result.vicinity != null ? result.vicinity : "Unknown location";
                String coords = String.format("(%.4f, %.4f)", result.geometry.location.lat, result.geometry.location.lng);
                double distance = calculateDistance(currentLatLng.latitude, currentLatLng.longitude,
                        result.geometry.location.lat, result.geometry.location.lng);
                String status = result.openingHours != null ? (result.openingHours.openNow ? "Open" : "Closed") : "Unknown";
                String hospital = String.format("%s|%s|%.1f mi|%s|%s|Hospital", name, vicinity, distance, coords, status);
                hospitals.add(hospital);
            }
        } else {
            cachedHospitals = null;
            hospitals = new ArrayList<>();
        }
    }

    private void saveCachedData() {
        Gson gson = new Gson();
        SharedPreferences.Editor editor = prefs.edit();
        long currentTime = System.currentTimeMillis();
        if (cachedGasStations != null) {
            editor.putString(KEY_CACHED_PLACES + "_gas", gson.toJson(cachedGasStations));
            editor.putLong(KEY_CACHED_PLACES + "_gas_time", currentTime);
        }
        if (cachedHospitals != null) {
            editor.putString(KEY_CACHED_PLACES + "_hospitals", gson.toJson(cachedHospitals));
            editor.putLong(KEY_CACHED_PLACES + "_hosp_time", currentTime);
        }
        editor.apply();
    }

    private static class PlaceWithDist {
        PlacesSearchResult place;
        double dist;

        PlaceWithDist(PlacesSearchResult p, double d) {
            place = p;
            dist = d;
        }
    }

    private double getDistanceAlongRoute(DirectionsRoute route, com.google.maps.model.LatLng target) {
        List<com.google.maps.model.LatLng> path = route.overviewPolyline.decodePath();
        double minDist = Double.MAX_VALUE;
        int closestIndex = 0;
        for (int i = 0; i < path.size(); i++) {
            double dist = calculateDistance(path.get(i).lat, path.get(i).lng, target.lat, target.lng);
            if (dist < minDist) {
                minDist = dist;
                closestIndex = i;
            }
        }
        if (minDist > 0.186) return -1;
        double routeDist = 0;
        for (int i = 0; i < closestIndex && i < path.size() - 1; i++) {
            routeDist += calculateDistance(path.get(i).lat, path.get(i).lng, path.get(i + 1).lat, path.get(i + 1).lng);
        }
        return routeDist;
    }

    private void fetchNearbyData(boolean highPriority) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            runOnUiThread(() -> {
                ProgressBar progressBar = findViewById(R.id.progressBar);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                stationsList.removeAllViews();
                hospitalsList.removeAllViews();
                TextView loadingStations = new TextView(this);
                loadingStations.setText("Loading petrol stations...");
                loadingStations.setTextSize(14);
                loadingStations.setTextColor(Color.parseColor("#555555"));
                loadingStations.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
                stationsList.addView(loadingStations);
                TextView loadingHospitals = new TextView(this);
                loadingHospitals.setText("Loading hospitals...");
                loadingHospitals.setTextSize(14);
                loadingHospitals.setTextColor(Color.parseColor("#555555"));
                loadingHospitals.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
                hospitalsList.addView(loadingHospitals);
            });
            return;
        }

        // Check cache first
        long currentTime = System.currentTimeMillis();
        long cacheValidity = 3600 * 1000; // 1 hour
        boolean useCache = !highPriority &&
                prefs.getLong(KEY_CACHED_PLACES + "_gas_time", 0) > (currentTime - cacheValidity) &&
                prefs.getLong(KEY_CACHED_PLACES + "_hosp_time", 0) > (currentTime - cacheValidity) &&
                !petrolBunks.isEmpty() && !hospitals.isEmpty();

        if (useCache) {
            runOnUiThread(() -> {
                ProgressBar progressBar = findViewById(R.id.progressBar);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                populateWaypoints();
            });
            return;
        }

        runOnUiThread(() -> {
            ProgressBar progressBar = findViewById(R.id.progressBar);
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            stationsList.removeAllViews();
            hospitalsList.removeAllViews();
            TextView loadingStations = new TextView(this);
            loadingStations.setText("Loading petrol stations...");
            loadingStations.setTextSize(14);
            loadingStations.setTextColor(Color.parseColor("#555555"));
            loadingStations.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
            stationsList.addView(loadingStations);
            TextView loadingHospitals = new TextView(this);
            loadingHospitals.setText("Loading hospitals...");
            loadingHospitals.setTextSize(14);
            loadingHospitals.setTextColor(Color.parseColor("#555555"));
            loadingHospitals.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
            hospitalsList.addView(loadingHospitals);
        });

        executorService.execute(() -> {
            int notificationIdBase = notifications.size();

            if (directionsResult == null || directionsResult.routes == null || selectedRouteIndex >= directionsResult.routes.length) {
                Log.e(TAG, "No valid route for fetching nearby data");
                runOnUiThread(() -> {
                    Toast.makeText(this, "No route available", Toast.LENGTH_SHORT).show();
                    ProgressBar progressBar = findViewById(R.id.progressBar);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    populateWaypoints(); // Fallback to cached data
                });
                return;
            }

            DirectionsRoute route = directionsResult.routes[selectedRouteIndex];
            List<com.google.maps.model.LatLng> path = route.overviewPolyline.decodePath();
            if (path.isEmpty()) {
                Log.e(TAG, "Empty route path");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Invalid route path", Toast.LENGTH_SHORT).show();
                    ProgressBar progressBar = findViewById(R.id.progressBar);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    populateWaypoints(); // Fallback to cached data
                });
                return;
            }

            int maxSamples = Math.max(10, (int) (route.legs[0].distance.inMeters / 1000));
            int step = Math.max(1, path.size() / maxSamples);
            List<com.google.maps.model.LatLng> samplePoints = new ArrayList<>();
            samplePoints.add(new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude));
            for (int i = 0; i < path.size(); i += step) {
                samplePoints.add(path.get(i));
            }
            if (!samplePoints.get(samplePoints.size() - 1).equals(path.get(path.size() - 1))) {
                samplePoints.add(path.get(path.size() - 1));
            }

            boolean fetchGas = cachedGasStations == null || cachedGasStations.isEmpty();
            boolean fetchHosp = cachedHospitals == null || cachedHospitals.isEmpty();

            if (fetchGas) {
                try {
                    List<PlaceWithDist> gasWithDist = new ArrayList<>();
                    Set<String> gasPlaceIds = new HashSet<>();
                    for (com.google.maps.model.LatLng point : samplePoints) {
                        PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, point)
                                .radius(5000) // 5km radius
                                .type(PlaceType.GAS_STATION)
                                .await();
                        if (response.results != null) {
                            for (PlacesSearchResult result : response.results) {
                                if (result.geometry == null || result.geometry.location == null) {
                                    continue;
                                }
                                if (!gasPlaceIds.contains(result.placeId)) {
                                    double routeDist = getDistanceAlongRoute(route, result.geometry.location);
                                    double straightDist = calculateDistance(currentLatLng.latitude, currentLatLng.longitude,
                                            result.geometry.location.lat, result.geometry.location.lng);
                                    if (routeDist >= 0) {
                                        gasPlaceIds.add(result.placeId);
                                        gasWithDist.add(new PlaceWithDist(result, straightDist));
                                    }
                                }
                            }
                        }
                    }
                    Collections.sort(gasWithDist, (a, b) -> Double.compare(a.dist, b.dist));
                    cachedGasStations = new ArrayList<>();
                    List<String> newPetrolBunks = new ArrayList<>();
                    int count = 0;
                    for (PlaceWithDist pwd : gasWithDist) {
                        if (count >= 5) break;
                        PlacesSearchResult result = pwd.place;
                        cachedGasStations.add(result);
                        String name = result.name != null ? result.name : "Unknown Petrol Station";
                        String vicinity = result.vicinity != null ? result.vicinity : "Unknown location";
                        String coords = String.format("(%.4f, %.4f)", result.geometry.location.lat, result.geometry.location.lng);
                        double distance = pwd.dist;
                        String status = result.openingHours != null ? (result.openingHours.openNow ? "Open" : "Closed") : "Unknown";
                        String gasStation = String.format("%s|%s|%.1f mi|%s|%s|Petrol", name, vicinity, distance, coords, status);
                        newPetrolBunks.add(gasStation);
                        count++;
                    }
                    petrolBunks = newPetrolBunks;
                    saveCachedData();
                } catch (Exception e) {
                    Log.e(TAG, "Places API error for petrol stations: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(this, "Failed to fetch petrol stations", Toast.LENGTH_SHORT).show());
                }
            }

            if (fetchHosp) {
                try {
                    List<PlaceWithDist> hospWithDist = new ArrayList<>();
                    Set<String> hospPlaceIds = new HashSet<>();
                    for (com.google.maps.model.LatLng point : samplePoints) {
                        PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext, point)
                                .radius(5000) // 5km radius
                                .type(PlaceType.HOSPITAL)
                                .await();
                        if (response.results != null) {
                            for (PlacesSearchResult result : response.results) {
                                if (result.geometry == null || result.geometry.location == null) {
                                    continue;
                                }
                                if (!hospPlaceIds.contains(result.placeId)) {
                                    double routeDist = getDistanceAlongRoute(route, result.geometry.location);
                                    double straightDist = calculateDistance(currentLatLng.latitude, currentLatLng.longitude,
                                            result.geometry.location.lat, result.geometry.location.lng);
                                    if (routeDist >= 0) {
                                        hospPlaceIds.add(result.placeId);
                                        hospWithDist.add(new PlaceWithDist(result, straightDist));
                                    }
                                }
                            }
                        }
                    }
                    Collections.sort(hospWithDist, (a, b) -> Double.compare(a.dist, b.dist));
                    cachedHospitals = new ArrayList<>();
                    List<String> newHospitals = new ArrayList<>();
                    int count = 0;
                    for (PlaceWithDist pwd : hospWithDist) {
                        if (count >= 5) break;
                        PlacesSearchResult result = pwd.place;
                        cachedHospitals.add(result);
                        String name = result.name != null ? result.name : "Unknown Hospital";
                        String vicinity = result.vicinity != null ? result.vicinity : "Unknown location";
                        String coords = String.format("(%.4f, %.4f)", result.geometry.location.lat, result.geometry.location.lng);
                        double distance = pwd.dist;
                        String status = result.openingHours != null ? (result.openingHours.openNow ? "Open" : "Closed") : "Unknown";
                        String hospital = String.format("%s|%s|%.1f mi|%s|%s|Hospital", name, vicinity, distance, coords, status);
                        newHospitals.add(hospital);
                        count++;
                    }
                    hospitals = newHospitals;
                    saveCachedData();
                } catch (Exception e) {
                    Log.e(TAG, "Places API error for hospitals: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(this, "Failed to fetch hospitals", Toast.LENGTH_SHORT).show());
                }
            }

            runOnUiThread(() -> {
                ProgressBar progressBar = findViewById(R.id.progressBar);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                populateWaypoints();
            });

            if (trafficAlerts == null || trafficAlerts.isEmpty()) {
                trafficAlerts = new ArrayList<>();
                String trafficAlert = String.format("Clear on Road|0.5 mi|Clear|%.4f,%.4f", currentLatLng.latitude + 0.005, currentLatLng.longitude + 0.005);
                trafficAlerts.add(trafficAlert);
                if (highPriority && !notifications.contains(trafficAlert)) {
                    notifications.add(trafficAlert);
                    if (prefs.getBoolean("notifications_enabled", true)) {
                        NotificationHelper.showNotification(this, "Traffic Alert", "Clear on Road", notificationIdBase++, highPriority);
                    }
                }
                runOnUiThread(() -> {
                    updateAlertCard();
                    saveNotifications();
                });
            }
        });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 0.621371; // Convert to miles
        return Math.round(distance * 10.0) / 10.0; // Round to 1 decimal
    }

    private void updateNavigationUI(LatLng currentPosition) {
        if (directionsResult == null || directionsResult.routes == null || selectedRouteIndex >= directionsResult.routes.length) {
            return;
        }
        DirectionsRoute route = directionsResult.routes[selectedRouteIndex];
        if (waypointLatLng != null && waypointName != null) {
            double distToWaypoint = getDistanceToPoint(route, currentPosition, waypointLatLng);
            if (distToWaypoint < 0.01) {
                Toast.makeText(this, "Arrived at " + waypointName, Toast.LENGTH_LONG).show();
                reachingAtText.setText("Destination: " + destinationAddress);
                double distToDest = getDistanceToPoint(route, currentPosition, destinationLatLng);
                distanceText.setText(String.format("Distance: %.1f mi", distToDest));
                if (waypointMarker != null) {
                    waypointMarker.remove();
                    waypointMarker = null;
                }
                waypointLatLng = null;
                waypointName = null;
                return;
            }
            reachingAtText.setText("Next Stop: " + waypointName);
            distanceText.setText(String.format("Distance to Stop: %.1f mi", distToWaypoint));
        } else {
            double distToDest = getDistanceToPoint(route, currentPosition, destinationLatLng);
            reachingAtText.setText("Destination: " + destinationAddress);
            distanceText.setText(String.format("Distance: %.1f mi", distToDest));
        }
    }

    private void startNavigation() {
        if (directionsResult == null || directionsResult.routes == null || selectedRouteIndex >= directionsResult.routes.length) {
            Toast.makeText(this, "No route selected", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Invalid route data: directionsResult=" + directionsResult + ", selectedRouteIndex=" + selectedRouteIndex);
            return;
        }

        isNavigating = true;
        startNavigationButton.setText("End Navigation");
        navigationInfoCard.setVisibility(View.VISIBLE);

        DirectionsRoute route = directionsResult.routes[selectedRouteIndex];
        // Temporary initial texts (will be updated immediately by updateNavigationUI)
        reachingAtText.setText("Destination: " + destinationAddress);
        distanceText.setText(String.format("Distance: %.1f mi", route.legs[0].distance.inMeters / 1609.34));

        if (googleMap != null) {
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions().position(currentLatLng).title("You are here"));
            googleMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination: " + destinationAddress));

            PolylineOptions polylineOptions = new PolylineOptions()
                    .color(Color.parseColor("#2979FF"))
                    .width(10);
            List<com.google.maps.model.LatLng> path = route.overviewPolyline.decodePath();
            for (com.google.maps.model.LatLng point : path) {
                polylineOptions.add(new LatLng(point.lat, point.lng));
            }
            googleMap.addPolyline(polylineOptions);

            if (waypointLatLng != null && waypointName != null) {
                waypointMarker = googleMap.addMarker(new MarkerOptions().position(waypointLatLng).title(waypointName));
            }

            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17), 1000, null);
        }

        int notificationIdBase = notifications.size();
        if (prefs.getBoolean("notifications_enabled", true)) {
            if (!petrolBunks.isEmpty()) {
                String[] parts = petrolBunks.get(0).split("\\|");
                if (parts.length >= 5) {
                    String notificationText = parts[0] + " - " + parts[4];
                    NotificationHelper.showNotification(this, "Nearby Petrol Station", notificationText, notificationIdBase++, true);
                    notifications.add(petrolBunks.get(0));
                }
            }
            if (!hospitals.isEmpty()) {
                String[] parts = hospitals.get(0).split("\\|");
                if (parts.length >= 5) {
                    String notificationText = parts[0] + " - " + parts[4];
                    NotificationHelper.showNotification(this, "Nearby Hospital", notificationText, notificationIdBase++, true);
                    notifications.add(hospitals.get(0));
                }
            }
            saveNotifications();
        }

        navigationStartTime = System.currentTimeMillis();
        navigationHandler = new Handler(Looper.getMainLooper());
        navigationRunnable = () -> {
            if (!isNavigating) return;
            LatLng currentPosition = interpolatePosition(route, System.currentTimeMillis() - navigationStartTime);
            if (currentPosition != null && googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder()
                                .target(currentPosition)
                                .zoom(17)
                                .tilt(45)
                                .bearing(0)
                                .build()
                ), 1000, null);

                updateNavigationUI(currentPosition);

                double distanceToDestination = getDistanceToPoint(route, currentPosition, destinationLatLng);
                if (distanceToDestination < 0.01) {
                    stopNavigation();
                    runOnUiThread(() -> Toast.makeText(this, "Arrived at destination", Toast.LENGTH_LONG).show());
                    return;
                }
            }
            navigationHandler.postDelayed(navigationRunnable, 2000);
        };

        // Initial UI update
        updateNavigationUI(currentLatLng);

        navigationHandler.postDelayed(navigationRunnable, 2000);

        Log.d(TAG, "Navigation started");
    }

    private LatLng interpolatePosition(DirectionsRoute route, long elapsedTime) {
        long totalDuration = route.legs[0].durationInTraffic != null ? route.legs[0].durationInTraffic.inSeconds : route.legs[0].duration.inSeconds;
        List<com.google.maps.model.LatLng> path = route.overviewPolyline.decodePath();
        if (path.isEmpty()) return currentLatLng;

        double progress = Math.min((double) elapsedTime / (totalDuration * 1000), 1.0);
        int index = (int) (progress * (path.size() - 1));
        if (index >= path.size() - 1) index = path.size() - 2;

        com.google.maps.model.LatLng p1 = path.get(index);
        com.google.maps.model.LatLng p2 = path.get(index + 1);
        double fraction = progress * (path.size() - 1) - index;
        double lat = p1.lat + (p2.lat - p1.lat) * fraction;
        double lng = p1.lng + (p2.lng - p1.lng) * fraction;
        return new LatLng(lat, lng);
    }

    private void stopNavigation() {
        isNavigating = false;
        startNavigationButton.setText("Start Navigation");
        navigationInfoCard.setVisibility(View.GONE);
        if (navigationHandler != null && navigationRunnable != null) {
            navigationHandler.removeCallbacks(navigationRunnable);
            navigationRunnable = null;
        }
        reachingAtText.setText("Destination: --");
        distanceText.setText("Distance: --");
        if (googleMap != null) {
            googleMap.clear();
            updateMap();
            if (waypointLatLng != null && waypointName != null) {
                waypointMarker = googleMap.addMarker(new MarkerOptions().position(waypointLatLng).title(waypointName));
            }
        }
        fetchNearbyData(true);
        Log.d(TAG, "Navigation stopped");
    }

    private void updateAlertCard() {
        if (trafficAlerts == null || trafficAlerts.isEmpty()) {
            alertStatusText.setText("No Traffic Alerts");
            alertDistanceText.setText("Within 5km of your location");
            alertActiveText.setText("No Active Alerts");
            alertActiveText.setTextColor(Color.parseColor("#FF3B30"));
            return;
        }
        String alert = trafficAlerts.get(0);
        String[] parts = alert.split("\\|");
        if (parts.length >= 3) {
            alertStatusText.setText(parts[0]);
            alertDistanceText.setText(parts[1]);
            alertActiveText.setText("Active Alert");
            String status = parts[0].split(" ")[0];
            int color = status.equals("Heavy") ? Color.parseColor("#FF3B30") :
                    status.equals("Moderate") ? Color.parseColor("#FFB300") :
                            Color.parseColor("#4CAF50");
            alertActiveText.setTextColor(color);
            alertActiveText.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        } else {
            Log.e(TAG, "Invalid traffic alert format: " + alert);
        }
    }

    private void populateRoutes() {
        routesList.removeAllViews();
        if (directionsResult == null || directionsResult.routes == null) {
            TextView noRoutes = new TextView(this);
            noRoutes.setText("No routes available");
            noRoutes.setTextSize(14);
            noRoutes.setTextColor(Color.parseColor("#555555"));
            noRoutes.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
            routesList.addView(noRoutes);
            return;
        }

        for (int i = 0; i < directionsResult.routes.length; i++) {
            DirectionsRoute route = directionsResult.routes[i];
            String trafficStatus = trafficAlerts != null && i < trafficAlerts.size() ? trafficAlerts.get(i).split("\\|")[0].split(" ")[0] : "Unknown";
            String roadName = route.summary != null && !route.summary.isEmpty() ? route.summary : "Route " + (i + 1);
            long durationInTraffic = route.legs[0].durationInTraffic != null ? route.legs[0].durationInTraffic.inSeconds : route.legs[0].duration.inSeconds;
            String duration = String.format("%d min", durationInTraffic / 60);
            String distance = String.format("%.1f km", route.legs[0].distance.inMeters / 1000.0);

            View routeView = LayoutInflater.from(this).inflate(R.layout.route_item_home2, routesList, false);
            TextView nameText = routeView.findViewById(R.id.name_text);
            TextView detailsText = routeView.findViewById(R.id.details_text);
            TextView statusText = routeView.findViewById(R.id.status_text);
            if (nameText != null) nameText.setText(roadName);
            if (detailsText != null) detailsText.setText(duration + " · " + distance);
            if (statusText != null) {
                statusText.setText(trafficStatus);
                statusText.setTextColor(trafficStatus.equals("Heavy") ? Color.parseColor("#FF3B30") :
                        trafficStatus.equals("Moderate") ? Color.parseColor("#FFB300") :
                                Color.parseColor("#4CAF50"));
            }
            int index = i;
            routeView.setOnClickListener(v -> {
                selectedRouteIndex = index;
                updateMap();
                cachedGasStations = null;
                cachedHospitals = null;
                fetchNearbyData(false);
                if (isNavigating) {
                    stopNavigation();
                    startNavigation();
                }
                Toast.makeText(this, "Selected " + roadName, Toast.LENGTH_SHORT).show();
            });
            routesList.addView(routeView);
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        Switch notificationSwitch = new Switch(this);
        notificationSwitch.setText("Enable Notifications");
        notificationSwitch.setChecked(prefs.getBoolean("notifications_enabled", true));
        notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
            Toast.makeText(this, "Notifications " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
        layout.addView(notificationSwitch);
        builder.setView(layout);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void populateWaypoints() {
        stationsList.removeAllViews();
        if (petrolBunks == null || petrolBunks.isEmpty()) {
            TextView noStations = new TextView(this);
            noStations.setText("No petrol stations found");
            noStations.setTextSize(14);
            noStations.setTextColor(Color.parseColor("#555555"));
            noStations.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
            stationsList.addView(noStations);
        } else {
            for (String bunk : petrolBunks) {
                if (bunk.startsWith("No ") || bunk.startsWith("Failed")) continue;
                String[] parts = bunk.split("\\|");
                if (parts.length < 5) {
                    Log.e(TAG, "Invalid petrol bunk format: " + bunk);
                    continue;
                }
                View stationView = LayoutInflater.from(this).inflate(R.layout.petrol_bunk_item_home2, stationsList, false);
                TextView nameText = stationView.findViewById(R.id.name_text);
                TextView vicinityText = stationView.findViewById(R.id.vicinity_text);
                TextView distanceTextView = stationView.findViewById(R.id.distance_text);
                TextView statusText = stationView.findViewById(R.id.status_text);
                if (nameText != null) nameText.setText(parts[0]);
                if (vicinityText != null) vicinityText.setText(parts[1]);
                if (distanceTextView != null) distanceTextView.setText(parts[2]);
                if (statusText != null) {
                    statusText.setText(parts[4]);
                    int color = parts[4].equals("Open") ? Color.parseColor("#4CAF50") :
                            parts[4].equals("Closed") ? Color.parseColor("#FF3B30") :
                                    Color.parseColor("#FFB300"); // Unknown
                    statusText.setTextColor(color);
                }
                LatLng stopLatLng = new LatLng(
                        Double.parseDouble(parts[3].split(",")[0].replace("(", "")),
                        Double.parseDouble(parts[3].split(",")[1].replace(")", ""))
                );
                stationView.setOnClickListener(v -> {
                    addWaypointStop(parts[0], stopLatLng);
                    scrollToMap();
                });
                stationsList.addView(stationView);
            }
        }

        hospitalsList.removeAllViews();
        if (hospitals == null || hospitals.isEmpty()) {
            TextView noHospitals = new TextView(this);
            noHospitals.setText("No hospitals found");
            noHospitals.setTextSize(14);
            noHospitals.setTextColor(Color.parseColor("#555555"));
            noHospitals.setTypeface(Typeface.create("poppins_regular", Typeface.NORMAL));
            hospitalsList.addView(noHospitals);
        } else {
            for (String hospital : hospitals) {
                if (hospital.startsWith("No ") || hospital.startsWith("Failed")) continue;
                String[] parts = hospital.split("\\|");
                if (parts.length < 5) {
                    Log.e(TAG, "Invalid hospital format: " + hospital);
                    continue;
                }
                View hospitalView = LayoutInflater.from(this).inflate(R.layout.petrol_bunk_item_home2, hospitalsList, false);
                TextView nameText = hospitalView.findViewById(R.id.name_text);
                TextView vicinityText = hospitalView.findViewById(R.id.vicinity_text);
                TextView distanceTextView = hospitalView.findViewById(R.id.distance_text);
                TextView statusText = hospitalView.findViewById(R.id.status_text);
                if (nameText != null) nameText.setText(parts[0]);
                if (vicinityText != null) vicinityText.setText(parts[1]);
                if (distanceTextView != null) distanceTextView.setText(parts[2]);
                if (statusText != null) {
                    statusText.setText(parts[4]);
                    int color = parts[4].equals("Open") ? Color.parseColor("#4CAF50") :
                            parts[4].equals("Closed") ? Color.parseColor("#FF3B30") :
                                    Color.parseColor("#FFB300"); // Unknown
                    statusText.setTextColor(color);
                }
                LatLng stopLatLng = new LatLng(
                        Double.parseDouble(parts[3].split(",")[0].replace("(", "")),
                        Double.parseDouble(parts[3].split(",")[1].replace(")", ""))
                );
                hospitalView.setOnClickListener(v -> {
                    addWaypointStop(parts[0], stopLatLng);
                    scrollToMap();
                });
                hospitalsList.addView(hospitalView);
            }
        }
    }

    private void scrollToMap() {
        if (scrollView != null && mapView != null) {
            scrollView.post(() -> {
                int mapTop = mapView.getTop();
                // Adjust for the alert card height or scroll slightly down to position map at top
                // Assuming alert card is about 120dp, scroll to ~120 to have map start at top
                // But for simplicity, scroll to 0 to show from top content
                scrollView.smoothScrollTo(0, 0);
                // To precisely position map at viewport top: scrollView.smoothScrollTo(0, mapTop - getStatusBarHeight() - topBarHeight);
            });
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private void addWaypointStop(String name, LatLng stopLatLng) {
        if (googleMap == null) return;

        CameraPosition currentCameraPosition = googleMap.getCameraPosition();
        waypointName = name;
        waypointLatLng = stopLatLng;

        googleMap.addMarker(new MarkerOptions().position(stopLatLng).title(name));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(stopLatLng, 15), 1000, null);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            executorService.execute(() -> {
                try {
                    DirectionsResult newResult = DirectionsApi.newRequest(geoApiContext)
                            .origin(new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude))
                            .destination(new com.google.maps.model.LatLng(destinationLatLng.latitude, destinationLatLng.longitude))
                            .waypoints(new com.google.maps.model.LatLng(stopLatLng.latitude, stopLatLng.longitude))
                            .mode(TravelMode.DRIVING)
                            .alternatives(true)
                            .await();
                    runOnUiThread(() -> {
                        directionsResult = newResult;
                        selectedRouteIndex = 0;
                        cachedGasStations = null;
                        cachedHospitals = null;
                        populateRoutes();
                        fetchNearbyData(false);
                        updateMap();
                        boolean wasNavigating = isNavigating;
                        if (wasNavigating) {
                            stopNavigation();
                        }
                        startNavigation();
                        scrollToMap();
                        Toast.makeText(this, "Added stop: " + name, Toast.LENGTH_SHORT).show();

                        if (!wasNavigating && currentCameraPosition != null) {
                            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(currentCameraPosition), 1000, null);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to add stop", Toast.LENGTH_SHORT).show();
                        waypointLatLng = null;
                        waypointName = null;
                        if (googleMap != null) {
                            googleMap.clear();
                            updateMap();
                        }
                        scrollToMap();
                    });
                    Log.e(TAG, "Error adding stop: " + e.getMessage());
                }
            });
        }, 3000);
    }

    private void updateMap() {
        if (googleMap == null || directionsResult == null || directionsResult.routes == null || selectedRouteIndex >= directionsResult.routes.length) {
            Log.e(TAG, "Cannot update map: googleMap=" + googleMap + ", directionsResult=" + directionsResult + ", selectedRouteIndex=" + selectedRouteIndex);
            return;
        }
        googleMap.clear();
        googleMap.addMarker(new MarkerOptions().position(currentLatLng).title("Start: " + currentAddress));
        googleMap.addMarker(new MarkerOptions().position(destinationLatLng).title("End: " + destinationAddress));
        DirectionsRoute route = directionsResult.routes[selectedRouteIndex];
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.parseColor("#2979FF"))
                .width(10);
        for (com.google.maps.model.LatLng point : route.overviewPolyline.decodePath()) {
            polylineOptions.add(new LatLng(point.lat, point.lng));
        }
        googleMap.addPolyline(polylineOptions);
        if (waypointLatLng != null && waypointName != null) {
            googleMap.addMarker(new MarkerOptions().position(waypointLatLng).title(waypointName));
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                new LatLngBounds.Builder()
                        .include(currentLatLng)
                        .include(destinationLatLng)
                        .build(), 100));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchNearbyData(false);
            if (googleMap != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
            }
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
        if (navigationHandler != null && navigationRunnable != null) {
            navigationHandler.removeCallbacks(navigationRunnable);
        }
        if (geoApiContext != null) {
            executorService.execute(() -> {
                try {
                    geoApiContext.shutdown();
                } catch (Exception e) {
                    Log.e(TAG, "Error shutting down GeoApiContext: " + e.getMessage());
                }
            });
            try {
                executorService.shutdown();
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "ExecutorService shutdown interrupted: " + e.getMessage());
            } finally {
                executorService.shutdownNow();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle mapState = new Bundle();
            mapView.onSaveInstanceState(mapState);
            outState.putBundle("mapView", mapState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
}