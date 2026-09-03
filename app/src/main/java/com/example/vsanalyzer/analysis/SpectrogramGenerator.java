package com.example.vsanalyzer.analysis;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.File;
import java.io.IOException;

/**
 * Computes a spectrogram from a WAV file: for each time window, the full FFT
 * magnitude spectrum (not just the dominant bin), then renders the result as
 * a color-mapped Bitmap (time on x-axis, frequency on y-axis, color = magnitude).
 */
public class SpectrogramGenerator {

    private static final int WINDOW_SIZE = 1024;
    // Only render up to this many frequency bins (out of WINDOW_SIZE/2) to keep
    // the image focused on the range most environmental sound/vibration falls in.
    private static final int MAX_BINS_TO_SHOW = 256; // ~4kHz at 16kHz sample rate, WINDOW_SIZE=1024

    public static class Result {
        public Bitmap bitmap;
        public int sampleRate;
        public double maxFrequencyHz;
        public double durationSec;
    }

    public static Result generate(File wavFile) throws IOException {
        WavUtils.WavData wav = WavUtils.read(wavFile);
        int sampleRate = wav.sampleRate;
        short[] samples = wav.samples;

        int numWindows = samples.length / WINDOW_SIZE;
        int binsToShow = Math.min(MAX_BINS_TO_SHOW, WINDOW_SIZE / 2);

        double[][] spectrogram = new double[numWindows][binsToShow];
        double maxMag = 1e-9;

        for (int w = 0; w < numWindows; w++) {
            double[] window = new double[WINDOW_SIZE];
            for (int i = 0; i < WINDOW_SIZE; i++) {
                window[i] = samples[w * WINDOW_SIZE + i];
            }
            FFT.applyHannWindow(window);
            double[] spectrum = FFT.magnitudeSpectrum(window);

            for (int b = 0; b < binsToShow; b++) {
                double mag = spectrum[b];
                spectrogram[w][b] = mag;
                if (mag > maxMag) maxMag = mag;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(Math.max(numWindows, 1), binsToShow, Bitmap.Config.ARGB_8888);
        for (int w = 0; w < numWindows; w++) {
            for (int b = 0; b < binsToShow; b++) {
                // log scale so quieter detail is still visible, normalized 0..1
                double norm = Math.log10(1 + spectrogram[w][b]) / Math.log10(1 + maxMag);
                int color = magnitudeToColor(norm);
                // flip so low frequency is at the bottom of the image
                bitmap.setPixel(w, binsToShow - 1 - b, color);
            }
        }

        Result result = new Result();
        result.bitmap = bitmap;
        result.sampleRate = sampleRate;
        result.maxFrequencyHz = binsToShow * (double) sampleRate / WINDOW_SIZE;
        result.durationSec = samples.length / (double) sampleRate;
        return result;
    }

    /** Blue (quiet) -> green -> yellow -> red (loud), norm in [0,1]. */
    private static int magnitudeToColor(double norm) {
        norm = Math.max(0, Math.min(1, norm));
        float hue = (float) (240 - 240 * norm); // 240=blue down to 0=red
        return Color.HSVToColor(new float[]{hue, 1f, 1f});
    }
}
