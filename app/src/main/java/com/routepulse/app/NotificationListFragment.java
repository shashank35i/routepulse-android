package com.routepulse.app;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class NotificationListFragment extends Fragment {
    private static final String TAG = "NotificationListFragment";
    private RecyclerView recyclerView;
    private ItemAdapter adapter;
    private int type; // 0 for traffic alerts, 1 for petrol stations

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        type = getArguments() != null ? getArguments().getInt("type", 0) : 0;
        adapter = new ItemAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        AlertsFragment parentFragment = (AlertsFragment) getParentFragment();
        if (parentFragment != null) {
            if (type == 0) {
                parentFragment.getTrafficAlertsLiveData().observe(getViewLifecycleOwner(), items -> {
                    adapter.updateData(items != null ? items : new ArrayList<>());
                });
            } else {
                parentFragment.getGasStationsLiveData().observe(getViewLifecycleOwner(), items -> {
                    adapter.updateData(items != null ? items : new ArrayList<>());
                });
            }
        }
        return view;
    }

    public void notifyDataSetChanged() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private List<String> items;

        ItemAdapter(List<String> items) {
            this.items = items != null ? items : new ArrayList<>();
        }

        void updateData(List<String> newItems) {
            this.items = newItems != null ? newItems : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(type == 0 ? R.layout.recycler_route_item : R.layout.recycler_gas_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String item = items.get(position);
            if (item == null || item.startsWith("No ") || item.startsWith("Failed")) {
                if (holder.nameText != null) {
                    holder.nameText.setText(item != null ? item : "No data available");
                }
                if (holder.detailsText != null) holder.detailsText.setVisibility(View.GONE);
                if (holder.statusText != null) holder.statusText.setVisibility(View.GONE);
                if (holder.vicinityText != null) holder.vicinityText.setVisibility(View.GONE);
                return;
            }

            String[] parts = item.split("\\|");
            if (type == 0) { // Traffic alerts
                if (parts.length >= 4) {
                    if (holder.nameText != null) {
                        holder.nameText.setText(parts[0]);
                    }
                    if (holder.detailsText != null) {
                        holder.detailsText.setText(parts[2] + " · " + parts[1]);
                        holder.detailsText.setVisibility(View.VISIBLE);
                    }
                    if (holder.statusText != null) {
                        String status = parts[0].split(" ")[0];
                        holder.statusText.setText(status);
                        int color = status.equals("Heavy") ? Color.RED :
                                status.equals("Accident") ? Color.YELLOW :
                                        status.equals("Clear") ? Color.GREEN : Color.GRAY;
                        holder.statusText.setTextColor(color);
                        holder.statusText.setVisibility(View.VISIBLE);
                    }
                    if (holder.vicinityText != null) {
                        holder.vicinityText.setVisibility(View.GONE);
                    }
                }
            } else { // Petrol stations
                if (parts.length >= 5) {
                    if (holder.nameText != null) {
                        holder.nameText.setText(parts[0]);
                    }
                    if (holder.vicinityText != null) {
                        holder.vicinityText.setText(parts[1]);
                        holder.vicinityText.setVisibility(View.VISIBLE);
                    }
                    if (holder.detailsText != null) {
                        holder.detailsText.setText(parts[2]);
                        holder.detailsText.setVisibility(View.VISIBLE);
                    }
                    if (holder.statusText != null) {
                        holder.statusText.setText(parts[4]);
                        holder.statusText.setTextColor(parts[4].equals("Open") ? Color.parseColor("#4CAF50") : Color.parseColor("#FF3B30"));
                        holder.statusText.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, detailsText, statusText, vicinityText;

            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.name_text);
                detailsText = itemView.findViewById(R.id.details_text);
                statusText = itemView.findViewById(R.id.status_text);
                if (type == 1) {
                    vicinityText = itemView.findViewById(R.id.vicinity_text);
                }

                // Log if any TextView is null
                if (nameText == null) {
                    Log.e(TAG, "nameText is null in ViewHolder for layout: " + (type == 0 ? "recycler_route_item" : "recycler_gas_item"));
                }
                if (detailsText == null) {
                    Log.e(TAG, "detailsText is null in ViewHolder for layout: " + (type == 0 ? "recycler_route_item" : "recycler_gas_item"));
                }
                if (statusText == null) {
                    Log.e(TAG, "statusText is null in ViewHolder for layout: " + (type == 0 ? "recycler_route_item" : "recycler_gas_item"));
                }
                if (type == 1 && vicinityText == null) {
                    Log.e(TAG, "vicinityText is null in ViewHolder for recycler_gas_item");
                }
            }
        }
    }
}