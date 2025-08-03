package com.example.gmailish.ui.inbox;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.Gravity;

public class InboxActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create layout programmatically
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);

        TextView textView = new TextView(this);
        textView.setText("📥 Welcome to your Inbox!");
        textView.setTextSize(24);

        layout.addView(textView);
        setContentView(layout);
    }
}
