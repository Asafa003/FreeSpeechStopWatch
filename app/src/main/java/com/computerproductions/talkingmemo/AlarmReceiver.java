
package com.computerproductions.talkingmemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String CHANNEL_ID = "alarm_firing_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        Alarm alarm = new Alarm(context);
        alarm.fromIntent(intent);

        Log.i(TAG, "Alarm received: id=" + alarm.getId() + " title='" + alarm.getTitle() + "'");

        Intent activityIntent = new Intent(context, AlarmNotificationActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        alarm.toIntent(activityIntent);

        // Post a full-screen notification — this is the reliable path on Android 10+.
        // When the screen is off or locked, the system launches the full-screen intent
        // (opening AlarmNotificationActivity directly). When the phone is unlocked and
        // in use, it shows a heads-up notification the user can tap.
        postFullScreenNotification(context, alarm, activityIntent);

        // Start alarm sound immediately — don't wait for the activity to launch,
        // because on Android 10+ the activity may not auto-open (user gets a
        // heads-up notification instead).
        startAlarmSound(context);

        // Also try starting the activity directly as a fallback.
        // On older Android this works reliably. On Android 10+ it may be silently
        // ignored, but the full-screen notification above covers that case.
        try {
            context.startActivity(activityIntent);
        } catch (Exception e) {
            Log.w(TAG, "Direct startActivity failed (expected on Android 10+): " + e.getMessage());
        }
    }

    private void startAlarmSound(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("message_PREFS", Context.MODE_PRIVATE);
        String reminderMessage = prefs.getString("KEY_MSG", "");

        Intent soundIntent = new Intent(context, SoundService.class);

        if (reminderMessage.equals(context.getString(R.string.appointment_label))) {
            soundIntent.putExtra("EXTRA_APT", R.raw.sound_appointment);
        }
        if (reminderMessage.equals(context.getString(R.string.daily_medication_label))) {
            soundIntent.putExtra("EXTRA_MED", R.raw.sound_medication);
        }
        if (reminderMessage.equals(context.getString(R.string.prescription_refill_label))) {
            soundIntent.putExtra("EXTRA_PRESC", R.raw.sound_prescription);
        }

        if (!reminderMessage.isEmpty()) {
            ContextCompat.startForegroundService(context, soundIntent);
        }
    }

    private void postFullScreenNotification(Context context, Alarm alarm, Intent activityIntent) {
        // Create notification channel (required Android 8+, safe to call repeatedly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Fires when an alarm goes off");
            channel.setBypassDnd(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                (int) alarm.getId(),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_MAX);
        }

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(alarm.getTitle())
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int) alarm.getId(), notification);
    }
}

