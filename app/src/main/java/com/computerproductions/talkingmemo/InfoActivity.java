
package com.computerproductions.talkingmemo;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class InfoActivity extends AppCompatActivity {
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_info);

        MaterialToolbar toolbar = findViewById(R.id.info_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        long nextId = DataSource.getInstance(null).getNextId() - 1;

        mTextView = findViewById(R.id.alarms_created_text);
        mTextView.setText("Reminders created: " + nextId);
    }
}

