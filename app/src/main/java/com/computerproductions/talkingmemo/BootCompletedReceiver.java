
package com.computerproductions.talkingmemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // just create AlarmListAdapter instance so it will load alarms and start them
        new AlarmListAdapter(context);
    }
}

