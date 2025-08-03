package com.example.gmailish.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;
import com.example.gmailish.ui.inbox.InboxActivity;
import com.example.gmailish.ui.register.RegisterActivity;

public class LoginActivity extends AppCompatActivity {
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText editEmail = findViewById(R.id.editEmail);
        EditText editPassword = findViewById(R.id.editPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView textRegister = findViewById(R.id.textRegisterLink);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        btnLogin.setOnClickListener(v -> {
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString();

            viewModel.login(email, password);
        });

        viewModel.loginResult.observe(this, success -> {
            if (success) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                // TODO: Replace InboxActivity with your real inbox screen
                Intent intent = new Intent(this, InboxActivity.class);
                startActivity(intent);
                finish();
            }
        });

        viewModel.error.observe(this, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        );

        textRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}
