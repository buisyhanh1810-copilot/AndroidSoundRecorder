package com.example.vsanalyzer.analysis;

import java.io.*;

/**
 * Analyzes a recorded WAV file in fixed-size windows:
 * - RMS level in dBFS (relative to full scale, since phone mics aren't calibrated to real SPL)
 * - Dominant frequency via FFT
 * - Flags windows whose level exceeds a threshold
 *
 * Output: analysis_audio.csv in the same session folder as the input WAV.
 */
public class SoundAnalyzer {

    private static final int WINDOW_SIZE = 1024; // power of 2, ~64ms at 16kHz
    private static final double DEFAULT_DB_THRESHOLD = -20.0; // dBFS; tune per your environment

    public static class WindowResult {
        public double timeSec;
        public double dBFS;
        public double dominantFreqHz;
        public boolean flagged;
    }

    public static java.util.List<WindowResult> analyze(File wavFile, double dbThreshold) throws IOException {
        WavUtils.WavData wav = WavUtils.read(wavFile);
        int sampleRate = wav.sampleRate;
        short[] samples = wav.samples;

        java.util.List<WindowResult> results = new java.util.ArrayList<>();
        int numWindows = samples.length / WINDOW_SIZE;

        for (int w = 0; w < numWindows; w++) {
            double[] window = new double[WINDOW_SIZE];
            long sumSquares = 0;
            for (int i = 0; i < WINDOW_SIZE; i++) {
                short s = samples[w * WINDOW_SIZE + i];
                window[i] = s;
                sumSquares += (long) s * s;
            }

            double rms = Math.sqrt(sumSquares / (double) WINDOW_SIZE);
            double dBFS = rms > 0 ? 20 * Math.log10(rms / 32768.0) : -96.0;

            FFT.applyHannWindow(window);
            double[] spectrum = FFT.magnitudeSpectrum(window);
            double dominantFreq = FFT.dominantFrequency(spectrum, sampleRate, WINDOW_SIZE);

            WindowResult r = new WindowResult();
            r.timeSec = (w * WINDOW_SIZE) / (double) sampleRate;
            r.dBFS = dBFS;
            r.dominantFreqHz = dominantFreq;
            r.flagged = dBFS > dbThreshold;
            results.add(r);
        }

        writeReport(wavFile.getParentFile(), results);
        return results;
    }

    public static java.util.List<WindowResult> analyze(File wavFile) throws IOException {
        return analyze(wavFile, DEFAULT_DB_THRESHOLD);
    }

    private static void writeReport(File sessionDir, java.util.List<WindowResult> results) throws IOException {
        File out = new File(sessionDir, "analysis_audio.csv");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(out))) {
            w.write("time_sec,dBFS,dominant_freq_hz,flagged\n");
            for (WindowResult r : results) {
                w.write(String.format(java.util.Locale.US, "%.3f,%.2f,%.1f,%b\n",
                        r.timeSec, r.dBFS, r.dominantFreqHz, r.flagged));
            }
        }
    }
}
