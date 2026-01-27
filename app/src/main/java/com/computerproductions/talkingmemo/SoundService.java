package com.computerproductions.talkingmemo;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;

public class SoundService extends Service {
    MediaPlayer mMediaPlayer;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int soundId = 0;
        if (intent.hasExtra("EXTRA_APT")) {
            soundId =  intent.getIntExtra("EXTRA_APT", 0);
        }if (intent.hasExtra("EXTRA_MED")) {
            soundId =  intent.getIntExtra("EXTRA_MED", 0);
        }if (intent.hasExtra("EXTRA_PRESC")) {
            soundId =  intent.getIntExtra("EXTRA_PRESC", 0);
        }

       mMediaPlayer = MediaPlayer.create(this, soundId);
        mMediaPlayer.start();
        return START_NOT_STICKY;
    }


    public SoundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
