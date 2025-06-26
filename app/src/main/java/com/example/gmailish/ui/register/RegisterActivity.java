package com.example.gmailish.ui.register;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;


import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;
import com.example.gmailish.ui.login.LoginActivity;

import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {
    private RegisterViewModel viewModel;
    private ProgressBar progressBar;

    // Step containers
    private LinearLayout welcomeBlock, stepName, stepDobGender, stepUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setProgress(0);


        // Initialize step blocks
        welcomeBlock = findViewById(R.id.welcome_block);
        stepName = findViewById(R.id.step_name);
        stepDobGender = findViewById(R.id.step_dob_gender);
        stepUsername = findViewById(R.id.step_username);

        // Spinner setup
        MaterialAutoCompleteTextView genderSpinner = findViewById(R.id.spinnerGender);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.gender_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(adapter);

        // Inputs
        EditText firstNameInput = findViewById(R.id.editFirstName);
        EditText lastNameInput = findViewById(R.id.editLastName);
        EditText dobInput = findViewById(R.id.editDob);
        dobInput.setFocusable(false);
        dobInput.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    RegisterActivity.this,
                    (view, year1, month1, dayOfMonth) -> {
                        String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month1 + 1, year1);
                        dobInput.setText(selectedDate);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });
        EditText usernameInput = findViewById(R.id.editUsername);
        EditText passwordInput = findViewById(R.id.editPassword);
        EditText confirmInput = findViewById(R.id.editConfirmPassword);

        // Buttons
        Button startRegister  = findViewById(R.id.button_start_register);
        Button next1 = findViewById(R.id.next1);
        Button next2 = findViewById(R.id.next2);
        Button submitButton = findViewById(R.id.submitRegistration);

        // Step 0 → Step 1
        startRegister.setOnClickListener(v -> {
            welcomeBlock.setVisibility(View.GONE);
            stepName.setVisibility(View.VISIBLE);
            progressBar.setProgress(1);
        });

        // Step 1 → Step 2
        next1.setOnClickListener(v -> {
            viewModel.setName(
                    firstNameInput.getText().toString(),
                    lastNameInput.getText().toString()
            );
            if (viewModel.firstNameError.getValue() == null &&
                    viewModel.lastNameError.getValue() == null) {
                stepName.setVisibility(View.GONE);
                stepDobGender.setVisibility(View.VISIBLE);
            }
            progressBar.setProgress(2);
            stepName.setVisibility(View.GONE);
            stepDobGender.setVisibility(View.VISIBLE);
        });

        // Step 2 → Step 3
        next2.setOnClickListener(v -> {
            viewModel.setDobAndGender(
                    dobInput.getText().toString(),
                    genderSpinner.getText() != null ? genderSpinner.getText().toString() : ""
            );
            progressBar.setProgress(3);
            stepDobGender.setVisibility(View.GONE);
            stepUsername.setVisibility(View.VISIBLE);
        });

        // Submit
        submitButton.setOnClickListener(v -> {
            String user = usernameInput.getText().toString();
            String pwd = passwordInput.getText().toString();
            String confirm = confirmInput.getText().toString();
            viewModel.setUsername(user);
            viewModel.setPassword(pwd);
            viewModel.register(confirm);
        });

        // Observers
        viewModel.firstNameError.observe(this, err -> {
            if (err != null) firstNameInput.setError(err);
        });

        viewModel.lastNameError.observe(this, err -> {
            if (err != null) lastNameInput.setError(err);
        });

        viewModel.dobError.observe(this, err -> {
            if (err != null) dobInput.setError(err);
        });

        viewModel.passwordError.observe(this, err -> {
            if (err != null) passwordInput.setError(err);
        });

        viewModel.message.observe(this, msg -> {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.registrationSuccess.observe(this, success -> {
            if (success) {
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
            }
        });
    }
}
