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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gmailish.R;
import com.example.gmailish.model.Email;
import com.example.gmailish.ui.compose.ComposeActivity;
import com.google.android.material.navigation.NavigationView;

import java.util.List;

public class InboxActivity extends AppCompatActivity {

    private InboxViewModel viewModel;
    private RecyclerView recyclerView;
    private EmailAdapter adapter;
    private Button composeButton;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

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
            @Override public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        NavigationView navigationView = findViewById(R.id.navigationView);

        navigationView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();

            if (id == R.id.nav_primary) {
                Log.d("Nav","Primary");
            } else if (id == R.id.nav_promotions) {
                Log.d("Nav","Promotions");
            } else if (id == R.id.nav_social) {
                Log.d("Nav","Social");
            } else if (id == R.id.nav_updates) {
                Log.d("Nav","Updates");
            } else if (id == R.id.nav_starred) {
                Log.d("Nav","Starred");
            } else if (id == R.id.nav_important) {
                Log.d("Nav","Important");
            } else if (id == R.id.nav_sent) {
                Log.d("Nav","Sent");
            } else if (id == R.id.nav_drafts) {
                Log.d("Nav","Drafts");
            } else if (id == R.id.nav_all_mail) {
                Log.d("Nav","All mail");
            } else if (id == R.id.nav_spam) {
                Log.d("Nav","Spam");
            } else if (id == R.id.nav_trash) {
                Log.d("Nav","Trash");
            } else if (id == R.id.nav_calendar) {
                Log.d("Nav","Calendar");
            } else if (id == R.id.nav_contacts) {
                Log.d("Nav","Contacts");
            } else if (id == R.id.nav_settings) {
                Log.d("Nav","Settings");
            } else if (id == R.id.nav_help) {
                Log.d("Nav","Help & feedback");
            }

            menuItem.setChecked(true);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Default selected item (pill highlight)
        navigationView.setCheckedItem(R.id.nav_primary);

        // Right-side badges (items with actionLayout="@layout/menu_badge")
        Menu menu = navigationView.getMenu();
        setBadge(menu.findItem(R.id.nav_primary),    "99+");
        setBadge(menu.findItem(R.id.nav_promotions), "26 new", 0xFFBFE6C8);
        setBadge(menu.findItem(R.id.nav_social),     "27 new", 0xFFD5E4FF);
        setBadge(menu.findItem(R.id.nav_updates),    "82 new", 0xFFFFE2CC);

        recyclerView = findViewById(R.id.inboxRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        composeButton = findViewById(R.id.composeButton);
        composeButton.setOnClickListener(v ->
                startActivity(new Intent(this, ComposeActivity.class)));

        EditText searchBar = findViewById(R.id.searchBar);
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
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
        viewModel.getEmails().observe(this, this::displayEmails);
        viewModel.getError().observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show());

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

    // Badge helpers
    private void setBadge(MenuItem item, String text) {
        setBadge(item, text, 0xFFE6EDF6);
    }

    private void setBadge(MenuItem item, String text, int bgColor) {
        if (item == null) return;
        View v = item.getActionView(); // requires android:actionLayout="@layout/menu_badge"
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setText(text);
            tv.getBackground().setTint(bgColor);
            tv.setVisibility(View.VISIBLE);
        }
    }
}
