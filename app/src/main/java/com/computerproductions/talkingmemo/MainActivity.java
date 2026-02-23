

package com.computerproductions.talkingmemo;

import android.os.Bundle;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.os.Build;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.content.Context;
import android.app.AlarmManager;
import android.app.NotificationManager;


import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.prefs.Preferences;

import androidx.annotation.NonNull;

//  Toast.makeText(getApplicationContext(), "Delete" + index, Toast.LENGTH_SHORT).show();

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private ListView mAlarmList;
    private AlarmListAdapter mAlarmListAdapter;
    private Alarm mCurrentAlarm;

    private ActivityResultLauncher<Intent> mNewAlarmLauncher;
    private ActivityResultLauncher<Intent> mEditAlarmLauncher;
    private ActivityResultLauncher<Intent> mPreferencesLauncher;

    private final int CONTEXT_MENU_EDIT = 0;
    private final int CONTEXT_MENU_DELETE = 1;
    private final int CONTEXT_MENU_DUPLICATE = 2;

    String message;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "MainActivity.onCreate()");

        mAlarmList = findViewById(R.id.alarm_list);

        mAlarmListAdapter = new AlarmListAdapter(this);
        mAlarmList.setAdapter(mAlarmListAdapter);
        mAlarmList.setOnItemClickListener(mListOnItemClickListener);
        registerForContextMenu(mAlarmList);

        mCurrentAlarm = null;

        // Initialize activity result launchers
        mNewAlarmLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        mCurrentAlarm.fromIntent(result.getData());
                        mAlarmListAdapter.add(mCurrentAlarm);
                    }
                    mCurrentAlarm = null;
                });

        mEditAlarmLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        mCurrentAlarm.fromIntent(result.getData());
                        mAlarmListAdapter.update(mCurrentAlarm);
                    }
                    mCurrentAlarm = null;
                });

        mPreferencesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> mAlarmListAdapter.onSettingsUpdated());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setOnItemSelectedListener(menuItem -> {
            message = menuItem.getTitle() + "";
            addAlarm();
            return true;
        });

        // Android 12+ exact alarm permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                );
                startActivity(intent);
            }
        }

        // Android 14+ full-screen intent permission (needed for alarm to auto-open)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                );
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "MainActivity.onDestroy()");
//    mAlarmListAdapter.save();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity.onResume()");
        mAlarmListAdapter.updateAlarms();
    }

    public void addAlarm() {
        Intent intent = new Intent(getBaseContext(), EditAlarmActivity.class);
        mCurrentAlarm = new Alarm(this);
        intent.putExtra("EXTRA_MSG", message);
        mCurrentAlarm.toIntent(intent);
        mNewAlarmLauncher.launch(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (R.id.menu_settings == item.getItemId()) {
            Intent intent = new Intent(getBaseContext(), PreferencesActivity.class);
            mPreferencesLauncher.launch(intent);
            return true;
        } else if (R.id.menu_about == item.getItemId()) {
            Intent intent = new Intent(getBaseContext(), InfoActivity.class);
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.alarm_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

            menu.setHeaderTitle(mAlarmListAdapter.getItem(info.position).getTitle());
            menu.add(Menu.NONE, CONTEXT_MENU_EDIT, Menu.NONE, "Edit");
            menu.add(Menu.NONE, CONTEXT_MENU_DELETE, Menu.NONE, "Delete");
            menu.add(Menu.NONE, CONTEXT_MENU_DUPLICATE, Menu.NONE, "Duplicate");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int index = item.getItemId();

        if (index == CONTEXT_MENU_EDIT) {
            Intent intent = new Intent(getBaseContext(), EditAlarmActivity.class);

            mCurrentAlarm = mAlarmListAdapter.getItem(info.position);
            mCurrentAlarm.toIntent(intent);
            mEditAlarmLauncher.launch(intent);
        } else if (index == CONTEXT_MENU_DELETE) {
            mAlarmListAdapter.delete(info.position);
        } else if (index == CONTEXT_MENU_DUPLICATE) {
            Alarm alarm = mAlarmListAdapter.getItem(info.position);
            Alarm newAlarm = new Alarm(this);
            Intent intent = new Intent();

            alarm.toIntent(intent);
            newAlarm.fromIntent(intent);
            newAlarm.setTitle(alarm.getTitle() + " (copy)");
            mAlarmListAdapter.add(newAlarm);
        }

        return true;
    }

    private AdapterView.OnItemClickListener mListOnItemClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Intent intent = new Intent(getBaseContext(), EditAlarmActivity.class);

            mCurrentAlarm = mAlarmListAdapter.getItem(position);
            mCurrentAlarm.toIntent(intent);
            mEditAlarmLauncher.launch(intent);
        }
    };

}

