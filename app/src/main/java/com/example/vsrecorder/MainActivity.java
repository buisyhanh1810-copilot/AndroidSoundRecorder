package com.example.vsrecorder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;
    private TextView statusText;
    private Button startButton, stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

        startButton.setOnClickListener(v -> {
            if (hasPermissions()) {
                startRecording();
            } else {
                requestPermissions();
            }
        });

        stopButton.setOnClickListener(v -> stopRecording());
    }

    private boolean hasPermissions() {
        boolean audioOk = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean notifOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifOk = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        boolean storageOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // "All files access" is required to write into the shared Documents folder
            // so the separate Analyzer app can read it too.
            storageOk = Environment.isExternalStorageManager();
        }
        return audioOk && notifOk && storageOk;
    }

    private void requestPermissions() {
        java.util.List<String> perms = new java.util.ArrayList<>();
        perms.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERMISSION_REQUEST_CODE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && hasPermissions()) {
            startRecording();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE && hasPermissions()) {
            startRecording();
        } else if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            statusText.setText("Storage access required to save recordings where the Analyzer app can read them");
        }
    }

    private void startRecording() {
        Intent intent = new Intent(this, RecordingService.class);
        intent.setAction(RecordingService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
        statusText.setText("Recording...");
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
    }

    private void stopRecording() {
        Intent intent = new Intent(this, RecordingService.class);
        intent.setAction(RecordingService.ACTION_STOP);
        startService(intent);
        statusText.setText("Idle");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }
}
