
package com.computerproductions.talkingmemo;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class InfoActivity extends AppCompatActivity {
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_info);

        long nextId = DataSource.getInstance(null).getNextId() - 1;

        mTextView = findViewById(R.id.alarms_created_text);
        mTextView.setText("Alarms created: " + nextId);
    }
}

