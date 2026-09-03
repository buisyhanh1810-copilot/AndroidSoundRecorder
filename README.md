# Vibration & Sound Recorder (scaffold)

Records raw sensor data only — analysis lives in the separate **VS Analyzer** app.

- **Accelerometer** (`TYPE_LINEAR_ACCELERATION`) → `vibration.csv` (`timestamp_ms,x,y,z`)
- **Microphone audio** (16kHz mono PCM) → `audio.wav`

Both streams use `System.currentTimeMillis()` so they can be time-aligned later.

## Where files are saved
Sessions are written to the **shared public folder** `Documents/VSRecordings/session_<timestamp>/`
(not app-private storage), so the separate Analyzer app can read them too. This requires
"All files access" (`MANAGE_EXTERNAL_STORAGE`), which the app will prompt for on first launch.
This permission is fine for personal/sideloaded use but is restricted on the Play Store.

## How to use
1. Open this folder in Android Studio as an existing project (add icons + Gradle wrapper
   on import — omitted here for brevity).
2. Run on a real device (emulator has no real mic/accelerometer data).
3. Grant mic + "all files access" permissions when prompted.
4. Tap **Start Recording** → tap **Stop Recording**.
5. Output lands in `Documents/VSRecordings/session_<timestamp>/` — visible in any
   file manager, and readable by the companion Analyzer app.

## Files
- `MainActivity.java` — start/stop UI + permission handling
- `RecordingService.java` — foreground service; owns the sensor listener and the
  `AudioRecord` capture loop; writes raw PCM during recording, then wraps it in a
  proper WAV header on stop
- `activity_main.xml` — bare-bones layout

## Companion app
See the **VS Analyzer** project for offline dB/FFT analysis of recorded sessions.

## Continuous Integration
`.github/workflows/android-build.yml` builds a debug APK on every push/PR to `main`
(and can be triggered manually). No Gradle wrapper is committed — the workflow
provisions Gradle directly via `gradle/actions/setup-gradle`. The built APK is
uploaded as a workflow artifact (`vs-recorder-debug-apk`), downloadable from the
Actions run page.
