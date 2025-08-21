package com.example.gmailish.ui.inbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gmailish.R;
import com.example.gmailish.data.dao.PendingOperationDao;
import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.db.AppDbProvider;
import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.PendingOperationEntity;
import com.example.gmailish.data.model.PendingOperationType;
import com.example.gmailish.data.repository.LabelRepository;
import com.example.gmailish.data.sync.SyncPendingWorker;

import org.json.JSONObject;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CreateLabelActivity extends AppCompatActivity {


    private EditText labelInput;
    private Button saveButton;
    private Button cancelButton;

    @Inject LabelRepository labelRepository;

    private PendingOperationDao pendingDao;
    private ExecutorService ioExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_label);

        labelInput = findViewById(R.id.labelInput);
        saveButton = findViewById(R.id.saveLabelButton);
        cancelButton = findViewById(R.id.cancelLabelButton);

        AppDatabase db = DbHolder.getInstance(getApplicationContext());
        pendingDao = db.pendingOperationDao();
        ioExecutor = Executors.newSingleThreadExecutor();

        cancelButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> onSaveClicked());
    }

    private void onSaveClicked() {
        String name = labelInput.getText() != null ? labelInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Label name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String ownerId = getOwnerIdFromPrefs();
        if (TextUtils.isEmpty(ownerId)) {
            Toast.makeText(this, "Missing owner id. Please sign in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        String localId = "local-" + UUID.randomUUID();
        LabelEntity local = new LabelEntity(localId, ownerId, name);

        ioExecutor.execute(() -> {
            try {
                labelRepository.saveLabel(local);

                JSONObject payload = new JSONObject();
                payload.put("localId", localId);
                payload.put("ownerId", ownerId);
                payload.put("name", name);

                PendingOperationEntity op = new PendingOperationEntity(
                        UUID.randomUUID().toString(),
                        PendingOperationType.LABEL_CREATE,
                        payload.toString(),
                        new Date(System.currentTimeMillis()),
                        0,
                        "PENDING",
                        localId
                );

                pendingDao.upsert(op);
                SyncPendingWorker.enqueue(getApplicationContext());

                runOnUiThread(() -> {
                    Toast.makeText(this, "Label created (queued for sync)", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to save label offline", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private String getOwnerIdFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        return prefs.getString("user_id", null);
    }

    private static final class DbHolder {
        static AppDatabase getInstance(Context context) {
            return AppDbProvider.get(context.getApplicationContext());
        }
    }
}