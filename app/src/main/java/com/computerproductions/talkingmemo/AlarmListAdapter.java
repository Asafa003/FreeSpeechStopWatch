
package com.computerproductions.talkingmemo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.os.Build;

import androidx.core.content.ContextCompat;



class AlarmListAdapter extends BaseAdapter {
    private final String TAG = "MainActivity";

    private Context mContext;
    private DataSource mDataSource;
    private LayoutInflater mInflater;
    private DateTime mDateTime;
    private int mColorOutdated;
    private int mColorActive;
    private AlarmManager mAlarmManager;

    public AlarmListAdapter(Context context) {
        mContext = context;
        mDataSource = DataSource.getInstance(context);

        Log.i(TAG, "AlarmListAdapter.create()");

        mInflater = LayoutInflater.from(context);
        mDateTime = new DateTime(context);

        mColorOutdated = ContextCompat.getColor(mContext, R.color.alarm_title_outdated);
        mColorActive = ContextCompat.getColor(mContext, R.color.alarm_title_active);

        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        dataSetChanged();
    }

    public void save() {
        DataSource.save();
    }

    public void update(Alarm alarm) {
        DataSource.update(alarm);
        dataSetChanged();
    }

    public void updateAlarms() {
        Log.i(TAG, "AlarmListAdapter.updateAlarms()");
        for (int i = 0; i < DataSource.size(); i++)
            DataSource.update(DataSource.get(i));
        dataSetChanged();
    }

    public void add(Alarm alarm) {
        DataSource.add(alarm);
        dataSetChanged();
    }

    public void delete(int index) {
        cancelAlarm(DataSource.get(index));
        DataSource.remove(index);
        dataSetChanged();
    }

    public void onSettingsUpdated() {
        mDateTime.update();
        dataSetChanged();
    }

    public int getCount() {
        return DataSource.size();
    }

    public Alarm getItem(int position) {
        return DataSource.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        Alarm alarm = DataSource.get(position);

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.list_item, null);

            holder = new ViewHolder();
            holder.title = convertView.findViewById(R.id.item_title);
            holder.details = convertView.findViewById(R.id.item_details);
            holder.desc = convertView.findViewById(R.id.item_desc);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.title.setText(alarm.getTitle());
        holder.details.setText(mDateTime.formatDetails(alarm) + (alarm.getEnabled() ? "" : " [disabled]"));
        holder.desc.setText(alarm.getMessage());

        if (alarm.getOutdated())
            holder.title.setTextColor(mColorOutdated);
        else
            holder.title.setTextColor(mColorActive);

        return convertView;
    }

    private void dataSetChanged() {
        for (int i = 0; i < DataSource.size(); i++)
            setAlarm(DataSource.get(i));

        notifyDataSetChanged();
    }

    private void setAlarm(Alarm alarm) {
        PendingIntent sender;
        Intent intent;

        if (!alarm.getEnabled() || alarm.getOutdated()) {
            return;
        }

        // ✅ Android 12+ exact alarm permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!mAlarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted. Skipping alarm id=" + alarm.getId());
                return;
            }
        }

        intent = new Intent(mContext, AlarmReceiver.class);
        alarm.toIntent(intent);

        sender = PendingIntent.getBroadcast(
                mContext,
                (int) alarm.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // setAlarmClock() is exempt from Doze mode and grants background
        // activity start privileges — the correct API for alarm clock apps.
        Intent showIntent = new Intent(mContext, MainActivity.class);
        PendingIntent showPendingIntent = PendingIntent.getActivity(
                mContext, 0, showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                alarm.getDate(), showPendingIntent);

        mAlarmManager.setAlarmClock(alarmClockInfo, sender);
    }


    private void cancelAlarm(Alarm alarm) {
        PendingIntent sender;
        Intent intent;

        intent = new Intent(mContext, AlarmReceiver.class);
        sender = PendingIntent.getBroadcast(mContext, (int) alarm.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mAlarmManager.cancel(sender);
    }

    static class ViewHolder {
        TextView title;
        TextView details;
        TextView desc;
    }
}

