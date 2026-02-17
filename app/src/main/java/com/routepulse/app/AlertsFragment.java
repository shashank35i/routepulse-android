package com.routepulse.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AlertsFragment extends Fragment {
    private static final String TAG = "AlertsFragment";
    private static final String PREFS_NAME = "RoutePulsePrefs";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private TextView statusText, distanceText, activeText, sortText;
    private Switch openNowSwitch;
    private ProgressBar preloader;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ViewPagerAdapter adapter;
    private MutableLiveData<List<String>> trafficAlertsLiveData = new MutableLiveData<>();
    private MutableLiveData<List<String>> gasStationsLiveData = new MutableLiveData<>();
    private List<String> notifications = new ArrayList<>();
    private List<String> cachedTrafficAlerts = new ArrayList<>();
    private List<String> cachedGasStations = new ArrayList<>();
    private List<String> filteredGasStations = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean sortByDistance = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadNotifications();
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
        // Separate traffic alerts and gas stations
        cachedTrafficAlerts.clear();
        cachedGasStations.clear();
        for (String notification : notifications) {
            if (notification.contains(" on ") && notification.split("\\|").length == 4) {
                cachedTrafficAlerts.add(notification);
            } else if (notification.split("\\|").length == 5) {
                cachedGasStations.add(notification);
            }
        }
        filteredGasStations = new ArrayList<>(cachedGasStations);
        trafficAlertsLiveData.setValue(cachedTrafficAlerts.isEmpty() ? List.of("No active alerts") : cachedTrafficAlerts);
        gasStationsLiveData.setValue(cachedGasStations.isEmpty() ? List.of("No petrol stations") : cachedGasStations);
        Log.d(TAG, "Loaded notifications: traffic=" + cachedTrafficAlerts.size() + ", gas=" + cachedGasStations.size());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alerts, container, false);
        statusText = view.findViewById(R.id.status_text);
        distanceText = view.findViewById(R.id.distance_text);
        activeText = view.findViewById(R.id.active_text);
        openNowSwitch = view.findViewById(R.id.openNowSwitch);
        sortText = view.findViewById(R.id.sortText);
        preloader = view.findViewById(R.id.preloader);
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager = view.findViewById(R.id.view_pager);

        adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Traffic Alerts" : "Nearby Petrol Stations");
        }).attach();

        openNowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            filterGasStations(isChecked);
            updateFragments();
        });
        sortText.setOnClickListener(v -> {
            sortByDistance = !sortByDistance;
            sortText.setText(sortByDistance ? "Sort by Distance" : "Sort by Name");
            updateFragments();
        });

        updateAlertCard();
        updateFragments();

        return view;
    }

    private void updateAlertCard() {
        if (!isAdded()) return;
        if (cachedTrafficAlerts.isEmpty()) {
            statusText.setText("No Traffic Alerts");
            distanceText.setText("No alerts within 5km");
            activeText.setText("Updated now");
        } else {
            String alert = cachedTrafficAlerts.get(0);
            String[] parts = alert.split("\\|");
            if (parts.length >= 3) {
                statusText.setText(parts[0]);
                distanceText.setText(parts[2]);
                activeText.setText("Updated now");
            } else {
                Log.e(TAG, "Invalid traffic alert format: " + alert);
            }
        }
    }

    private void filterGasStations(boolean openOnly) {
        filteredGasStations.clear();
        for (String bunk : cachedGasStations) {
            if (bunk.startsWith("No ") || bunk.startsWith("Failed")) continue;
            String[] parts = bunk.split("\\|");
            if (parts.length < 5) continue;
            if (!openOnly || parts[4].equals("Open")) {
                filteredGasStations.add(bunk);
            }
        }
    }

    private void updateFragments() {
        if (!isAdded()) return;
        List<String> sortedBunks = new ArrayList<>(filteredGasStations);
        if (sortByDistance) {
            Collections.sort(sortedBunks, (a, b) -> {
                double distA = Double.parseDouble(a.split("\\|")[2].replaceAll("[^0-9.]", ""));
                double distB = Double.parseDouble(b.split("\\|")[2].replaceAll("[^0-9.]", ""));
                return Double.compare(distA, distB);
            });
        } else {
            Collections.sort(sortedBunks, Comparator.comparing(bunk -> bunk.split("\\|")[0]));
        }
        gasStationsLiveData.setValue(sortedBunks.isEmpty() ? List.of("No petrol stations") : sortedBunks);
        if (adapter != null) {
            NotificationListFragment trafficFragment = adapter.getFragment(0);
            NotificationListFragment gasFragment = adapter.getFragment(1);
            if (trafficFragment != null) trafficFragment.notifyDataSetChanged();
            if (gasFragment != null) gasFragment.notifyDataSetChanged();
        }
    }

    private static class ViewPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private final List<NotificationListFragment> fragments = new ArrayList<>(2);

        public ViewPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
            fragments.add(null);
            fragments.add(null);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            NotificationListFragment fragment = new NotificationListFragment();
            Bundle args = new Bundle();
            args.putInt("type", position);
            fragment.setArguments(args);
            fragments.set(position, fragment);
            Log.d(TAG, "Created NotificationListFragment for position " + position);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public NotificationListFragment getFragment(int position) {
            return fragments.get(position);
        }
    }

    public MutableLiveData<List<String>> getTrafficAlertsLiveData() {
        return trafficAlertsLiveData;
    }

    public MutableLiveData<List<String>> getGasStationsLiveData() {
        return gasStationsLiveData;
    }
}