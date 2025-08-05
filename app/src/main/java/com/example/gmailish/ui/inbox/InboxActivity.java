package com.example.gmailish.ui.inbox;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.gmailish.ui.HeaderManager;
import com.example.gmailish.ui.inbox.CreateLabelActivity;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gmailish.R;
import com.example.gmailish.model.Email;
import com.example.gmailish.ui.compose.ComposeActivity;
import com.google.android.material.navigation.NavigationView;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class InboxActivity extends AppCompatActivity {

    private InboxViewModel viewModel;
    private RecyclerView recyclerView;
    private EmailAdapter adapter;
    private Button composeButton;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private ImageView avatarImageView;
    private TextView avatarLetterTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        avatarImageView = findViewById(R.id.avatarImageView);
        avatarLetterTextView = findViewById(R.id.avatarLetterTextView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        ImageView hamburgerIcon = findViewById(R.id.hamburgerIcon);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        hamburgerIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        NavigationView navigationView = findViewById(R.id.navigationView);
        View header = navigationView.getHeaderView(0);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top + dp(24), v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        navigationView.setNavigationItemSelectedListener(menuItem -> {
            Log.d("Nav", "Selected: " + menuItem.getTitle());
            menuItem.setChecked(true);
            drawerLayout.closeDrawer(GravityCompat.START);

            int id = menuItem.getItemId();

            if (id == R.id.nav_primary) {
                viewModel.loadEmailsByLabel("inbox");
            } else if (id == R.id.nav_sent) {
                viewModel.loadEmailsByLabel("sent");
            } else if (id == R.id.nav_drafts) {
                viewModel.loadEmailsByLabel("drafts");
            } else if (id == R.id.nav_spam) {
                viewModel.loadEmailsByLabel("spam");
            } else if (id == R.id.nav_trash) {
                viewModel.loadEmailsByLabel("trash");
            } else if (id == R.id.nav_starred) {
                viewModel.loadEmailsByLabel("starred");
            } else if (id == R.id.nav_create_label) {
                startActivity(new Intent(this, CreateLabelActivity.class));
            } else {
                // Handle dynamic label
                CharSequence title = menuItem.getTitle();
                if (title != null) {
                    viewModel.loadEmailsByLabel(title.toString().toLowerCase());
                }
            }

            return true;
        });

        navigationView.setCheckedItem(R.id.nav_primary);
        Menu menu = navigationView.getMenu();
        setBadge(menu.findItem(R.id.nav_primary), "99+", 0xFFE6EDF6);
        setBadge(menu.findItem(R.id.nav_promotions), "26 new", 0xFFBFE6C8);
        setBadge(menu.findItem(R.id.nav_social), "27 new", 0xFFD5E4FF);
        setBadge(menu.findItem(R.id.nav_updates), "82 new", 0xFFFFE2CC);

        loadUserLabels(navigationView);

        recyclerView = findViewById(R.id.inboxRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmailAdapter();
        recyclerView.setAdapter(adapter);

        composeButton = findViewById(R.id.composeButton);
        composeButton.setOnClickListener(v ->
                startActivity(new Intent(this, ComposeActivity.class)));

        EditText searchBar = findViewById(R.id.searchBar);
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            Log.d("Search", "Search triggered with actionId=" + actionId);
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                String query = v.getText().toString().trim();
                if (!query.isEmpty()) {
                    viewModel.searchEmails(query);
                }
                return true;
            }
            return false;
        });

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        viewModel.getEmails().observe(this, adapter::updateData);
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        HeaderManager.setup(
                this,
                viewModel,
                avatarImageView,
                avatarLetterTextView
        );

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);

        if (token != null) {
            viewModel.loadEmails(token);
            viewModel.loadCurrentUser(token);

        } else {
            Toast.makeText(this, "JWT missing!", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();

        // Reload labels
        NavigationView navigationView = findViewById(R.id.navigationView);
        loadUserLabels(navigationView);

        // ✅ Reload inbox mails
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        if (token != null) {
            viewModel.loadEmails(token);
        }
    }


    private void setBadge(MenuItem item, String text, int bgColor) {
        if (item == null) return;
        View v = item.getActionView();
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setText(text);
            tv.getBackground().setTint(bgColor);
            tv.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void loadUserLabels(NavigationView navigationView) {
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        if (token == null) return;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("http://10.0.2.2:3000/api/labels")
                .header("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("Labels", "Failed to fetch labels: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("Labels", "Failed: " + response.code());
                    return;
                }

                String json = response.body().string();
                Log.d("Labels", "Got: " + json);

                try {
                    JSONArray array = new JSONArray(json);
                    runOnUiThread(() -> {
                        Menu menu = navigationView.getMenu();

                        // 🚫 Clear previous dynamic labels to avoid duplicates
                        menu.removeGroup(R.id.dynamic_labels_group);

                        // Add each label to the correct group
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                JSONObject labelObj = array.getJSONObject(i);
                                String label = labelObj.getString("name");

                                MenuItem item = menu.add(R.id.dynamic_labels_group, Menu.NONE, Menu.NONE, label);
                                item.setIcon(R.drawable.ic_label);
                                item.setCheckable(true);
                            } catch (Exception e) {
                                Log.e("Labels", "Error parsing label at index " + i + ": " + e.getMessage());
                            }
                        }
                    });

                } catch (Exception e) {
                    Log.e("Labels", "Parse error: " + e.getMessage());
                }
            }
        });
    }

}
