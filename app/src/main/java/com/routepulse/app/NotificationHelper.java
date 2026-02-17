package com.routepulse.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.os.Build;
import android.util.Log;

public class NotificationHelper {
    private static final String CHANNEL_ID = "RoutePulseChannel";
    private static final String CHANNEL_NAME = "RoutePulse Notifications";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH // Ensures heads-up notifications
            );
            channel.setDescription("Notifications for traffic alerts and petrol stations");
            channel.enableVibration(true); // Enable vibration for high-priority
            channel.setShowBadge(true); // Show badge on app icon
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d("NotificationHelper", "Notification channel created: " + CHANNEL_ID);
            } else {
                Log.e("NotificationHelper", "Failed to get NotificationManager");
            }
        }
    }

    public static void showNotification(Context context, String title, String content, int notificationId, boolean highPriority) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Ensure this resource exists
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setPriority(highPriority ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);

        if (highPriority) {
            builder.setVibrate(new long[]{0, 500, 250, 500}); // Vibration pattern for high-priority
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND); // Add sound for high-priority
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
            Log.d("NotificationHelper", "Notification sent: ID=" + notificationId + ", Title=" + title + ", HighPriority=" + highPriority);
        } else {
            Log.e("NotificationHelper", "NotificationManager is null");
        }
    }
}