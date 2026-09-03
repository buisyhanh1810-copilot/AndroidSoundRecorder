package com.example.vsanalyzer;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vsanalyzer.analysis.SpectrogramGenerator;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChartActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_PATH = "session_path";

    private TextView sessionNameText;
    private TextView spectrogramLabel;
    private LineChart dbChart;
    private LineChart vibrationChart;
    private ImageView spectrogramImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        sessionNameText = findViewById(R.id.sessionNameText);
        spectrogramLabel = findViewById(R.id.spectrogramLabel);
        dbChart = findViewById(R.id.dbChart);
        vibrationChart = findViewById(R.id.vibrationChart);
        spectrogramImage = findViewById(R.id.spectrogramImage);

        String path = getIntent().getStringExtra(EXTRA_SESSION_PATH);
        if (path == null) {
            Toast.makeText(this, "No session provided", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File sessionDir = new File(path);
        sessionNameText.setText(sessionDir.getName());

        loadDbChart(new File(sessionDir, "analysis_audio.csv"));
        loadVibrationChart(new File(sessionDir, "analysis_vibration.csv"));
        loadSpectrogram(new File(sessionDir, "audio.wav"));
    }

    private void loadDbChart(File csv) {
        if (!csv.exists()) return;
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csv))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                float t = Float.parseFloat(parts[0]);
                float db = Float.parseFloat(parts[1]);
                entries.add(new Entry(t, db));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        setUpLineChart(dbChart, entries, "dBFS", Color.parseColor("#1E88E5"));
    }

    private void loadVibrationChart(File csv) {
        if (!csv.exists()) return;
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csv))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                float t = Float.parseFloat(parts[0]);
                float mag = Float.parseFloat(parts[1]);
                entries.add(new Entry(t, mag));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        setUpLineChart(vibrationChart, entries, "RMS magnitude (m/s\u00b2)", Color.parseColor("#E53935"));
    }

    private void setUpLineChart(LineChart chart, List<Entry> entries, String label, int color) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setDrawCircles(false);
        dataSet.setLineWidth(1.5f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        Description desc = new Description();
        desc.setText("Time (s)");
        chart.setDescription(desc);
        chart.getAxisRight().setEnabled(false);
        chart.animateX(300);
        chart.invalidate();
    }

    private void loadSpectrogram(File wavFile) {
        if (!wavFile.exists()) {
            spectrogramLabel.setText("No audio.wav found for this session");
            return;
        }
        new Thread(() -> {
            try {
                SpectrogramGenerator.Result result = SpectrogramGenerator.generate(wavFile);
                runOnUiThread(() -> {
                    spectrogramImage.setImageBitmap(result.bitmap);
                    spectrogramLabel.setText(String.format(Locale.US,
                            "0\u2013%.0f Hz over %.1fs (log-scaled magnitude, blue=quiet \u2192 red=loud)",
                            result.maxFrequencyHz, result.durationSec));
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> spectrogramLabel.setText("Spectrogram failed: " + e.getMessage()));
            }
        }, "SpectrogramThread").start();
    }
}
