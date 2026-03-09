
package com.computerproductions.talkingmemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.app.KeyguardManager;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class AlarmNotificationActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    SharedPreferences mSharedPreferences;
    String reminderMessage;
    private Ringtone mRingtone;
    private Vibrator mVibrator;
    private final long[] mVibratePattern = {0, 500, 500};
    private boolean mVibrate;
    private Uri mAlarmSound;
    private long mPlayTime;
    private Timer mTimer = null;
    private Alarm mAlarm;
    private DateTime mDateTime;
    private TextView mTextView;
    private PlayTimerTask mTimerTask;
    private TextToSpeech mTextToSpeech;
    private boolean mTtsInitialized = false;
    private String mPendingTtsText = null;
    private Timer mTtsRepeatTimer = null;
    private static final long TTS_REPEAT_INTERVAL = 8000; // repeat every 8 seconds

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mSharedPreferences = getSharedPreferences("message_PREFS", MODE_PRIVATE);
        
        // Show alarm on lock screen for all Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_alarm_notification);

        reminderMessage = mSharedPreferences.getString("KEY_MSG", "");

        mDateTime = new DateTime(this);
        mTextView = findViewById(R.id.alarm_title_text);

        readPreferences();

        // SoundService is now started from AlarmReceiver so it plays immediately
        // even when this activity doesn't auto-launch (phone unlocked & in use).
        // No need to start it again here.

        // Dismiss the alarm notification since we're now showing the full activity
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();

        // Initialize TextToSpeech
        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTextToSpeech.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "TTS: Language not supported");
                        mTextToSpeech.setLanguage(Locale.US);
                    }
                    mTtsInitialized = true;
                    Log.i(TAG, "TTS initialized successfully");
                    // Speak any text that was queued before TTS was ready
                    if (mPendingTtsText != null) {
                        Log.i(TAG, "Speaking pending TTS text: " + mPendingTtsText);
                        mTextToSpeech.speak(mPendingTtsText, TextToSpeech.QUEUE_ADD, null, "alarm_tts");
                        mPendingTtsText = null;
                        // Start the repeat timer now that TTS is ready
                        startRepeatingTts();
                    }
                } else {
                    Log.e(TAG, "TTS initialization failed");
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "AlarmNotificationActivity.onDestroy()");

        stop();
        
        // Shutdown TextToSpeech
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            mTextToSpeech.shutdown();
            mTextToSpeech = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mRingtone = RingtoneManager.getRingtone(getApplicationContext(), mAlarmSound);
        if (mVibrate)
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        start(getIntent());

        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen1.startTone(ToneGenerator.TONE_CDMA_ONE_MIN_BEEP, 150);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "AlarmNotificationActivity.onNewIntent()");

        addNotification(mAlarm);

        stop();
        start(intent);
    }

    private void start(Intent intent) {
        mAlarm = new Alarm(this);
        mAlarm.fromIntent(intent);

        Log.i(TAG, "AlarmNotificationActivity.start('" + mAlarm.getTitle() + "')");

        mTextView.setText(mAlarm.getTitle());

        mTimerTask = new PlayTimerTask();
        mTimer = new Timer();
        mTimer.schedule(mTimerTask, mPlayTime);
        mRingtone.play();
        if (mVibrate)
            mVibrator.vibrate(mVibratePattern, 0);
        
        // Speak the alarm title repeatedly using text-to-speech
        startRepeatingTts();
    }

    private void stop() {
        Log.i(TAG, "AlarmNotificationActivity.stop()");

        mTimer.cancel();
        mRingtone.stop();
        if (mVibrate)
            mVibrator.cancel();
        
        // Stop repeating TTS
        stopRepeatingTts();
        if (mTextToSpeech != null && mTextToSpeech.isSpeaking()) {
            mTextToSpeech.stop();
        }
    }

    public void onDismissClick(View view) {

        finish();

    }

    private void startRepeatingTts() {
        // Cancel any existing repeat timer
        stopRepeatingTts();

        // Speak immediately
        speakAlarmTitle();

        // Then repeat every TTS_REPEAT_INTERVAL milliseconds
        mTtsRepeatTimer = new Timer();
        mTtsRepeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> speakAlarmTitle());
            }
        }, TTS_REPEAT_INTERVAL, TTS_REPEAT_INTERVAL);
    }

    private void stopRepeatingTts() {
        if (mTtsRepeatTimer != null) {
            mTtsRepeatTimer.cancel();
            mTtsRepeatTimer = null;
        }
    }

    private void speakAlarmTitle() {
        if (mAlarm == null) return;
        String textToSpeak = mAlarm.getTitle();
        if (textToSpeak == null || textToSpeak.isEmpty()) {
            Log.w(TAG, "Alarm title is empty, skipping TTS");
            return;
        }

        if (mTextToSpeech != null && mTtsInitialized) {
            Log.i(TAG, "Speaking alarm title: " + textToSpeak);
            mTextToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, "alarm_tts");
        } else {
            // TTS not ready yet — queue for when onInit completes
            Log.i(TAG, "TTS not ready, queuing: " + textToSpeak);
            mPendingTtsText = textToSpeak;
        }
    }

    private void readPreferences() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mAlarmSound = Uri.parse(prefs.getString("alarm_sound_pref", "DEFAULT_RINGTONE_URI"));
        mVibrate = prefs.getBoolean("vibrate_pref", true);
        mPlayTime = (long) Integer.parseInt(prefs.getString("alarm_play_time_pref", "30")) * 1000;
    }


    private void addNotification(Alarm alarm) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = null;
        PendingIntent activity;
        Intent intent;

        Log.i(TAG, "AlarmNotificationActivity.addNotification(" + alarm.getId() + ", '" + alarm.getTitle() + "', '" + mDateTime.formatDetails(alarm) + "')");

        intent = new Intent(this.getApplicationContext(), MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        activity = PendingIntent.getActivity(this, (int) alarm.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel("alarmme_01", "MainActivity Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this)
                    .setContentIntent(activity)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setContentTitle("Missed alarm: " + alarm.getTitle())
                    .setContentText(mDateTime.formatDetails(alarm))
                    .setChannelId("alarmme_01")
                    .build();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify((int) alarm.getId(), notification);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private class PlayTimerTask extends TimerTask {
        @Override
        public void run() {
            Log.i(TAG, "AlarmNotificationActivity.PalyTimerTask.run()");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                addNotification(mAlarm);
            }
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopService(new Intent(this, SoundService.class));
    }
}

