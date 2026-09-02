package com.example.vsrecorder;

import android.app.*;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service implements SensorEventListener {

    public static final String ACTION_START = "com.example.vsrecorder.START";
    public static final String ACTION_STOP = "com.example.vsrecorder.STOP";
    private static final String CHANNEL_ID = "vs_recording_channel";
    private static final int NOTIFICATION_ID = 1;

    // --- Audio config ---
    private static final int SAMPLE_RATE = 16000;
    private static final int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private BufferedWriter vibrationWriter;

    private AudioRecord audioRecord;
    private Thread audioThread;
    private volatile boolean isRecording = false;
    private File sessionDir;
    private File rawPcmFile;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            startForeground(NOTIFICATION_ID, buildNotification());
            beginRecording();
        } else if (ACTION_STOP.equals(action)) {
            endRecording();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void beginRecording() {
        if (isRecording) return;
        isRecording = true;

        String sessionName = "session_" +
                new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date());
        File sharedRoot = new File(Environment.getExternalStorageDirectory(), "Documents/VSRecordings");
        sharedRoot.mkdirs();
        sessionDir = new File(sharedRoot, sessionName);
        sessionDir.mkdirs();

        startVibrationRecording();
        startAudioRecording();
    }

    private void endRecording() {
        if (!isRecording) return;
        isRecording = false;

        sensorManager.unregisterListener(this);
        try {
            if (vibrationWriter != null) {
                vibrationWriter.flush();
                vibrationWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        try {
            if (audioThread != null) audioThread.join(1000);
        } catch (InterruptedException ignored) {}

        // Convert raw PCM to a playable WAV with proper header
        if (rawPcmFile != null && rawPcmFile.exists()) {
            File wavFile = new File(sessionDir, "audio.wav");
            try {
                writeWavFromPcm(rawPcmFile, wavFile);
                rawPcmFile.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ---------------- Vibration ----------------

    private void startVibrationRecording() {
        try {
            File csv = new File(sessionDir, "vibration.csv");
            vibrationWriter = new BufferedWriter(new FileWriter(csv));
            vibrationWriter.write("timestamp_ms,x,y,z\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRecording || vibrationWriter == null) return;
        long timestampMs = System.currentTimeMillis();
        try {
            vibrationWriter.write(timestampMs + "," + event.values[0] + ","
                    + event.values[1] + "," + event.values[2] + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------------- Audio ----------------

    private void startAudioRecording() {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING);
        if (minBufferSize <= 0) {
            minBufferSize = SAMPLE_RATE * 2; // fallback
        }
        final int bufferSize = minBufferSize * 2;

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AUDIO_CHANNEL, AUDIO_ENCODING, bufferSize);

        rawPcmFile = new File(sessionDir, "audio.pcm");

        audioRecord.startRecording();

        audioThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(rawPcmFile))) {
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "AudioRecordingThread");
        audioThread.start();
    }

    /** Writes a standard 44-byte WAV header followed by the raw PCM data. */
    private void writeWavFromPcm(File pcmFile, File wavFile) throws IOException {
        long pcmSize = pcmFile.length();
        long totalDataLen = pcmSize + 36;
        long byteRate = (long) SAMPLE_RATE * 1 * 16 / 8;

        try (FileInputStream in = new FileInputStream(pcmFile);
             FileOutputStream out = new FileOutputStream(wavFile)) {

            byte[] header = new byte[44];
            writeWavHeader(header, totalDataLen, pcmSize, SAMPLE_RATE, 1, byteRate);
            out.write(header);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void writeWavHeader(byte[] header, long totalDataLen, long pcmSize,
                                  int sampleRate, int channels, long byteRate) {
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        putIntLE(header, 4, (int) totalDataLen);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        putIntLE(header, 16, 16); // Subchunk1Size for PCM
        header[20] = 1; header[21] = 0; // AudioFormat = PCM
        header[22] = (byte) channels; header[23] = 0;
        putIntLE(header, 24, sampleRate);
        putIntLE(header, 28, (int) byteRate);
        header[32] = (byte) (channels * 16 / 8); header[33] = 0; // block align
        header[34] = 16; header[35] = 0; // bits per sample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        putIntLE(header, 40, (int) pcmSize);
    }

    private void putIntLE(byte[] b, int offset, int value) {
        b[offset] = (byte) (value & 0xff);
        b[offset + 1] = (byte) ((value >> 8) & 0xff);
        b[offset + 2] = (byte) ((value >> 16) & 0xff);
        b[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    // ---------------- Notification / Service plumbing ----------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Recording Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Recording vibration & sound")
                .setContentText("Data collection in progress")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
