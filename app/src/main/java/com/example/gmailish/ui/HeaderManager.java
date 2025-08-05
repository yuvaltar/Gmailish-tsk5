package com.example.gmailish.ui;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.example.gmailish.R;
import com.example.gmailish.model.User;
import com.example.gmailish.ui.inbox.InboxViewModel;
import com.example.gmailish.ui.login.LoginActivity;

public class HeaderManager {

    public static void setup(
            AppCompatActivity activity,
            InboxViewModel    viewModel,
            ImageView         avatarImageView,
            TextView          avatarLetterTextView
    ) {
        // Observe user
        viewModel.getCurrentUserLiveData()
                .observe((LifecycleOwner) activity, user -> {
                    if (user == null) return;

                    // Load image or letter
                    if (user.getPicture() != null && !user.getPicture().isEmpty()) {
                        Glide.with(activity)
                                .load("http://10.0.2.2:3000/api/users/" + user.getId() + "/picture")
                                .circleCrop()
                                .into(avatarImageView);
                        avatarImageView.setVisibility(View.VISIBLE);
                        avatarLetterTextView.setVisibility(View.GONE);
                    } else {
                        avatarImageView.setVisibility(View.GONE);
                        avatarLetterTextView.setVisibility(View.VISIBLE);
                        avatarLetterTextView.setText(
                                user.getUsername().substring(0,1).toUpperCase()
                        );
                    }

                    // Tag for popup
                    avatarImageView.setTag(user);
                    avatarLetterTextView.setTag(user);

                    // Wire clicks *inside* the observer so we have a valid tag
                    View.OnClickListener listener = v -> showProfilePopup(activity, v);
                    avatarImageView.setOnClickListener(listener);
                    avatarLetterTextView.setOnClickListener(listener);
                });
    }

    private static void showProfilePopup(AppCompatActivity activity, View anchor) {
        User user = (User) anchor.getTag();

        View popupView = LayoutInflater.from(activity)
                .inflate(R.layout.popup_profile, null);

        TextView usernameTv = popupView.findViewById(R.id.profileUsername);
        TextView logoutTv   = popupView.findViewById(R.id.logoutBtn);

        usernameTv.setText(user.getUsername());

        PopupWindow popup = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        // Show just below the avatar
        popup.showAsDropDown(anchor, 0, 8, Gravity.END);

        logoutTv.setOnClickListener(v -> {
            SharedPreferences prefs =
                    activity.getSharedPreferences("prefs", MODE_PRIVATE);
            prefs.edit().remove("jwt").apply();

            Intent i = new Intent(activity, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(i);
        });
    }
}
