package com.routepulse.app;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RecyclerViewFragment extends Fragment {
    private static final String TAG = "RecyclerViewFragment";
    private RecyclerView recyclerView;
    private DataAdapter adapter;
    private List<String> data;
    private int type;
    private static final String KEY_DATA = "data";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = new ArrayList<>();
        if (getArguments() != null) {
            type = getArguments().getInt("type", 0);
        }
        if (savedInstanceState != null) {
            ArrayList<String> savedData = savedInstanceState.getStringArrayList(KEY_DATA);
            if (savedData != null) {
                data.addAll(savedData);
                Log.d(TAG, "Restored data for type " + type + ": " + data.size() + " items: " + data);
            }
        }
        Log.d(TAG, "onCreate for type " + type);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView for type " + type);
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DataAdapter(requireContext(), data, type);
        recyclerView.setAdapter(adapter);

        HomeFragment homeFragment = (HomeFragment) getParentFragment();
        if (homeFragment == null) {
            Log.e(TAG, "HomeFragment is null for type " + type);
            return view;
        }

        LiveData<List<String>> liveData;
        String noDataMessage;
        switch (type) {
            case 0:
                liveData = homeFragment.getTrafficAlertsLiveData();
                noDataMessage = "No traffic data available";
                break;
            case 1:
                liveData = homeFragment.getGasStationsLiveData();
                noDataMessage = "No CNG stations found";
                break;
            case 2:
                liveData = homeFragment.getPetrolStationsLiveData();
                noDataMessage = "No Petrol stations found";
                break;
            case 3:
                liveData = homeFragment.getGasStations2LiveData();
                noDataMessage = "No LPG stations found";
                break;
            case 4:
                liveData = homeFragment.getDieselStationsLiveData();
                noDataMessage = "No Diesel stations found";
                break;
            default:
                Log.e(TAG, "Invalid type: " + type);
                return view;
        }

        liveData.observe(getViewLifecycleOwner(), newData -> {
            if (!isAdded()) return;
            data.clear();
            data.addAll(newData != null && !newData.isEmpty() ? newData : List.of(noDataMessage));
            adapter.notifyDataSetChanged();
            Log.d(TAG, "LiveData updated for type " + type + ": " + data.size() + " items: " + data);
        });

        if (!data.isEmpty()) {
            adapter.notifyDataSetChanged();
            Log.d(TAG, "Initialized adapter with " + data.size() + " items on create: " + data);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            Log.d(TAG, "onResume: Notified adapter for type " + type + " with " + data.size() + " items: " + data);
        } else {
            Log.e(TAG, "Adapter is null in onResume for type " + type);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            Log.d(TAG, "onViewCreated: Notified adapter for type " + type + " with " + data.size() + " items: " + data);
        } else {
            Log.e(TAG, "Adapter is null in onViewCreated for type " + type);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(KEY_DATA, new ArrayList<>(data));
        Log.d(TAG, "Saved data for type " + type + ": " + data.size() + " items");
    }

    private static class DataAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_TRAFFIC = 0;
        private static final int TYPE_GAS = 1;
        private static final int TYPE_NO_DATA = 2;
        private final Context context;
        private final List<String> data;
        private final int type;

        DataAdapter(Context context, List<String> data, int type) {
            this.context = context;
            this.data = data;
            this.type = type;
        }

        @Override
        public int getItemViewType(int position) {
            if (data.get(position).startsWith("No ") || data.get(position).startsWith("Failed")) {
                return TYPE_NO_DATA;
            }
            return type == 0 ? TYPE_TRAFFIC : TYPE_GAS;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_NO_DATA) {
                View view = LayoutInflater.from(context).inflate(R.layout.recycler_item, parent, false);
                return new NoDataViewHolder(view);
            } else if (viewType == TYPE_TRAFFIC) {
                View view = LayoutInflater.from(context).inflate(R.layout.recycler_traffic_item, parent, false);
                return new TrafficViewHolder(view);
            } else {
                View view = LayoutInflater.from(context).inflate(R.layout.recycler_gas_item, parent, false);
                return new GasViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            String item = data.get(position);
            if (holder instanceof NoDataViewHolder) {
                ((NoDataViewHolder) holder).itemText.setText(item);
                Log.d(TAG, "Binding no data at position " + position + ": " + item);
            } else if (holder instanceof TrafficViewHolder) {
                TrafficViewHolder trafficHolder = (TrafficViewHolder) holder;
                try {
                    String[] parts = item.split("\\|");
                    String status = parts[0].split(" ")[0];
                    trafficHolder.statusText.setText(parts[0]);
                    trafficHolder.vicinityText.setText(parts[1]);
                    trafficHolder.distanceText.setText(parts[2]);
                    trafficHolder.delayText.setText(parts[3]);
                    int color = status.equals("Heavy") ? Color.RED :
                            status.equals("Accident") ? Color.YELLOW :
                                    status.equals("Clear") ? Color.GREEN : Color.GRAY;
                    trafficHolder.statusIndicator.setBackgroundColor(color);
                    Log.d(TAG, "Binding traffic at position " + position + ": " + item);
                } catch (Exception e) {
                    Log.e(TAG, "Error binding traffic item: " + e.getMessage());
                    trafficHolder.statusText.setText("Error");
                }
            } else if (holder instanceof GasViewHolder) {
                GasViewHolder gasHolder = (GasViewHolder) holder;
                try {
                    String[] parts = item.split("\\|");
                    gasHolder.nameText.setText(parts[0]);
                    gasHolder.vicinityText.setText(parts[1]);
                    gasHolder.distanceText.setText(parts[2]);
                    gasHolder.statusText.setText(parts[4]);
                    gasHolder.statusText.setTextColor(parts[4].equals("Open") ? Color.parseColor("#4CAF50") : Color.parseColor("#FF3B30"));
                    Log.d(TAG, "Binding gas station at position " + position + ": " + item);
                } catch (Exception e) {
                    Log.e(TAG, "Error binding gas item: " + e.getMessage());
                    gasHolder.nameText.setText("Error");
                }
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class NoDataViewHolder extends RecyclerView.ViewHolder {
            TextView itemText;

            NoDataViewHolder(View view) {
                super(view);
                itemText = view.findViewById(R.id.item_text);
            }
        }

        static class TrafficViewHolder extends RecyclerView.ViewHolder {
            TextView statusText, vicinityText, distanceText, delayText;
            View statusIndicator;

            TrafficViewHolder(View view) {
                super(view);
                statusIndicator = view.findViewById(R.id.status_indicator);
                statusText = view.findViewById(R.id.status_text);
                vicinityText = view.findViewById(R.id.vicinity_text);
                distanceText = view.findViewById(R.id.distance_text);
                delayText = view.findViewById(R.id.delay_text);
            }
        }

        static class GasViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, vicinityText, distanceText, statusText;
            ImageView icon;

            GasViewHolder(View view) {
                super(view);
                icon = view.findViewById(R.id.icon);
                nameText = view.findViewById(R.id.name_text);
                vicinityText = view.findViewById(R.id.vicinity_text);
                distanceText = view.findViewById(R.id.distance_text);
                statusText = view.findViewById(R.id.status_text);
            }
        }
    }
}