package com.example.vsanalyzer.analysis;

/**
 * Minimal in-place iterative radix-2 Cooley-Tukey FFT.
 * Input length must be a power of two.
 */
public class FFT {

    public static double[] magnitudeSpectrum(double[] samples) {
        int n = samples.length;
        double[] real = samples.clone();
        double[] imag = new double[n];

        transform(real, imag);

        double[] magnitude = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            magnitude[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return magnitude;
    }

    private static void transform(double[] real, double[] imag) {
        int n = real.length;
        if ((n & (n - 1)) != 0) {
            throw new IllegalArgumentException("FFT length must be a power of 2, got " + n);
        }

        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tmp = real[i]; real[i] = real[j]; real[j] = tmp;
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wReal = Math.cos(angle);
            double wImag = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double curReal = 1, curImag = 0;
                for (int k = 0; k < len / 2; k++) {
                    double uReal = real[i + k];
                    double uImag = imag[i + k];
                    double vReal = real[i + k + len / 2] * curReal - imag[i + k + len / 2] * curImag;
                    double vImag = real[i + k + len / 2] * curImag + imag[i + k + len / 2] * curReal;

                    real[i + k] = uReal + vReal;
                    imag[i + k] = uImag + vImag;
                    real[i + k + len / 2] = uReal - vReal;
                    imag[i + k + len / 2] = uImag - vImag;

                    double nextReal = curReal * wReal - curImag * wImag;
                    double nextImag = curReal * wImag + curImag * wReal;
                    curReal = nextReal;
                    curImag = nextImag;
                }
            }
        }
    }

    public static double dominantFrequency(double[] magnitude, int sampleRate, int fftSize) {
        int maxBin = 1;
        double maxVal = 0;
        for (int i = 1; i < magnitude.length; i++) {
            if (magnitude[i] > maxVal) {
                maxVal = magnitude[i];
                maxBin = i;
            }
        }
        return maxBin * (double) sampleRate / fftSize;
    }

    public static void applyHannWindow(double[] samples) {
        int n = samples.length;
        for (int i = 0; i < n; i++) {
            double w = 0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1)));
            samples[i] *= w;
        }
    }
}
