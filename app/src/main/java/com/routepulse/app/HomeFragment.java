package com.routepulse.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlacesApi;
import com.google.maps.model.AddressType;
import com.google.maps.model.AutocompletePrediction;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import com.google.maps.model.TravelMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private MapView mapView;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private GeoApiContext geoApiContext;
    private EditText currentLocationText, destinationText;
    private LinearLayout currentLocationLayout, recenterButton, layersButton, gasButton, emergencyButton;
    private LinearLayout skeletonLoader;
    private TextView noInternetMessage;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ViewPagerAdapter adapter;
    private LatLng currentLatLng, lastCachedLatLng;
    private boolean isMapInitialized, isLocationEnabled, isPermissionLayoutShown, isLocationEnabledChecked;
    private ListPopupWindow suggestionsPopup;
    private ArrayAdapter<String> suggestionsAdapter;
    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    private MutableLiveData<List<String>> trafficAlertsLiveData = new MutableLiveData<>();
    private MutableLiveData<List<String>> gasStationsLiveData = new MutableLiveData<>(); // CNG
    private MutableLiveData<List<String>> petrolStationsLiveData = new MutableLiveData<>();
    private MutableLiveData<List<String>> gasStations2LiveData = new MutableLiveData<>(); // Gas
    private MutableLiveData<List<String>> dieselStationsLiveData = new MutableLiveData<>();
    private Bundle savedMapState;
    private List<String> cachedTrafficAlerts = new ArrayList<>();
    private List<String> cachedGasStations = new ArrayList<>(); // CNG
    private List<String> cachedPetrolStations = new ArrayList<>();
    private List<String> cachedGasStations2 = new ArrayList<>(); // Gas
    private List<String> cachedDieselStations = new ArrayList<>();
    private static final String KEY_TRAFFIC = "cached_traffic";
    private static final String KEY_CNG = "cached_cng";
    private static final String KEY_PETROL = "cached_petrol";
    private static final String KEY_GAS = "cached_gas";
    private static final String KEY_DIESEL = "cached_diesel";
    private static final String KEY_LAST_LATLNG = "last_latlng";
    private static final float CACHE_DISTANCE_THRESHOLD = 500; // meters
    private boolean isNavigating = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            geoApiContext = new GeoApiContext.Builder()
                    .apiKey(getString(R.string.google_maps_key))
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build();
            Log.d(TAG, "GeoApiContext initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize GeoApiContext: " + e.getMessage());
            Toast.makeText(requireContext(), "API initialization failed", Toast.LENGTH_LONG).show();
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        if (savedInstanceState != null) {
            savedMapState = savedInstanceState.getBundle("mapView");
            ArrayList<String> savedTraffic = savedInstanceState.getStringArrayList(KEY_TRAFFIC);
            ArrayList<String> savedCng = savedInstanceState.getStringArrayList(KEY_CNG);
            ArrayList<String> savedPetrol = savedInstanceState.getStringArrayList(KEY_PETROL);
            ArrayList<String> savedGas = savedInstanceState.getStringArrayList(KEY_GAS);
            ArrayList<String> savedDiesel = savedInstanceState.getStringArrayList(KEY_DIESEL);
            double[] lastLatLng = savedInstanceState.getDoubleArray(KEY_LAST_LATLNG);
            if (savedTraffic != null) {
                cachedTrafficAlerts.addAll(savedTraffic);
                trafficAlertsLiveData.setValue(cachedTrafficAlerts);
                Log.d(TAG, "Restored cached traffic: " + cachedTrafficAlerts.size() + " items");
            }
            if (savedCng != null) {
                cachedGasStations.addAll(savedCng);
                gasStationsLiveData.setValue(cachedGasStations);
                Log.d(TAG, "Restored cached CNG: " + cachedGasStations.size() + " items");
            }
            if (savedPetrol != null) {
                cachedPetrolStations.addAll(savedPetrol);
                petrolStationsLiveData.setValue(cachedPetrolStations);
                Log.d(TAG, "Restored cached petrol: " + cachedPetrolStations.size() + " items");
            }
            if (savedGas != null) {
                cachedGasStations2.addAll(savedGas);
                gasStations2LiveData.setValue(cachedGasStations2);
                Log.d(TAG, "Restored cached gas: " + cachedGasStations2.size() + " items");
            }
            if (savedDiesel != null) {
                cachedDieselStations.addAll(savedDiesel);
                dieselStationsLiveData.setValue(cachedDieselStations);
                Log.d(TAG, "Restored cached diesel: " + cachedDieselStations.size() + " items");
            }
            if (lastLatLng != null && lastLatLng.length == 2) {
                lastCachedLatLng = new LatLng(lastLatLng[0], lastLatLng[1]);
                Log.d(TAG, "Restored last cached LatLng: " + lastCachedLatLng);
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        checkLocationServices();
        boolean hasLocationPermission = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!isLocationEnabled || !hasLocationPermission) {
            View view = inflater.inflate(R.layout.activity_location_access, container, false);
            isPermissionLayoutShown = true;
            setupLocationPermissionView(view);
            return view;
        }

        isPermissionLayoutShown = false;
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize UI components
        mapView = view.findViewById(R.id.map_view);
        currentLocationText = view.findViewById(R.id.current_location_text);
        destinationText = view.findViewById(R.id.destination_text);
        currentLocationLayout = view.findViewById(R.id.current_location);
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);
        skeletonLoader = view.findViewById(R.id.skeleton_loader);
        recenterButton = view.findViewById(R.id.recenter_button);
        layersButton = view.findViewById(R.id.layers_button);
        gasButton = view.findViewById(R.id.gas_button);
        emergencyButton = view.findViewById(R.id.emergency_button);
        noInternetMessage = view.findViewById(R.id.no_internet_message);

        // Initialize suggestions popup
        suggestionsAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line);
        suggestionsPopup = new ListPopupWindow(requireContext());
        suggestionsPopup.setAdapter(suggestionsAdapter);
        suggestionsPopup.setWidth(ListPopupWindow.MATCH_PARENT);
        suggestionsPopup.setHeight(ListPopupWindow.WRAP_CONTENT);

        // Setup ViewPager
        adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(4);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Traffic Alerts");
                    break;
                case 1:
                    tab.setText("CNG Stations");
                    break;
                case 2:
                    tab.setText("Petrol Stations");
                    break;
                case 3:
                    tab.setText("Gas Stations");
                    break;
                case 4:
                    tab.setText("Diesel Stations");
                    break;
            }
        }).attach();

        // Show skeleton loader initially
        skeletonLoader.setVisibility(View.VISIBLE);
        mapView.setVisibility(View.GONE);
        noInternetMessage.setVisibility(isNetworkAvailable() ? View.GONE : View.VISIBLE);

        // Initialize map asynchronously
        mapView.onCreate(savedMapState != null ? savedMapState : savedInstanceState);
        initMap();

        // Setup click listeners
        currentLocationLayout.setOnClickListener(v -> currentLocationText.performClick());
        recenterButton.setOnClickListener(v -> getCurrentLocation(true));
        layersButton.setOnClickListener(v -> toggleMapType());
        gasButton.setOnClickListener(v -> searchGasStations());
        emergencyButton.setOnClickListener(v -> callEmergency());

        // Initialize clear buttons
        ImageView clearCurrentLocation = view.findViewById(R.id.clear_current_location);
        ImageView clearDestination = view.findViewById(R.id.clear_destination);
        setupAddressSuggestions(currentLocationText, true, clearCurrentLocation);
        setupAddressSuggestions(destinationText, false, clearDestination);

        // Load cached data immediately if available
        if (!cachedTrafficAlerts.isEmpty() && !cachedGasStations.isEmpty() && !cachedPetrolStations.isEmpty() &&
                !cachedGasStations2.isEmpty() && !cachedDieselStations.isEmpty() && lastCachedLatLng != null) {
            trafficAlertsLiveData.setValue(cachedTrafficAlerts);
            gasStationsLiveData.setValue(cachedGasStations);
            petrolStationsLiveData.setValue(cachedPetrolStations);
            gasStations2LiveData.setValue(cachedGasStations2);
            dieselStationsLiveData.setValue(cachedDieselStations);
            currentLatLng = lastCachedLatLng;
            updateMapAndFragments();
        } else {
            getCurrentLocation(false);
        }

        return view;
    }

    private void checkLocationServices() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLocationEnabled = locationManager.isLocationEnabled();
            } else {
                isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking location services: " + e.getMessage());
            isLocationEnabled = false;
        }
        Log.d(TAG, "checkLocationServices: isLocationEnabled=" + isLocationEnabled);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            checkLocationServices();
            boolean hasLocationPermission = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "onActivityResult: isLocationEnabled=" + isLocationEnabled + ", hasLocationPermission=" + hasLocationPermission);
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commitAllowingStateLoss();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void setupLocationPermissionView(View view) {
        View allowButton = view.findViewById(R.id.button_allow);
        View settingsButton = view.findViewById(R.id.button_settings);
        if (allowButton == null || settingsButton == null) {
            Log.e(TAG, "Permission layout error: allowButton=" + allowButton + ", settingsButton=" + settingsButton);
            Toast.makeText(requireContext(), "Permission layout error: Missing buttons", Toast.LENGTH_LONG).show();
            return;
        }
        boolean hasLocationPermission = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        allowButton.setVisibility(hasLocationPermission ? View.GONE : View.VISIBLE);
        allowButton.setOnClickListener(v -> {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        });
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(intent, LOCATION_PERMISSION_REQUEST_CODE);
        });
    }

    private void initMap() {
        if (mapView == null) return;
        mapView.getMapAsync(map -> {
            if (!isAdded()) return;
            googleMap = map;
            if (googleMap == null) {
                skeletonLoader.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Failed to initialize map", Toast.LENGTH_SHORT).show();
                return;
            }
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            isMapInitialized = true;

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
                getCurrentLocation(false);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            }
        });
    }

    private void getCurrentLocation(boolean forceRefresh) {
        if (!isAdded() || !isMapInitialized || !isLocationEnabled) return;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        if (!forceRefresh && lastCachedLatLng != null && !cachedTrafficAlerts.isEmpty() && !cachedGasStations.isEmpty() &&
                !cachedPetrolStations.isEmpty() && !cachedGasStations2.isEmpty() && !cachedDieselStations.isEmpty()) {
            currentLatLng = lastCachedLatLng;
            currentLocationText.setText("Current Location");
            updateMapAndFragments();
            return;
        }

        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setInterval(100);
        fusedLocationClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                fusedLocationClient.removeLocationUpdates(this);
                if (!isAdded()) return;
                if (result.getLastLocation() != null) {
                    currentLatLng = new LatLng(result.getLastLocation().getLatitude(), result.getLastLocation().getLongitude());
                    executorService.execute(() -> {
                        try {
                            String address = "Current Location";
                            if (forceRefresh || shouldFetchData()) {
                                GeocodingResult[] results = GeocodingApi.reverseGeocode(geoApiContext,
                                        new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude)).await();
                                address = results.length > 0 ? results[0].formattedAddress : address;
                            }
                            String finalAddress = address;
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                currentLocationText.setText(finalAddress);
                                if (forceRefresh || shouldFetchData()) {
                                    fetchNearbyData();
                                } else {
                                    trafficAlertsLiveData.setValue(cachedTrafficAlerts);
                                    gasStationsLiveData.setValue(cachedGasStations);
                                    petrolStationsLiveData.setValue(cachedPetrolStations);
                                    gasStations2LiveData.setValue(cachedGasStations2);
                                    dieselStationsLiveData.setValue(cachedDieselStations);
                                    updateMapAndFragments();
                                }
                            });
                        } catch (Exception e) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                currentLocationText.setText("Current Location");
                                if (forceRefresh || shouldFetchData()) {
                                    fetchNearbyData();
                                } else {
                                    trafficAlertsLiveData.setValue(cachedTrafficAlerts);
                                    gasStationsLiveData.setValue(cachedGasStations);
                                    petrolStationsLiveData.setValue(cachedPetrolStations);
                                    gasStations2LiveData.setValue(cachedGasStations2);
                                    dieselStationsLiveData.setValue(cachedDieselStations);
                                    updateMapAndFragments();
                                }
                            });
                        }
                    });
                } else {
                    currentLatLng = lastCachedLatLng != null ? lastCachedLatLng : new LatLng(13.0478, 80.0521); // Fallback: Chennai
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        currentLocationText.setText(lastCachedLatLng != null ? "Cached Location" : "Chennai, India");
                        if (forceRefresh || shouldFetchData()) {
                            fetchNearbyData();
                        } else {
                            trafficAlertsLiveData.setValue(cachedTrafficAlerts);
                            gasStationsLiveData.setValue(cachedGasStations);
                            petrolStationsLiveData.setValue(cachedPetrolStations);
                            gasStations2LiveData.setValue(cachedGasStations2);
                            dieselStationsLiveData.setValue(cachedDieselStations);
                            updateMapAndFragments();
                        }
                    });
                }
            }
        }, Looper.getMainLooper());
    }

    private boolean shouldFetchData() {
        if (cachedTrafficAlerts.isEmpty() || cachedGasStations.isEmpty() || cachedPetrolStations.isEmpty() ||
                cachedGasStations2.isEmpty() || cachedDieselStations.isEmpty() || lastCachedLatLng == null) {
            return true;
        }
        float[] distance = new float[1];
        android.location.Location.distanceBetween(
                currentLatLng.latitude, currentLatLng.longitude,
                lastCachedLatLng.latitude, lastCachedLatLng.longitude,
                distance);
        boolean shouldFetch = distance[0] > CACHE_DISTANCE_THRESHOLD;
        Log.d(TAG, "Distance from last cached location: " + distance[0] + "m, shouldFetch=" + shouldFetch);
        return shouldFetch;
    }

    private void fetchNearbyData() {
        if (!isAdded() || currentLatLng == null) return;

        if (!shouldFetchData()) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                trafficAlertsLiveData.setValue(cachedTrafficAlerts);
                gasStationsLiveData.setValue(cachedGasStations);
                petrolStationsLiveData.setValue(cachedPetrolStations);
                gasStations2LiveData.setValue(cachedGasStations2);
                dieselStationsLiveData.setValue(cachedDieselStations);
                updateMapAndFragments();
            });
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Runnable> tasks = new ArrayList<>();

        // Fetch traffic data using nearby junctions and roads
        tasks.add(() -> {
            List<String> newTrafficAlerts = new ArrayList<>();
            boolean hasValidData = false;
            try {
                String[] keywords = {"junction", "road"};
                List<com.google.maps.model.LatLng> destinations = new ArrayList<>();
                for (String keyword : keywords) {
                    PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext,
                                    new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude))
                            .radius(4000)
                            .keyword(keyword)
                            .await();
                    if (response.results != null && response.results.length > 0) {
                        for (PlacesSearchResult result : response.results) {
                            if (result.geometry != null && result.geometry.location != null) {
                                destinations.add(result.geometry.location);
                            }
                        }
                    }
                }
                if (destinations.isEmpty()) {
                    destinations.addAll(Arrays.asList(
                            new com.google.maps.model.LatLng(currentLatLng.latitude + 0.02, currentLatLng.longitude), // North
                            new com.google.maps.model.LatLng(currentLatLng.latitude - 0.02, currentLatLng.longitude), // South
                            new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude + 0.02), // East
                            new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude - 0.02)  // West
                    ));
                }

                for (com.google.maps.model.LatLng dest : destinations) {
                    try {
                        DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                                .origin(new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude))
                                .destination(dest)
                                .mode(TravelMode.DRIVING)
                                .trafficModel(com.google.maps.model.TrafficModel.BEST_GUESS)
                                .await();
                        if (result != null && result.routes != null && result.routes.length > 0) {
                            for (DirectionsRoute route : result.routes) {
                                String trafficStatus = "Clear";
                                String delay = "0 min delay";
                                String distance = "0 mi";
                                String roadName = route.summary != null && !route.summary.isEmpty() ? route.summary : "Nearby Road";
                                if (route.legs != null && route.legs.length > 0) {
                                    long durationInTraffic = route.legs[0].durationInTraffic != null ?
                                            route.legs[0].durationInTraffic.inSeconds : route.legs[0].duration.inSeconds;
                                    long normalDuration = route.legs[0].duration.inSeconds;
                                    long delaySeconds = durationInTraffic - normalDuration;
                                    if (delaySeconds > 600) trafficStatus = "Heavy";
                                    else if (delaySeconds > 300) trafficStatus = "Moderate";
                                    delay = String.format("%d min delay", delaySeconds / 60);
                                    distance = String.format("%.1f mi", route.legs[0].distance.inMeters / 1609.34);
                                    double lat = route.legs[0].endLocation.lat;
                                    double lng = route.legs[0].endLocation.lng;
                                    newTrafficAlerts.add(String.format("%s on %s|%s|%s|%.4f,%.4f",
                                            trafficStatus, roadName, distance, delay, lat, lng));
                                    hasValidData = true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Traffic data fetch error for destination " + dest + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Nearby roads fetch error: " + e.getMessage());
            }
            if (!hasValidData) {
                newTrafficAlerts.add("Clear flow nearby");
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                cachedTrafficAlerts.clear();
                cachedTrafficAlerts.addAll(newTrafficAlerts);
                trafficAlertsLiveData.setValue(cachedTrafficAlerts);
                lastCachedLatLng = currentLatLng;
                updateMapAndFragments();
            });
        });

        // Fetch fuel stations for each type with refined keywords
        String[] fuelTypes = {"CNG", "Petrol", "LPG", "Diesel"};
        String[] fuelKeywords = {
                "CNG station",
                "petrol pump",
                "LPG station",
                "diesel fuel station"
        };
        for (int i = 0; i < fuelTypes.length; i++) {
            final String fuelType = fuelTypes[i];
            final String keyword = fuelKeywords[i];
            tasks.add(() -> {
                try {
                    PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext,
                                    new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude))
                            .radius(5000)
                            .keyword(keyword)
                            .type(PlaceType.GAS_STATION)
                            .await();
                    List<String> newFuelStations = new ArrayList<>();
                    if (response.results != null && response.results.length > 0) {
                        for (PlacesSearchResult result : response.results) {
                            String name = result.name != null ? result.name.toLowerCase() : "";
                            String keywordBase = keyword.toLowerCase().replace(" station", "").replace(" pump", "").trim();
                            // Include stations that match the current fuel type or keyword
                            boolean matchesFuelType = name.contains(fuelType.toLowerCase()) || name.contains(keywordBase)
                                    || (fuelType.equalsIgnoreCase("LPG") && name.contains("gas"));
                            // Allow stations with ambiguous names if they strongly match the current fuel
                            boolean isPrimaryMatch = name.contains(fuelType.toLowerCase()) || name.contains(keywordBase);
                            // Exclude stations that strongly match other fuel types
                            boolean matchesOtherFuel = false;
                            for (String otherFuel : fuelTypes) {
                                if (!otherFuel.equalsIgnoreCase(fuelType)) {
                                    String otherKeyword = fuelKeywords[Arrays.asList(fuelTypes).indexOf(otherFuel)]
                                            .toLowerCase().replace(" station", "").replace(" pump", "").trim();
                                    if (name.contains(otherFuel.toLowerCase()) || name.contains(otherKeyword)
                                            || (otherFuel.equalsIgnoreCase("LPG") && name.contains("gas"))) {
                                        if (!isPrimaryMatch) {
                                            matchesOtherFuel = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!matchesFuelType || matchesOtherFuel) {
                                Log.d(TAG, "Skipping station for " + fuelType + ": " + name + " (matchesFuelType=" + matchesFuelType + ", matchesOtherFuel=" + matchesOtherFuel + ")");
                                continue;
                            }
                            String stationName = result.name != null ? result.name : "Unknown " + fuelType + " Station";
                            String vicinity = result.vicinity != null ? result.vicinity : "Unknown";
                            String coords = String.format("(%.4f, %.4f)", result.geometry.location.lat, result.geometry.location.lng);
                            double distance = calculateDistance(currentLatLng.latitude, currentLatLng.longitude,
                                    result.geometry.location.lat, result.geometry.location.lng);
                            String status = result.openingHours != null && result.openingHours.openNow ? "Open" : "Closed";
                            newFuelStations.add(String.format("%s|%s|%.1f mi|%s|%s|%s", stationName, vicinity, distance, coords, status, fuelType));
                            Log.d(TAG, "Added station for " + fuelType + ": " + stationName);
                        }
                    }
                    if (newFuelStations.isEmpty()) {
                        newFuelStations.add("No " + fuelType + " stations found");
                        Log.d(TAG, "No stations found for " + fuelType);
                    }
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        List<String> fuelList = new ArrayList<>(newFuelStations);
                        switch (fuelType.toLowerCase()) {
                            case "cng":
                                cachedGasStations.clear();
                                cachedGasStations.addAll(fuelList);
                                gasStationsLiveData.setValue(cachedGasStations);
                                Log.d(TAG, "Updated CNG stations: " + fuelList.size() + " entries");
                                break;
                            case "petrol":
                                cachedPetrolStations.clear();
                                cachedPetrolStations.addAll(fuelList);
                                petrolStationsLiveData.setValue(cachedPetrolStations);
                                Log.d(TAG, "Updated Petrol stations: " + fuelList.size() + " entries");
                                break;
                            case "lpg":
                                cachedGasStations2.clear();
                                cachedGasStations2.addAll(fuelList);
                                gasStations2LiveData.setValue(cachedGasStations2);
                                Log.d(TAG, "Updated LPG stations: " + fuelList.size() + " entries");
                                break;
                            case "diesel":
                                cachedDieselStations.clear();
                                cachedDieselStations.addAll(fuelList);
                                dieselStationsLiveData.setValue(cachedDieselStations);
                                Log.d(TAG, "Updated Diesel stations: " + fuelList.size() + " entries");
                                break;
                        }
                        lastCachedLatLng = currentLatLng;
                        updateMapAndFragments();
                    });
                } catch (Exception e) {
                    String finalFuelType = fuelType;
                    Log.e(TAG, "Error fetching " + finalFuelType + " stations: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        List<String> errorList = new ArrayList<>();
                        errorList.add("No " + finalFuelType + " stations Found");
                        switch (finalFuelType.toLowerCase()) {
                            case "cng":
                                cachedGasStations.clear();
                                cachedGasStations.addAll(errorList);
                                gasStationsLiveData.setValue(cachedGasStations);
                                break;
                            case "petrol":
                                cachedPetrolStations.clear();
                                cachedPetrolStations.addAll(errorList);
                                petrolStationsLiveData.setValue(cachedPetrolStations);
                                break;
                            case "lpg":
                                cachedGasStations2.clear();
                                cachedGasStations2.addAll(errorList);
                                gasStations2LiveData.setValue(cachedGasStations2);
                                break;
                            case "diesel":
                                cachedDieselStations.clear();
                                cachedDieselStations.addAll(errorList);
                                dieselStationsLiveData.setValue(cachedDieselStations);
                                break;
                        }
                        lastCachedLatLng = currentLatLng;
                        updateMapAndFragments();
                    });
                }
            });
        }

        for (Runnable task : tasks) {
            executor.execute(task);
        }
        executor.shutdown();
    }


    private void searchGasStations() {
        if (!isAdded() || currentLatLng == null) {
            Toast.makeText(requireContext(), "Current location not available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        googleMap.clear();
        googleMap.addMarker(new MarkerOptions()
                .position(currentLatLng)
                .title("Current Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        String[] fuelTypes = {"CNG", "petrol", "gas", "diesel"};
        float[] hues = {
                BitmapDescriptorFactory.HUE_GREEN,  // CNG
                BitmapDescriptorFactory.HUE_RED,    // Petrol
                BitmapDescriptorFactory.HUE_YELLOW, // Gas
                BitmapDescriptorFactory.HUE_CYAN    // Diesel
        };
        List<List<String>> fuelStations = Arrays.asList(
                gasStationsLiveData.getValue(),
                petrolStationsLiveData.getValue(),
                gasStations2LiveData.getValue(),
                dieselStationsLiveData.getValue()
        );

        for (int i = 0; i < fuelStations.size(); i++) {
            List<String> stations = fuelStations.get(i);
            if (stations != null && !stations.isEmpty()) {
                for (String station : stations) {
                    if (station.contains("No ") || station.contains("Failed")) continue;
                    try {
                        String[] parts = station.split("\\|");
                        String[] coords = parts[3].replace("(", "").replace(")", "").split(",");
                        double lat = Double.parseDouble(coords[0].trim());
                        double lng = Double.parseDouble(coords[1].trim());
                        googleMap.addMarker(new MarkerOptions()
                                .position(new LatLng(lat, lng))
                                .title(parts[0] + " (" + fuelTypes[i] + ")")
                                .snippet(parts[1] + " - " + parts[2] + " - " + parts[4])
                                .icon(BitmapDescriptorFactory.defaultMarker(hues[i])));
                    } catch (Exception e) {
                        Log.e(TAG, "Error adding marker for " + fuelTypes[i] + ": " + e.getMessage());
                    }
                }
            }
        }

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 13)); // Adjusted zoom to show 5km radius
        Toast.makeText(requireContext(), "Showing all fuel stations within 5km", Toast.LENGTH_SHORT).show();
    }

    private void updateMapAndFragments() {
        if (!isAdded() || googleMap == null || currentLatLng == null) {
            skeletonLoader.setVisibility(View.GONE);
            mapView.setVisibility(View.VISIBLE);
            noInternetMessage.setVisibility(isNetworkAvailable() ? View.GONE : View.VISIBLE);
            return;
        }

        googleMap.clear();
        googleMap.addMarker(new MarkerOptions()
                .position(currentLatLng)
                .title("Current Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        // Add fuel station markers with distinct colors
        List<List<String>> fuelStations = Arrays.asList(
                gasStationsLiveData.getValue(),
                petrolStationsLiveData.getValue(),
                gasStations2LiveData.getValue(),
                dieselStationsLiveData.getValue()
        );
        String[] fuelTypes = {"CNG", "Petrol", "Gas", "Diesel"};
        float[] hues = {
                BitmapDescriptorFactory.HUE_GREEN,  // CNG
                BitmapDescriptorFactory.HUE_RED,    // Petrol
                BitmapDescriptorFactory.HUE_YELLOW, // Gas
                BitmapDescriptorFactory.HUE_CYAN    // Diesel
        };

        for (int i = 0; i < fuelStations.size(); i++) {
            List<String> stations = fuelStations.get(i);
            if (stations != null && !stations.isEmpty()) {
                for (String station : stations) {
                    if (station.contains("No ") || station.contains("Failed")) continue;
                    try {
                        String[] parts = station.split("\\|");
                        String[] coords = parts[3].replace("(", "").replace(")", "").split(",");
                        double lat = Double.parseDouble(coords[0].trim());
                        double lng = Double.parseDouble(coords[1].trim());
                        googleMap.addMarker(new MarkerOptions()
                                .position(new LatLng(lat, lng))
                                .title(parts[0] + " (" + fuelTypes[i] + ")")
                                .snippet(parts[1] + " - " + parts[2] + " - " + parts[4])
                                .icon(BitmapDescriptorFactory.defaultMarker(hues[i])));
                    } catch (Exception e) {
                        Log.e(TAG, "Error adding marker for " + fuelTypes[i] + ": " + e.getMessage());
                    }
                }
            }
        }

        // Add traffic polylines
        List<String> trafficAlerts = trafficAlertsLiveData.getValue();
        if (trafficAlerts != null && !trafficAlerts.isEmpty()) {
            for (String traffic : trafficAlerts) {
                if (traffic.contains("No traffic") || traffic.contains("Clear traffic")) continue;
                try {
                    String[] parts = traffic.split("\\|");
                    String[] coords = parts[3].split(",");
                    double lat = Double.parseDouble(coords[0].trim());
                    double lng = Double.parseDouble(coords[1].trim());
                    String status = parts[0].split(" ")[0];
                    int color = status.equals("Heavy") ? Color.RED :
                            status.equals("Moderate") ? Color.YELLOW :
                                    status.equals("Clear") ? Color.GREEN : Color.GRAY;
                    googleMap.addPolyline(new PolylineOptions()
                            .add(currentLatLng, new LatLng(lat, lng))
                            .color(color)
                            .width(10));
                } catch (Exception e) {
                    Log.e(TAG, "Error adding polyline: " + e.getMessage());
                }
            }
        }

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
        skeletonLoader.setVisibility(View.GONE);
        mapView.setVisibility(View.VISIBLE);
        noInternetMessage.setVisibility(isNetworkAvailable() ? View.GONE : View.VISIBLE);
    }

    public MutableLiveData<List<String>> getTrafficAlertsLiveData() {
        return trafficAlertsLiveData;
    }

    public MutableLiveData<List<String>> getGasStationsLiveData() {
        return gasStationsLiveData;
    }

    public MutableLiveData<List<String>> getPetrolStationsLiveData() {
        return petrolStationsLiveData;
    }

    public MutableLiveData<List<String>> getGasStations2LiveData() {
        return gasStations2LiveData;
    }

    public MutableLiveData<List<String>> getDieselStationsLiveData() {
        return dieselStationsLiveData;
    }

    private void setupAddressSuggestions(EditText editText, boolean isCurrentLocation, ImageView clearButton) {
        updatePlaceholder(editText, isCurrentLocation);
        editText.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show();
                return;
            }
            editText.requestFocus();
            editText.setTag("active");
            clearButton.setVisibility(editText.getText().length() > 0 ? View.VISIBLE : View.GONE);
            fetchSuggestions(editText, isCurrentLocation);
            Log.d(TAG, "EditText clicked: " + (isCurrentLocation ? "current" : "destination") + ", clear button visibility: " + clearButton.getVisibility());
        });

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (!isNetworkAvailable()) {
                    Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show();
                    return;
                }
                editText.setTag("active");
                clearButton.setVisibility(editText.getText().length() > 0 ? View.VISIBLE : View.GONE);
                fetchSuggestions(editText, isCurrentLocation);
            } else {
                if (editText.getText().length() == 0) {
                    updatePlaceholder(editText, isCurrentLocation);
                    editText.setTag(null);
                    clearButton.setVisibility(View.GONE);
                }
            }
            Log.d(TAG, (isCurrentLocation ? "Current" : "Destination") + " focus: " + hasFocus);
        });

        editText.addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private Runnable searchRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 && editText.getTag() != null ? View.VISIBLE : View.GONE);
                if (!"active".equals(editText.getTag())) {
                    Log.d(TAG, "Skipping suggestions: " + (isCurrentLocation ? "current" : "destination") + " is fixed or not active");
                    return;
                }

                if (searchRunnable != null) {
                    handler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (s.length() == 0) {
                        updatePlaceholder(editText, isCurrentLocation);
                        fetchRecommendations(editText, isCurrentLocation);
                    } else {
                        fetchSuggestions(editText, isCurrentLocation);
                    }
                };
                handler.postDelayed(searchRunnable, 200);
            }
        });

        suggestionsPopup.setOnItemClickListener((parent, view, position, id) -> {
            String selectedPlace = suggestionsAdapter.getItem(position);
            if (selectedPlace != null && !selectedPlace.startsWith("No suggestions") && !selectedPlace.startsWith("Search failed")) {
                editText.setText(selectedPlace);
                editText.setTag("fixed:" + selectedPlace);
                clearButton.setVisibility(View.VISIBLE);
                suggestionsPopup.dismiss();
                if (!isCurrentLocation && currentLocationText.getText().length() > 0 && !isNavigating) {
                    navigateToHome2Activity();
                }
                Log.d(TAG, "Selected " + (isCurrentLocation ? "current" : "destination") + ": " + selectedPlace);
            } else {
                editText.setTag("active");
            }
        });

        clearButton.setOnClickListener(v -> {
            editText.setText("");
            updatePlaceholder(editText, isCurrentLocation);
            editText.setTag(null);
            clearButton.setVisibility(View.GONE);
            suggestionsPopup.dismiss();
            editText.requestFocus();
            fetchRecommendations(editText, isCurrentLocation);
            Log.d(TAG, "Cleared " + (isCurrentLocation ? "current" : "destination"));
        });
    }

    private void updatePlaceholder(EditText editText, boolean isCurrentLocation) {
        if (isCurrentLocation) {
            String placeholder = currentLatLng != null ? "Current Location" : "Enter Current Location";
            editText.setHint(placeholder);
            if (editText.getText().length() == 0 && currentLatLng != null && editText.getTag() == null) {
                skeletonLoader.setVisibility(View.VISIBLE);
                executorService.execute(() -> {
                    try {
                        GeocodingResult[] results = GeocodingApi.reverseGeocode(geoApiContext,
                                new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude)).await();
                        String address = results.length > 0 ? results[0].formattedAddress : placeholder;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            if (editText.getText().length() == 0 && editText.getTag() == null) {
                                editText.setText(address);
                                editText.setTag("fixed:" + address);
                            }
                            skeletonLoader.setVisibility(View.GONE);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to update current location placeholder: " + e.getMessage());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            skeletonLoader.setVisibility(View.GONE);
                            Toast.makeText(requireContext(), "Failed to load location", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } else {
                skeletonLoader.setVisibility(View.GONE);
            }
        } else {
            editText.setHint("Enter Destination");
            skeletonLoader.setVisibility(View.GONE);
        }
    }

    private void fetchRecommendations(EditText editText, boolean isCurrentLocation) {
        executorService.execute(() -> {
            try {
                List<String> recommendations = new ArrayList<>();
                if (isCurrentLocation) {
                    PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext,
                                    new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude))
                            .radius(1000)
                            .type(PlaceType.ESTABLISHMENT)
                            .keyword("landmark")
                            .await();
                    if (response.results != null) {
                        for (PlacesSearchResult result : response.results) {
                            if (result.name != null && result.vicinity != null) {
                                recommendations.add(String.format("%s, %s", result.name, result.vicinity));
                            }
                        }
                    }
                } else {
                    recommendations.addAll(Arrays.asList(
                            "Chennai International Airport, Chennai, Tamil Nadu",
                            "Marina Beach, Chennai, Tamil Nadu",
                            "Chennai Central Railway Station, Chennai, Tamil Nadu"
                    ));
                    PlacesSearchResponse response = PlacesApi.nearbySearchQuery(geoApiContext,
                                    new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude))
                            .radius(10000)
                            .type(PlaceType.ESTABLISHMENT)
                            .keyword("tourist attraction")
                            .await();
                    if (response.results != null) {
                        for (PlacesSearchResult result : response.results) {
                            if (result.name != null && result.vicinity != null) {
                                recommendations.add(String.format("%s, %s", result.name, result.vicinity));
                            }
                        }
                    }
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    suggestionsAdapter.clear();
                    recommendations.removeIf(String::isEmpty);
                    suggestionsAdapter.addAll(recommendations.isEmpty() ? List.of("No recommendations available") : recommendations);
                    suggestionsAdapter.notifyDataSetChanged();
                    suggestionsPopup.setAnchorView(editText);
                    suggestionsPopup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.dialog_holo_light_frame));
                    suggestionsPopup.setDropDownGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
                    suggestionsPopup.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                    suggestionsPopup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (editText.hasFocus() && "active".equals(editText.getTag())) {
                        suggestionsPopup.show();
                    }
                    Log.d(TAG, "Fetched recommendations for " + (isCurrentLocation ? "current" : "destination") + ": " + recommendations.size());
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    suggestionsAdapter.clear();
                    suggestionsAdapter.add("Recommendations failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                    suggestionsAdapter.notifyDataSetChanged();
                    suggestionsPopup.setAnchorView(editText);
                    if (editText.hasFocus() && "active".equals(editText.getTag())) {
                        suggestionsPopup.show();
                    }
                    Toast.makeText(requireContext(), "Recommendations failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchSuggestions(EditText editText, boolean isCurrentLocation) {
        executorService.execute(() -> {
            try {
                String input = editText.getText().toString().trim();
                if (input.isEmpty()) {
                    fetchRecommendations(editText, isCurrentLocation);
                    return;
                }
                com.google.maps.model.LatLng location = currentLatLng != null ?
                        new com.google.maps.model.LatLng(currentLatLng.latitude, currentLatLng.longitude) : null;
                com.google.maps.model.AutocompletePrediction[] predictions = PlacesApi.queryAutocomplete(geoApiContext, input)
                        .location(location)
                        .radius(10000)
                        .await();
                List<String> suggestions = new ArrayList<>();
                if (predictions != null) {
                    for (AutocompletePrediction prediction : predictions) {
                        if (prediction.description != null) {
                            suggestions.add(prediction.description);
                        }
                    }
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    suggestionsAdapter.clear();
                    suggestionsAdapter.addAll(suggestions.isEmpty() ? List.of("No suggestions found") : suggestions);
                    suggestionsAdapter.notifyDataSetChanged();
                    suggestionsPopup.setAnchorView(editText);
                    suggestionsPopup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.dialog_holo_light_frame));
                    suggestionsPopup.setDropDownGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
                    suggestionsPopup.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                    suggestionsPopup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (editText.hasFocus() && "active".equals(editText.getTag())) {
                        suggestionsPopup.show();
                    }
                    Log.d(TAG, "Fetched suggestions for " + (isCurrentLocation ? "current" : "destination") + ": " + suggestions.size());
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    suggestionsAdapter.clear();
                    suggestionsAdapter.add("Search failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                    suggestionsAdapter.notifyDataSetChanged();
                    suggestionsPopup.setAnchorView(editText);
                    if (editText.hasFocus() && "active".equals(editText.getTag())) {
                        suggestionsPopup.show();
                    }
                    Toast.makeText(requireContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void toggleMapType() {
        if (!isAdded() || googleMap == null) return;
        int newType = googleMap.getMapType() == GoogleMap.MAP_TYPE_NORMAL ? GoogleMap.MAP_TYPE_SATELLITE : GoogleMap.MAP_TYPE_NORMAL;
        googleMap.setMapType(newType);
        Toast.makeText(requireContext(), "Map type: " + (newType == GoogleMap.MAP_TYPE_NORMAL ? "Normal" : "Satellite"), Toast.LENGTH_SHORT).show();
    }

    private void callEmergency() {
        if (!isAdded()) return;
        Intent intent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:911"));
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), "Cannot make a call", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToHome2Activity() {
        if (isNavigating) {
            Log.d(TAG, "navigateToHome2Activity: Already navigating, skipping");
            return;
        }
        isNavigating = true;
        String currentAddress = currentLocationText.getText().toString().trim();
        String destinationAddress = destinationText.getText().toString().trim();

        if (currentAddress.isEmpty() || destinationAddress.isEmpty() || !destinationText.getTag().toString().startsWith("fixed:")) {
            Toast.makeText(requireContext(), "Please select a valid destination", Toast.LENGTH_SHORT).show();
            isNavigating = false;
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show();
            isNavigating = false;
            return;
        }

        executorService.execute(() -> {
            try {
                GeocodingResult[] currentResults = GeocodingApi.geocode(geoApiContext, currentAddress).await();
                if (currentResults == null || currentResults.length == 0) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Invalid current address", Toast.LENGTH_SHORT).show();
                        isNavigating = false;
                    });
                    return;
                }
                LatLng finalCurrentLatLng = new LatLng(currentResults[0].geometry.location.lat, currentResults[0].geometry.location.lng);
                String currentCountry = getCountryFromGeocodingResult(currentResults[0]);

                GeocodingResult[] destResults = GeocodingApi.geocode(geoApiContext, destinationAddress).await();
                if (destResults == null || destResults.length == 0) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Invalid destination address", Toast.LENGTH_SHORT).show();
                        isNavigating = false;
                    });
                    return;
                }
                LatLng finalDestinationLatLng = new LatLng(destResults[0].geometry.location.lat, destResults[0].geometry.location.lng);
                String destCountry = getCountryFromGeocodingResult(destResults[0]);

                if (!currentCountry.equalsIgnoreCase(destCountry)) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Navigation is only supported within the same country. Please select a destination in " + currentCountry + ".", Toast.LENGTH_LONG).show();
                        isNavigating = false;
                    });
                    return;
                }

                DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                        .origin(new com.google.maps.model.LatLng(finalCurrentLatLng.latitude, finalCurrentLatLng.longitude))
                        .destination(new com.google.maps.model.LatLng(finalDestinationLatLng.latitude, finalDestinationLatLng.longitude))
                        .mode(TravelMode.DRIVING)
                        .alternatives(true)
                        .await();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    if (result.routes != null && result.routes.length > 0) {
                        List<String> routeTrafficAlerts = new ArrayList<>();
                        for (int i = 0; i < Math.min(3, result.routes.length); i++) {
                            DirectionsRoute route = result.routes[i];
                            String trafficStatus = "Clear";
                            String delay = "0 min delay";
                            String distance = "0 mi";
                            if (route.legs != null && route.legs.length > 0) {
                                long durationInTraffic = route.legs[0].durationInTraffic != null ? route.legs[0].durationInTraffic.inSeconds : route.legs[0].duration.inSeconds;
                                long normalDuration = route.legs[0].duration.inSeconds;
                                long delaySeconds = durationInTraffic - normalDuration;
                                if (delaySeconds > 600) trafficStatus = "Heavy";
                                else if (delaySeconds > 300) trafficStatus = "Moderate";
                                delay = String.format("%d min delay", delaySeconds / 60);
                                distance = String.format("%.1f mi", route.legs[0].distance.inMeters / 1609.34);
                            }
                            String roadName = route.summary != null && !route.summary.isEmpty() ? route.summary : "Route " + (i + 1);
                            routeTrafficAlerts.add(String.format("%s on %s|%s|%s", trafficStatus, roadName, distance, delay));
                        }
                        Intent intent = new Intent(requireContext(), Home2Activity.class);
                        intent.putExtra("current_address", currentAddress);
                        intent.putExtra("destination_address", destinationAddress);
                        intent.putExtra("current_lat", finalCurrentLatLng.latitude);
                        intent.putExtra("current_lng", finalCurrentLatLng.longitude);
                        intent.putExtra("destination_lat", finalDestinationLatLng.latitude);
                        intent.putExtra("destination_lng", finalDestinationLatLng.longitude);
                        intent.putExtra("traffic_alerts", new ArrayList<>(routeTrafficAlerts));
                        intent.putExtra("petrol_bunks", new ArrayList<>(gasStationsLiveData.getValue() != null ? gasStationsLiveData.getValue() : new ArrayList<>()));
                        intent.putExtra("directions_result", result);
                        startActivity(intent);
                        Log.d(TAG, "Navigated to Home2Activity with " + routeTrafficAlerts.size() + " routes");
                    } else {
                        Toast.makeText(requireContext(), "No routes found", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "No routes found in Directions API response");
                    }
                    isNavigating = false;
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Directions API error: " + e.getMessage());
                    Toast.makeText(requireContext(), "Please enter a valid address within your region.", Toast.LENGTH_LONG).show();
                    isNavigating = false;
                });
            }
        });
    }

    private String getCountryFromGeocodingResult(GeocodingResult result) {
        for (com.google.maps.model.AddressComponent component : result.addressComponents) {
            if (Arrays.asList(component.types).contains(AddressType.COUNTRY)) {
                return component.longName;
            }
        }
        return "";
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 0.621371;
        return Math.round(distance * 10.0) / 10.0;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!isAdded()) return;
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationServices();
            if (isLocationEnabled) {
                View allowButton = requireView().findViewById(R.id.button_allow);
                if (allowButton != null) {
                    allowButton.setVisibility(View.GONE);
                }
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .commitAllowingStateLoss();
            } else {
                Toast.makeText(requireContext(), "Please enable location services", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_LONG).show();
        }
    }

    private BroadcastReceiver gpsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals("android.location.PROVIDERS_CHANGED")) {
                if (!isAdded()) {
                    Log.d(TAG, "gpsReceiver: Fragment not attached, skipping");
                    return;
                }
                checkLocationServices();
                boolean hasLocationPermission = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                Log.d(TAG, "gpsReceiver: isLocationEnabled=" + isLocationEnabled + ", hasLocationPermission=" + hasLocationPermission + ", isPermissionLayoutShown=" + isPermissionLayoutShown);
                if (isLocationEnabled != isPermissionLayoutShown || !hasLocationPermission) {
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new HomeFragment())
                            .commitAllowingStateLoss();
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (!isAdded()) {
            Log.d(TAG, "onResume: Fragment not attached, skipping");
            return;
        }
        checkLocationServices();
        boolean hasLocationPermission = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "onResume: isLocationEnabled=" + isLocationEnabled + ", hasLocationPermission=" + hasLocationPermission + ", isPermissionLayoutShown=" + isPermissionLayoutShown);
        IntentFilter filter = new IntentFilter("android.location.PROVIDERS_CHANGED");
        requireContext().registerReceiver(gpsReceiver, filter);
        if (isLocationEnabled && hasLocationPermission && isPermissionLayoutShown) {
            isPermissionLayoutShown = false;
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commitAllowingStateLoss();
        } else if (!isLocationEnabled && !isPermissionLayoutShown) {
            isPermissionLayoutShown = true;
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commitAllowingStateLoss();
        } else if (isMapInitialized && !cachedTrafficAlerts.isEmpty() && !cachedGasStations.isEmpty() &&
                !cachedPetrolStations.isEmpty() && !cachedGasStations2.isEmpty() && !cachedDieselStations.isEmpty()) {
            trafficAlertsLiveData.setValue(cachedTrafficAlerts);
            gasStationsLiveData.setValue(cachedGasStations);
            petrolStationsLiveData.setValue(cachedPetrolStations);
            gasStations2LiveData.setValue(cachedGasStations2);
            dieselStationsLiveData.setValue(cachedDieselStations);
            updateMapAndFragments();
            Log.d(TAG, "onResume: Restored cached data: traffic=" + cachedTrafficAlerts.size() + ", cng=" + cachedGasStations.size() +
                    ", petrol=" + cachedPetrolStations.size() + ", gas=" + cachedGasStations2.size() + ", diesel=" + cachedDieselStations.size());
        }
        if (noInternetMessage != null) noInternetMessage.setVisibility(isNetworkAvailable() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        if (suggestionsPopup != null) suggestionsPopup.dismiss();
        if (isAdded()) {
            try {
                requireContext().unregisterReceiver(gpsReceiver);
                Log.d(TAG, "onPause: Unregistered gpsReceiver");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "onPause: gpsReceiver already unregistered");
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle mapState = new Bundle();
            mapView.onSaveInstanceState(mapState);
            outState.putBundle("mapView", mapState);
        }
        outState.putStringArrayList(KEY_TRAFFIC, new ArrayList<>(cachedTrafficAlerts));
        outState.putStringArrayList(KEY_CNG, new ArrayList<>(cachedGasStations));
        outState.putStringArrayList(KEY_PETROL, new ArrayList<>(cachedPetrolStations));
        outState.putStringArrayList(KEY_GAS, new ArrayList<>(cachedGasStations2));
        outState.putStringArrayList(KEY_DIESEL, new ArrayList<>(cachedDieselStations));
        if (lastCachedLatLng != null) {
            outState.putDoubleArray(KEY_LAST_LATLNG, new double[]{lastCachedLatLng.latitude, lastCachedLatLng.longitude});
        }
        Log.d(TAG, "Saved instance state: traffic=" + cachedTrafficAlerts.size() +
                ", cng=" + cachedGasStations.size() +
                ", petrol=" + cachedPetrolStations.size() +
                ", gas=" + cachedGasStations2.size() +
                ", diesel=" + cachedDieselStations.size());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) mapView.onDestroy();
        executorService.execute(() -> {
            try {
                if (geoApiContext != null) {
                    geoApiContext.shutdown();
                    Log.d(TAG, "GeoApiContext shut down");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down GeoApiContext: " + e.getMessage());
            }
        });
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Log.e(TAG, "ExecutorService shutdown interrupted: " + e.getMessage());
        }
        isMapInitialized = false;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    private class ViewPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private final List<RecyclerViewFragment> fragments = new ArrayList<>(5);

        public ViewPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
            for (int i = 0; i < 5; i++) {
                fragments.add(null);
            }
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            RecyclerViewFragment fragment = new RecyclerViewFragment();
            Bundle args = new Bundle();
            args.putInt("type", position);
            switch (position) {
                case 0:
                    args.putString("data_type", "traffic");
                    break;
                case 1:
                    args.putString("data_type", "cng");
                    break;
                case 2:
                    args.putString("data_type", "petrol");
                    break;
                case 3:
                    args.putString("data_type", "gas");
                    break;
                case 4:
                    args.putString("data_type", "diesel");
                    break;
            }
            fragment.setArguments(args);
            fragments.set(position, fragment);
            Log.d(TAG, "Created RecyclerViewFragment for position " + position);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return 5;
        }

        public RecyclerViewFragment getFragment(int position) {
            return fragments.get(position);
        }
    }
}