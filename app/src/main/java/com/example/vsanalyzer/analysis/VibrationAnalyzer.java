package com.example.vsanalyzer.analysis;

import java.io.*;
import java.util.*;

/**
 * Analyzes recorded accelerometer data (vibration.csv, columns: timestamp_ms,x,y,z).
 * Groups samples into fixed-duration windows, computes RMS magnitude of the
 * acceleration vector per window, and flags windows exceeding a threshold.
 *
 * Output: analysis_vibration.csv in the same session folder as the input CSV.
 */
public class VibrationAnalyzer {

    private static final long WINDOW_MS = 200;
    private static final double DEFAULT_MAGNITUDE_THRESHOLD = 2.0; // m/s^2 RMS; tune per use case

    public static class WindowResult {
        public double timeSec;
        public double rmsMagnitude;
        public boolean flagged;
    }

    public static List<WindowResult> analyze(File csvFile, double magnitudeThreshold) throws IOException {
        List<Double> times = new ArrayList<>();
        List<Double> magnitudes = new ArrayList<>();

        long firstTimestamp = -1;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                long ts = Long.parseLong(parts[0].trim());
                double x = Double.parseDouble(parts[1].trim());
                double y = Double.parseDouble(parts[2].trim());
                double z = Double.parseDouble(parts[3].trim());

                if (firstTimestamp < 0) firstTimestamp = ts;

                times.add((ts - firstTimestamp) / 1000.0);
                magnitudes.add(Math.sqrt(x * x + y * y + z * z));
            }
        }

        List<WindowResult> results = new ArrayList<>();
        if (times.isEmpty()) {
            writeReport(csvFile.getParentFile(), results);
            return results;
        }

        double windowStart = 0;
        double sumSquares = 0;
        int count = 0;

        for (int i = 0; i < times.size(); i++) {
            double t = times.get(i);
            if (t - windowStart >= WINDOW_MS / 1000.0 && count > 0) {
                results.add(buildResult(windowStart, sumSquares, count, magnitudeThreshold));
                windowStart = t;
                sumSquares = 0;
                count = 0;
            }
            double mag = magnitudes.get(i);
            sumSquares += mag * mag;
            count++;
        }
        if (count > 0) {
            results.add(buildResult(windowStart, sumSquares, count, magnitudeThreshold));
        }

        writeReport(csvFile.getParentFile(), results);
        return results;
    }

    public static List<WindowResult> analyze(File csvFile) throws IOException {
        return analyze(csvFile, DEFAULT_MAGNITUDE_THRESHOLD);
    }

    private static WindowResult buildResult(double windowStart, double sumSquares, int count, double threshold) {
        WindowResult r = new WindowResult();
        r.timeSec = windowStart;
        r.rmsMagnitude = Math.sqrt(sumSquares / count);
        r.flagged = r.rmsMagnitude > threshold;
        return r;
    }

    private static void writeReport(File sessionDir, List<WindowResult> results) throws IOException {
        File out = new File(sessionDir, "analysis_vibration.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(out))) {
            w.write("time_sec,rms_magnitude,flagged\n");
            for (WindowResult r : results) {
                w.write(String.format(Locale.US, "%.3f,%.4f,%b\n", r.timeSec, r.rmsMagnitude, r.flagged));
            }
        }
    }
}
