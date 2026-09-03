package com.example.vsanalyzer.analysis;

import android.os.Environment;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Finds recording sessions written by the Recorder app to the shared
 * Documents/VSRecordings folder, and runs SoundAnalyzer + VibrationAnalyzer on them.
 */
public class SessionAnalyzer {

    public static final String SHARED_FOLDER = "Documents/VSRecordings";

    public static class Summary {
        public String sessionName;
        public int audioWindowsFlagged;
        public int audioWindowsTotal;
        public int vibrationWindowsFlagged;
        public int vibrationWindowsTotal;

        @Override
        public String toString() {
            return sessionName + ": audio " + audioWindowsFlagged + "/" + audioWindowsTotal
                    + " flagged, vibration " + vibrationWindowsFlagged + "/" + vibrationWindowsTotal + " flagged";
        }
    }

    /** The shared folder the Recorder app writes sessions into. */
    public static File sharedRoot() {
        return new File(Environment.getExternalStorageDirectory(), SHARED_FOLDER);
    }

    /** Lists all session_* folders, most recent first. */
    public static File[] listSessions() {
        File root = sharedRoot();
        File[] sessions = root.listFiles((FileFilter) f ->
                f.isDirectory() && f.getName().startsWith("session_"));
        if (sessions == null) return new File[0];
        Arrays.sort(sessions, Comparator.comparingLong(File::lastModified).reversed());
        return sessions;
    }

    /** Runs both analyzers on the given session folder and returns a summary. */
    public static Summary analyzeSession(File sessionDir) throws IOException {
        Summary summary = new Summary();
        summary.sessionName = sessionDir.getName();

        File wav = new File(sessionDir, "audio.wav");
        if (wav.exists()) {
            List<SoundAnalyzer.WindowResult> audioResults = SoundAnalyzer.analyze(wav);
            summary.audioWindowsTotal = audioResults.size();
            for (SoundAnalyzer.WindowResult r : audioResults) {
                if (r.flagged) summary.audioWindowsFlagged++;
            }
        }

        File csv = new File(sessionDir, "vibration.csv");
        if (csv.exists()) {
            List<VibrationAnalyzer.WindowResult> vibResults = VibrationAnalyzer.analyze(csv);
            summary.vibrationWindowsTotal = vibResults.size();
            for (VibrationAnalyzer.WindowResult r : vibResults) {
                if (r.flagged) summary.vibrationWindowsFlagged++;
            }
        }

        return summary;
    }
}
