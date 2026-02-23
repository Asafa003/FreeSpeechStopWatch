package com.computerproductions.talkingmemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class SoundService extends Service {
    private static final String TAG = "SoundService";
    private static final String CHANNEL_ID = "sound_service_channel";
    private static final int NOTIFICATION_ID = 9999;
    MediaPlayer mMediaPlayer;

    public SoundService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Promote to foreground immediately so Android doesn't kill us
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildForegroundNotification());

        int soundId = 0;
        if (intent != null) {
            if (intent.hasExtra("EXTRA_APT")) {
                soundId = intent.getIntExtra("EXTRA_APT", 0);
            }
            if (intent.hasExtra("EXTRA_MED")) {
                soundId = intent.getIntExtra("EXTRA_MED", 0);
            }
            if (intent.hasExtra("EXTRA_PRESC")) {
                soundId = intent.getIntExtra("EXTRA_PRESC", 0);
            }
        }

        if (soundId != 0) {
            mMediaPlayer = MediaPlayer.create(this, soundId);
            if (mMediaPlayer != null) {
                mMediaPlayer.setOnCompletionListener(mp -> stopSelf());
                mMediaPlayer.start();
            } else {
                Log.w(TAG, "MediaPlayer.create returned null for soundId=" + soundId);
                stopSelf();
            }
        } else {
            Log.w(TAG, "No sound extra provided");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Sound Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows while alarm sound is playing");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildForegroundNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Playing alarm sound")
                .build();
    }
}
