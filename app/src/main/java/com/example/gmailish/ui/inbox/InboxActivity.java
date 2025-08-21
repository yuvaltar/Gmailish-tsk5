package com.example.gmailish.ui.inbox;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.gmailish.R;
import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.db.AppDbProvider;
import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.sync.PendingSyncManager;
import com.example.gmailish.ui.HeaderManager;
import com.example.gmailish.ui.compose.ComposeActivity;
import com.example.gmailish.util.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@AndroidEntryPoint
public class InboxActivity extends AppCompatActivity {

    private static final String TAG = "InboxActivity";

    @Inject PendingSyncManager pendingSyncManager;

    private static final String LABEL_ALL_INBOXES = "__ALL_INBOXES__";
    private static final String KEY_ALL_INBOXES = "__ALL__";

    private static final String STATE_LABEL   = "state_label";
    private static final String STATE_CHECKED = "state_checked";
    private static final java.util.Set<String> SYSTEM_LABELS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "all inboxes","inbox","primary","promotions","social","updates",
                    "starred","important","sent","drafts","all mail","spam","trash",
                    "outbox","read","unread"
            ));

    private InboxViewModel viewModel;
    private RecyclerView recyclerView;
    private EmailAdapter adapter;
    private MaterialButton composeButton;
    private SwipeRefreshLayout swipeRefresh;
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
        AppCompatDelegate.setDefaultNightMode(
                ThemeManager.isDark(this)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        NavigationView navigationView = findViewById(R.id.navigationView);
        drawerLayout = findViewById(R.id.drawerLayout);
        ImageButton themeToggleTopBar = findViewById(R.id.themeToggleTopBar);
        avatarImageView = findViewById(R.id.avatarImageView);
        avatarLetterTextView = findViewById(R.id.avatarLetterTextView);

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);

        // Restore selection + load correct source (Drafts from local)
        if (savedInstanceState != null) {
            currentLabel  = savedInstanceState.getString(STATE_LABEL, "inbox");
            checkedMenuId = savedInstanceState.getInt(STATE_CHECKED, R.id.nav_primary);
            navigationView.setCheckedItem(checkedMenuId);

            if (LABEL_ALL_INBOXES.equals(currentLabel)) {
                viewModel.loadAllInboxes();
            } else if ("drafts".equalsIgnoreCase(currentLabel)) {
                viewModel.loadEmailsByLabelLocal(currentLabel);
            } else {
                viewModel.loadEmailsByLabel(currentLabel);
            }
        } else {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            currentLabel  = prefs.getString("last_label", "inbox");
            checkedMenuId = prefs.getInt("last_menu_id", R.id.nav_primary);
            navigationView.setCheckedItem(checkedMenuId);
            if (LABEL_ALL_INBOXES.equals(currentLabel)) {
                viewModel.loadAllInboxes();
            } else if ("drafts".equalsIgnoreCase(currentLabel)) {
                viewModel.loadEmailsByLabelLocal(currentLabel);
            } else {
                viewModel.loadEmailsByLabel(currentLabel);
            }
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
            persistSelectionSync(); // commit last selected label/menu

            boolean toDark = !ThemeManager.isDark(this);
            ThemeManager.setMode(this, toDark ? "dark" : "light");

            // Apply immediately so resources are re-inflated with the right theme:
            AppCompatDelegate.setDefaultNightMode(
                    toDark ? AppCompatDelegate.MODE_NIGHT_YES
                           : AppCompatDelegate.MODE_NIGHT_NO
            );

            recreate();
        });

        // Drawer selections (Drafts -> local)
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            Log.d(TAG, "Nav Selected: " + menuItem.getTitle());
            menuItem.setChecked(true);
            drawerLayout.closeDrawer(GravityCompat.START);

            int id = menuItem.getItemId();

            if (id == R.id.nav_all_inboxes) {
                currentLabel = LABEL_ALL_INBOXES;
                checkedMenuId = R.id.nav_all_inboxes;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadAllInboxes();

            } else if (id == R.id.nav_primary) {
                currentLabel = "inbox";
                checkedMenuId = R.id.nav_primary;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_sent) {
                currentLabel = "sent";
                checkedMenuId = R.id.nav_sent;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_drafts) {
                currentLabel = "drafts";
                checkedMenuId = R.id.nav_drafts;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadEmailsByLabelLocal(currentLabel); // <-- local for drafts

            } else if (id == R.id.nav_spam) {
                currentLabel = "spam";
                checkedMenuId = R.id.nav_spam;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_trash) {
                currentLabel = "trash";
                checkedMenuId = R.id.nav_trash;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_starred) {
                currentLabel = "starred";
                checkedMenuId = R.id.nav_starred;
                persistSelection();
                adapter.setCurrentLabel(currentLabel);
                viewModel.loadEmailsByLabel(currentLabel);

            } else if (id == R.id.nav_create_label) {
                startActivity(new Intent(this, CreateLabelActivity.class));

            } else {
                // Dynamic labels
                CharSequence title = menuItem.getTitle();
                if (title != null) {
                    currentLabel = title.toString();
                    checkedMenuId = id;
                    persistSelection();
                    adapter.setCurrentLabel(currentLabel);
                    viewModel.loadEmailsByLabel(currentLabel);
                }
            }
            return true;
        });

        // Badges + unread counts
        navigationView.setCheckedItem(checkedMenuId);
        Menu menu = navigationView.getMenu();
        setBadge(menu.findItem(R.id.nav_primary), "99+", 0xFFE6EDF6);
        setBadge(menu.findItem(R.id.nav_promotions), "26 new", 0xFFBFE6C8);
        setBadge(menu.findItem(R.id.nav_social), "27 new", 0xFFD5E4FF);
        setBadge(menu.findItem(R.id.nav_updates), "82 new", 0xFFFFE2CC);
        viewModel.getUnreadCounts().observe(this, counts -> {
            if (counts == null) return;
            Integer allC = counts.get(KEY_ALL_INBOXES);
            setNumericBadge(menu.findItem(R.id.nav_all_inboxes), allC != null ? allC : 0, null);
            applyCount(menu.findItem(R.id.nav_primary), counts.getOrDefault("primary", 0));
            applyCount(menu.findItem(R.id.nav_promotions), counts.getOrDefault("promotions", 0));
            applyCount(menu.findItem(R.id.nav_social),     counts.getOrDefault("social", 0));
            applyCount(menu.findItem(R.id.nav_updates),    counts.getOrDefault("updates", 0));
            applyCount(menu.findItem(R.id.nav_starred),    counts.getOrDefault("starred", 0));
            applyCount(menu.findItem(R.id.nav_important),  counts.getOrDefault("important", 0));
            applyCount(menu.findItem(R.id.nav_sent),       counts.getOrDefault("sent", 0));
            applyCount(menu.findItem(R.id.nav_drafts),     counts.getOrDefault("drafts", 0));
            applyCount(menu.findItem(R.id.nav_spam),       counts.getOrDefault("spam", 0));
            applyCount(menu.findItem(R.id.nav_trash),      counts.getOrDefault("trash", 0));
            // Dynamic labels
            for (int i = 0; i < menu.size(); i++) {
                MenuItem it = menu.getItem(i);
                if (it.getGroupId() == R.id.dynamic_labels_group) {
                    String labelName = it.getTitle().toString().toLowerCase();
                    int c = counts.getOrDefault(labelName, 0);
                    setNumericBadge(it, c, null);
                }
            }
        });

        // Dynamic labels
        loadUserLabels(navigationView);
        loadLocalLabels(navigationView);
        viewModel.refreshUnreadCounts();

        // Recycler
        recyclerView = findViewById(R.id.inboxRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(true);
        adapter = new EmailAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setCurrentLabel(currentLabel); // reflect initial/current tab

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String token = prefs.getString("jwt", null);

            // Flush pending ops in background (if online)
            if (token != null && isOnline()) {
                new Thread(() -> pendingSyncManager.flush(token)).start();
            }

            // Reload only the current view
            if (LABEL_ALL_INBOXES.equals(currentLabel)) {
                viewModel.loadAllInboxes();
            } else if ("drafts".equalsIgnoreCase(currentLabel)) {
                viewModel.loadEmailsByLabelLocal(currentLabel); // keep drafts local on refresh
            } else {
                viewModel.loadEmailsByLabel(currentLabel);
            }
            viewModel.refreshUnreadCounts();
        });

        // Observe emails: update list and stop spinner
        viewModel.getEmails().observe(this, emails -> {
            adapter.updateData(emails);
            if (swipeRefresh.isRefreshing()) swipeRefresh.setRefreshing(false);
        });

        // Compose
        composeButton = findViewById(R.id.composeButton);
        composeButton.setOnClickListener(v ->
                startActivity(new Intent(this, ComposeActivity.class)));

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

        // Errors
        viewModel.getError().observe(this, msg -> {
            if (swipeRefresh.isRefreshing()) swipeRefresh.setRefreshing(false);
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
            btn.setImageResource(R.drawable.ic_light_mode_24);
        } else {
            btn.setImageResource(R.drawable.ic_dark_mode_24);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        NavigationView navigationView = findViewById(R.id.navigationView);
        loadUserLabels(navigationView);
        loadLocalLabels(navigationView);
        viewModel.refreshUnreadCounts();

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);

        if (token != null && isOnline()) {
            Log.d(TAG, "onResume: online -> flushing pending ops");
            new Thread(() -> {
                try {
                    pendingSyncManager.flush(token);
                } catch (Exception e) {
                    Log.e(TAG, "pending flush error: " + e.getMessage(), e);
                }
            }).start();
        }

        if (token != null) {
            // Optional: do a silent cache sync here instead of posting to the UI
            if (LABEL_ALL_INBOXES.equals(currentLabel)) {
                viewModel.loadAllInboxes();
            } else {
                viewModel.loadEmailsByLabel(currentLabel);
            }
        }
    }

    private void loadLabelInbox(String chosen) {
        Log.d(TAG, "loadLabelInbox: chosen=" + chosen);
        viewModel.loadEmailsByLabelLocal(chosen);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String token = prefs.getString("jwt", null);
        if (token != null && isOnline()) {
            Log.d(TAG, "loadLabelInbox: online -> fetching remote for " + chosen);
            viewModel.loadEmailsByLabel(chosen);
        } else {
            Log.d(TAG, "loadLabelInbox: offline or missing token -> local only");
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
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

                String json = response.body() != null ? response.body().string() : "[]";

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
                        java.util.HashSet<String> seen = new java.util.HashSet<>(SYSTEM_LABELS);
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                JSONObject labelObj = array.getJSONObject(i);
                                String raw = labelObj.optString("name", "");
                                if (raw.isEmpty()) continue;

                                String lc = raw.toLowerCase();
                                if (SYSTEM_LABELS.contains(lc)) continue;      // skip core/system labels
                                if (!seen.add(lc)) continue;                   // skip duplicates

                                MenuItem item = menu.add(R.id.dynamic_labels_group, Menu.NONE, Menu.NONE, raw);
                                item.setIcon(R.drawable.ic_label);
                                item.setCheckable(true);
                                item.setActionView(R.layout.menu_badge);
                                setNumericBadge(item, 0, null);
                            } catch (Exception e) {
                                Log.e("Labels", "Error parsing label: " + e.getMessage());
                            }
                        }
                        loadLocalLabels(navigationView);
                    });
                } catch (Exception e) {
                    Log.e("Labels", "Parse error: " + e.getMessage());
                }
            }
        });
    }

    private void loadLocalLabels(NavigationView navigationView) {
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String ownerId = prefs.getString("user_id", null);
        if (ownerId == null) return;

        new Thread(() -> {
            try {
                AppDatabase db = AppDbProvider.get(getApplicationContext());
                List<LabelEntity> localLabels = db.labelDao().getAllByOwner(ownerId);
                if (localLabels == null || localLabels.isEmpty()) return;

                runOnUiThread(() -> {
                    Menu menu = navigationView.getMenu();
                    HashSet<String> existing = new HashSet<>();

                    // 1) Seed with ALL current dynamic titles
                    for (int i = 0; i < menu.size(); i++) {
                        MenuItem item = menu.getItem(i);
                        if (item != null && item.getGroupId() == R.id.dynamic_labels_group && item.getTitle() != null) {
                            existing.add(item.getTitle().toString().toLowerCase());
                        }
                    }
                    // 2) Seed with system labels so they NEVER get added to dynamic
                    existing.addAll(SYSTEM_LABELS);

                    // 3) Add only truly custom labels from local DB
                    for (LabelEntity le : localLabels) {
                        String title = le.name != null ? le.name.trim() : "";
                        if (title.isEmpty()) continue;

                        String lc = title.toLowerCase();
                        if (existing.contains(lc)) continue; // skip system + duplicates

                        MenuItem item = menu.add(R.id.dynamic_labels_group, Menu.NONE, Menu.NONE, title);
                        item.setIcon(R.drawable.ic_label);
                        item.setCheckable(true);
                        item.setActionView(R.layout.menu_badge);
                        setNumericBadge(item, 0, null);
                        existing.add(lc); // prevent further dupes in same run
                    }
                });
            } catch (Exception ignored) { }
        }).start();
    }

    private void persistSelection() {
        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit()
                .putString("last_label", currentLabel)
                .putInt("last_menu_id", checkedMenuId)
                .apply();
    }

    private void setNumericBadge(MenuItem item, int count, Integer bgColorOrNull) {
        if (item == null) return;

        View v = item.getActionView();
        TextView tv;
        if (!(v instanceof TextView)) {
            item.setActionView(R.layout.menu_badge);
            v = item.getActionView();
        }
        tv = (TextView) v;

        if (count <= 0) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(String.valueOf(count));
            if (bgColorOrNull != null) {
                tv.getBackground().setTint(bgColorOrNull);
            }
            tv.setVisibility(View.VISIBLE);
        }
    }

    private void applyCount(MenuItem item, int count) {
        setNumericBadge(item, count, null);
    }

    private void persistSelectionSync() {
        SharedPreferences.Editor ed = getSharedPreferences("prefs", MODE_PRIVATE).edit();
        ed.putString("last_label", currentLabel);
        ed.putInt("last_menu_id", checkedMenuId);
        ed.apply(); // sync
    }
}
