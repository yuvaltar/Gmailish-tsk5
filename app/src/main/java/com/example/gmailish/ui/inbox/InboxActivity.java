package com.example.gmailish.ui.inbox;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gmailish.R;
import com.example.gmailish.model.Email;
import com.example.gmailish.ui.compose.ComposeActivity;

import java.util.List;

public class InboxActivity extends AppCompatActivity {

    private InboxViewModel viewModel;
    private RecyclerView recyclerView;
    private EmailAdapter adapter;
    private Button composeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        recyclerView = findViewById(R.id.inboxRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        composeButton = findViewById(R.id.composeButton);
        composeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ComposeActivity.class);
            startActivity(intent);
        });

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);

        viewModel.getEmails().observe(this, this::displayEmails);
        viewModel.getError().observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        );

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);

        Log.d("JWT", "Loaded token: " + token);

        if (token != null) {
            viewModel.loadEmails(token);
        } else {
            Toast.makeText(this, "JWT missing!", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayEmails(List<Email> emails) {
        adapter = new EmailAdapter(emails);
        recyclerView.setAdapter(adapter);
    }
}
