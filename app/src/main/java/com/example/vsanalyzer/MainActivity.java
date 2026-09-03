package com.example.vsanalyzer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.vsanalyzer.analysis.SessionAnalyzer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int MANAGE_STORAGE_REQUEST_CODE = 200;

    private ListView sessionListView;
    private TextView resultText;
    private Button refreshButton;
    private Button viewChartsButton;
    private ArrayAdapter<String> adapter;
    private File[] currentSessions = new File[0];
    private File lastAnalyzedSession = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionListView = findViewById(R.id.sessionListView);
        resultText = findViewById(R.id.resultText);
        refreshButton = findViewById(R.id.refreshButton);
        viewChartsButton = findViewById(R.id.viewChartsButton);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        sessionListView.setAdapter(adapter);

        sessionListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentSessions.length) {
                analyzeSession(currentSessions[position]);
            }
        });

        refreshButton.setOnClickListener(v -> refreshSessions());

        viewChartsButton.setOnClickListener(v -> {
            if (lastAnalyzedSession != null) {
                Intent intent = new Intent(this, ChartActivity.class);
                intent.putExtra(ChartActivity.EXTRA_SESSION_PATH, lastAnalyzedSession.getAbsolutePath());
                startActivity(intent);
            }
        });

        if (!hasStoragePermission()) {
            requestStoragePermission();
        } else {
            refreshSessions();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (hasStoragePermission()) {
                refreshSessions();
            } else {
                resultText.setText("Storage access required to read sessions from " + SessionAnalyzer.SHARED_FOLDER);
            }
        }
    }

    private void refreshSessions() {
        currentSessions = SessionAnalyzer.listSessions();
        List<String> names = new ArrayList<>();
        for (File f : currentSessions) names.add(f.getName());
        adapter.clear();
        adapter.addAll(names);
        adapter.notifyDataSetChanged();
        if (names.isEmpty()) {
            resultText.setText("No sessions found in " + SessionAnalyzer.SHARED_FOLDER
                    + " \u2014 record something with the Recorder app first");
        } else {
            resultText.setText("Tap a session to analyze it");
        }
    }

    private void analyzeSession(File sessionDir) {
        resultText.setText("Analyzing " + sessionDir.getName() + "...");
        viewChartsButton.setEnabled(false);
        new Thread(() -> {
            try {
                SessionAnalyzer.Summary summary = SessionAnalyzer.analyzeSession(sessionDir);
                lastAnalyzedSession = sessionDir;
                runOnUiThread(() -> {
                    resultText.setText(summary.toString()
                            + "\n\nReports written to:\n" + sessionDir.getAbsolutePath());
                    viewChartsButton.setEnabled(true);
                    Toast.makeText(this, "Analysis complete", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> resultText.setText("Analysis failed: " + e.getMessage()));
            }
        }, "AnalysisThread").start();
    }
}
