package com.example.gmailish.ui.inbox;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
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
import com.example.gmailish.ui.HeaderManager;
import com.example.gmailish.ui.compose.ComposeActivity;
import com.example.gmailish.util.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class InboxActivity extends AppCompatActivity {

    private static final String TAG = "InboxActivity";
    private static final String STATE_LABEL   = "state_label";
    private static final String STATE_CHECKED = "state_checked";

    private InboxViewModel viewModel;
    private RecyclerView recyclerView;
    private EmailAdapter adapter;
    private MaterialButton composeButton;
    private MaterialButton refreshButton;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private ImageView avatarImageView;
    private TextView avatarLetterTextView;

    private String currentLabel = "inbox";
    private int checkedMenuId   = R.id.nav_primary;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_LABEL, currentLabel);
        outState.putInt(STATE_CHECKED, checkedMenuId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        NavigationView navigationView = findViewById(R.id.navigationView);
        drawerLayout = findViewById(R.id.drawerLayout);
        ImageButton themeToggleTopBar = findViewById(R.id.themeToggleTopBar);
        avatarImageView = findViewById(R.id.avatarImageView);
        avatarLetterTextView = findViewById(R.id.avatarLetterTextView);

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);

        // Restore selection
        if (savedInstanceState != null) {
            currentLabel  = savedInstanceState.getString(STATE_LABEL, "inbox");
            checkedMenuId = savedInstanceState.getInt(STATE_CHECKED, R.id.nav_primary);
            navigationView.setCheckedItem(checkedMenuId);
            viewModel.loadEmailsByLabel(currentLabel);
        } else {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            currentLabel  = prefs.getString("last_label", "inbox");
            checkedMenuId = prefs.getInt("last_menu_id", R.id.nav_primary);
            navigationView.setCheckedItem(checkedMenuId);
            viewModel.loadEmailsByLabel(currentLabel);
        }

        // Status bar icon contrast based on theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (ThemeManager.isDark(this)) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        // Drawer + hamburger
        ImageView hamburgerIcon = findViewById(R.id.hamburgerIcon);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        hamburgerIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Back press closes drawer first
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Header window inset padding
        View header = navigationView.getHeaderView(0);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top + dp(24), v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        // Theme toggle
        updateThemeIcon(themeToggleTopBar);
        themeToggleTopBar.setOnClickListener(v -> {
            persistSelection(); // keep current location across recreate
            String next = ThemeManager.isDark(this) ? "light" : "dark";
            ThemeManager.setMode(this, next);
            recreate();
        });

        // Drawer selections
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            Log.d("Nav", "Selected: " + menuItem.getTitle());
            menuItem.setChecked(true);
            drawerLayout.closeDrawer(GravityCompat.START);

            int id = menuItem.getItemId();
            if (id == R.id.nav_primary) {
                currentLabel = "inbox";
                checkedMenuId = R.id.nav_primary;
                persistSelection();
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_sent) {
                currentLabel = "sent";
                checkedMenuId = R.id.nav_sent;
                persistSelection();
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_drafts) {
                currentLabel = "drafts";
                checkedMenuId = R.id.nav_drafts;
                persistSelection();
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_spam) {
                currentLabel = "spam";
                checkedMenuId = R.id.nav_spam;
                persistSelection();
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_trash) {
                currentLabel = "trash";
                checkedMenuId = R.id.nav_trash;
                persistSelection();
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_starred) {
                currentLabel = "starred";
                checkedMenuId = R.id.nav_starred;
                persistSelection();
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_create_label) {
                startActivity(new Intent(this, CreateLabelActivity.class));

            } else {
                // Dynamic labels (id may be Menu.NONE)
                CharSequence title = menuItem.getTitle();
                if (title != null) {
                    currentLabel = title.toString();
                    checkedMenuId = id; // may be 0 for dynamic items; okay
                    persistSelection();
                    viewModel.loadEmailsByLabel(currentLabel);
                }
            }
            return true;
        });

        // Badges (if your menu items have an actionView TextView)
        navigationView.setCheckedItem(checkedMenuId);
        Menu menu = navigationView.getMenu();
        setBadge(menu.findItem(R.id.nav_primary), "99+", 0xFFE6EDF6);
        setBadge(menu.findItem(R.id.nav_promotions), "26 new", 0xFFBFE6C8);
        setBadge(menu.findItem(R.id.nav_social), "27 new", 0xFFD5E4FF);
        setBadge(menu.findItem(R.id.nav_updates), "82 new", 0xFFFFE2CC);

        // Dynamic labels
        loadUserLabels(navigationView);

        // Recycler
        recyclerView = findViewById(R.id.inboxRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(true);
        adapter = new EmailAdapter();
        recyclerView.setAdapter(adapter);

        // Compose & Refresh
        composeButton = findViewById(R.id.composeButton);
        composeButton.setOnClickListener(v ->
                startActivity(new Intent(this, ComposeActivity.class)));

        refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String token = prefs.getString("jwt", null);
            if (token != null) {
                viewModel.loadEmails(token);
                Toast.makeText(this, "Refreshing emails...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show();
            }
        });

        // Search
        EditText searchBar = findViewById(R.id.searchBar);
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                String query = v.getText().toString().trim();
                if (!query.isEmpty()) viewModel.searchEmails(query);
                return true;
            }
            return false;
        });

        // Observe VM
        viewModel.getEmails().observe(this, adapter::updateData);
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Header avatar/user popup
        HeaderManager.setup(this, viewModel, avatarImageView, avatarLetterTextView);

        // Load current user (don’t force a full mailbox reload here)
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        if (token != null) {
            viewModel.loadCurrentUser(token);
        } else {
            Toast.makeText(this, "JWT missing!", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateThemeIcon(ImageButton btn) {
        if (ThemeManager.isDark(this)) {
            btn.setImageResource(R.drawable.ic_light_mode_24); // sun when currently dark
        } else {
            btn.setImageResource(R.drawable.ic_dark_mode_24);  // moon when currently light
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild dynamic labels (optional)
        NavigationView navigationView = findViewById(R.id.navigationView);
        loadUserLabels(navigationView);
        // No auto reload of emails here to avoid stomping current filtered list
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
            @Override public void onFailure(Call call, IOException e) {
                Log.e("Labels", "Failed to fetch labels: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("Labels", "Failed: " + response.code());
                    return;
                }
                String json = response.body().string();
                Log.d("Labels", "Got: " + json);
                try {
                    JSONArray array = new JSONArray(json);
                    ArrayList<String> names = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        names.add(o.optString("name"));
                    }
                    getSharedPreferences("prefs", MODE_PRIVATE)
                            .edit()
                            .putString("cached_label_names", new JSONArray(names).toString())
                            .apply();
                    runOnUiThread(() -> {
                        Menu menu = navigationView.getMenu();
                        menu.removeGroup(R.id.dynamic_labels_group);
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                JSONObject labelObj = array.getJSONObject(i);
                                String label = labelObj.getString("name");
                                MenuItem item = menu.add(R.id.dynamic_labels_group, Menu.NONE, Menu.NONE, label);
                                item.setIcon(R.drawable.ic_label);
                                item.setCheckable(true);
                            } catch (Exception e) {
                                Log.e("Labels", "Error parsing label: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e("Labels", "Parse error: " + e.getMessage());
                }
            }
        });
    }

    private void persistSelection() {
        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit()
                .putString("last_label", currentLabel)
                .putInt("last_menu_id", checkedMenuId)
                .apply();
    }
}
