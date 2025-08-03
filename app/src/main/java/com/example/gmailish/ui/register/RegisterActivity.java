//registerActivity
package com.example.gmailish.ui.register;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gmailish.R;
import com.example.gmailish.ui.login.LoginActivity;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private RegisterViewModel viewModel;
    private ProgressBar progressBar;

    private LinearLayout welcomeBlock, stepName, stepDobGender, stepUsername;

    // UI Elements
    private EditText firstNameInput, lastNameInput, dobInput, usernameInput, passwordInput, confirmInput;
    private MaterialAutoCompleteTextView genderSpinner;

    private Date selectedDob = null;

    private ImageView imagePreview;
    private Button selectImageButton;
    private Uri selectedImageUri = null;

    private File imageFile;
    private static final int PICK_IMAGE_REQUEST = 1;
    private ActivityResultLauncher<Intent> imagePickerLauncher;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setProgress(0);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        imagePreview.setImageURI(selectedImageUri);
                    }
                }
        );

        // Initialize views
        initViews();

        // Set up gender spinner
        setupGenderSpinner();

        // Set up date picker
        setupDatePicker();

        // Set up buttons
        setupButtonListeners();

        // Set up observers
        setupObservers();
    }

    @SuppressLint("WrongViewCast")
    private void initViews() {
        welcomeBlock = findViewById(R.id.welcome_block);
        stepName = findViewById(R.id.step_name);
        stepDobGender = findViewById(R.id.step_dob_gender);
        stepUsername = findViewById(R.id.step_username);

        firstNameInput = findViewById(R.id.editFirstName);
        lastNameInput = findViewById(R.id.editLastName);
        dobInput = findViewById(R.id.editDob);
        dobInput.setFocusable(false);

        usernameInput = findViewById(R.id.editUsername);
        passwordInput = findViewById(R.id.editPassword);
        confirmInput = findViewById(R.id.editConfirmPassword);

        genderSpinner = findViewById(R.id.spinnerGender);
        imagePreview = findViewById(R.id.imagePreview);
        selectImageButton = findViewById(R.id.button_select_image);

    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGenderSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.gender_array,
                android.R.layout.simple_dropdown_item_1line
        );

        genderSpinner.setAdapter(adapter);

        genderSpinner.setOnTouchListener((v, event) -> {
            genderSpinner.showDropDown();
            return true;
        });
    }

    private void setupDatePicker() {
        dobInput.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    RegisterActivity.this,
                    (view, year1, month1, dayOfMonth) -> {
                        Calendar selectedCal = Calendar.getInstance();
                        selectedCal.set(year1, month1, dayOfMonth);
                        selectedDob = selectedCal.getTime();

                        // Format for display
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        dobInput.setText(sdf.format(selectedDob));
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            imagePreview.setImageURI(selectedImageUri);
        }
    }


    private void setupButtonListeners() {
        Button startRegister = findViewById(R.id.button_start_register);
        Button next1 = findViewById(R.id.next1);
        Button next2 = findViewById(R.id.next2);
        Button submitButton = findViewById(R.id.submitRegistration);

        startRegister.setOnClickListener(v -> {
            welcomeBlock.setVisibility(View.GONE);
            stepName.setVisibility(View.VISIBLE);
            progressBar.setProgress(1);
        });

        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });


        next1.setOnClickListener(v -> {
            boolean valid = viewModel.setName(
                    firstNameInput.getText().toString(),
                    lastNameInput.getText().toString()
            );
            if (valid) {
                if (selectedImageUri == null) {
                    Toast.makeText(this, "Please select a profile image", Toast.LENGTH_SHORT).show();
                    return;
                }
                imageFile = getFileFromUri(selectedImageUri);
                if (imageFile == null) {
                    Toast.makeText(this, "Image could not be processed", Toast.LENGTH_SHORT).show();
                    return;
                }
                stepName.setVisibility(View.GONE);
                stepDobGender.setVisibility(View.VISIBLE);
                progressBar.setProgress(2);
            }
        });

        next2.setOnClickListener(v -> {
            String gender = genderSpinner.getText() != null ? genderSpinner.getText().toString() : "";
            boolean valid = viewModel.setDobAndGender(selectedDob, gender);
            if (valid) {
                stepDobGender.setVisibility(View.GONE);
                stepUsername.setVisibility(View.VISIBLE);
                progressBar.setProgress(3);
            }
        });

        submitButton.setOnClickListener(v -> {
            boolean valid = viewModel.setUsernameAndPassword(
                    usernameInput.getText().toString(),
                    passwordInput.getText().toString(),
                    confirmInput.getText().toString()
            );
            if (valid) {
                viewModel.register(imageFile);
            }
        });
    }

    private File getFileFromUri(Uri uri) {
        File file = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                String fileName = cursor.getString(index);
                file = new File(getCacheDir(), fileName);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }


    private void setupObservers() {
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
