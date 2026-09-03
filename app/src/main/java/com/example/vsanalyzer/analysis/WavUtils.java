package com.example.vsanalyzer.analysis;

import java.io.*;

/**
 * Reads a 16-bit PCM mono WAV file (the format the Recorder app produces).
 */
public class WavUtils {

    public static class WavData {
        public int sampleRate;
        public short[] samples;
    }

    public static WavData read(File wavFile) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(wavFile)))) {
            byte[] header = new byte[44];
            in.readFully(header);

            int sampleRate = ((header[24] & 0xff)) | ((header[25] & 0xff) << 8)
                    | ((header[26] & 0xff) << 16) | ((header[27] & 0xff) << 24);
            int dataSize = ((header[40] & 0xff)) | ((header[41] & 0xff) << 8)
                    | ((header[42] & 0xff) << 16) | ((header[43] & 0xff) << 24);

            int numSamples = dataSize / 2;
            short[] samples = new short[numSamples];
            byte[] buf = new byte[dataSize];
            in.readFully(buf);
            for (int i = 0; i < numSamples; i++) {
                int lo = buf[i * 2] & 0xff;
                int hi = buf[i * 2 + 1];
                samples[i] = (short) ((hi << 8) | lo);
            }

            WavData result = new WavData();
            result.sampleRate = sampleRate;
            result.samples = samples;
            return result;
        }
    }
}
