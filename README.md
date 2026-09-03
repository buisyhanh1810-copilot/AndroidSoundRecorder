# Sound & Vibration Analyzer (scaffold)

Companion app to the **Recorder** app. Reads sessions the Recorder app writes to the
shared folder `Documents/VSRecordings/session_<timestamp>/` and runs offline analysis:

- **Sound**: windows `audio.wav` (~64ms chunks), computes dBFS level + FFT dominant
  frequency per window, flags windows above a dB threshold → `analysis_audio.csv`
- **Vibration**: windows `vibration.csv` (200ms chunks), computes RMS acceleration
  magnitude, flags spikes above a threshold → `analysis_vibration.csv`

Both output CSVs are written back into the same session folder, next to the raw data.

## Why two apps share storage this way
Each app's private storage (`getExternalFilesDir`) isn't visible to other apps.
Since the Recorder and Analyzer are separate apps, the Recorder writes into the shared
public `Documents/VSRecordings` folder instead, and both apps request **All Files
Access** (`MANAGE_EXTERNAL_STORAGE`) to read/write there directly with plain file APIs.
This is the simplest approach for two of your own apps on the same device; it's not
appropriate for a Play Store release (Google restricts that permission), but is fine
for personal/sideloaded use.

## How to use
1. Open this folder in Android Studio as an existing project (add icons and the
   Gradle wrapper on import, same as the Recorder app).
2. Install alongside the Recorder app.
3. Launch, grant "All files access" when prompted.
4. Record something with the Recorder app first.
5. Tap **Refresh List**, then tap a session to analyze it — results show inline and
   are also written as CSVs in that session folder.
6. Once analyzed, tap **View Charts** to see:
   - A line chart of sound level (dBFS) over time
   - A line chart of vibration RMS magnitude over time
   - A spectrogram of the audio (frequency vs. time, color = magnitude)

## Files
- `MainActivity.java` — permission handling, session list, tap-to-analyze, launches ChartActivity
- `ChartActivity.java` — reads the analysis CSVs into line charts (MPAndroidChart), generates the spectrogram
- `analysis/FFT.java` — plain-Java FFT, no external dependency
- `analysis/WavUtils.java` — WAV file reader
- `analysis/SoundAnalyzer.java` — dBFS + dominant frequency per window
- `analysis/VibrationAnalyzer.java` — RMS magnitude per window
- `analysis/SpectrogramGenerator.java` — full FFT spectrum per window, rendered as a color-mapped Bitmap
- `analysis/SessionAnalyzer.java` — finds sessions in the shared folder, orchestrates both analyzers

## Charting library
Line charts use [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart), pulled from
JitPack. `settings.gradle` in this scaffold already includes the JitPack repository —
if you start from a fresh Android Studio project instead, add
`maven { url 'https://jitpack.io' }` to its repositories yourself.

## Tuning
Default thresholds are starting points — adjust in the analyzer source once you see
real numbers from your environment:
- `SoundAnalyzer.DEFAULT_DB_THRESHOLD = -20.0` (dBFS)
- `VibrationAnalyzer.DEFAULT_MAGNITUDE_THRESHOLD = 2.0` (m/s² RMS)

## Next step
This still uses simple FFT + threshold logic. A pretrained model (e.g. YAMNet via
TFLite) would add sound *classification* on top of this — discussed separately.

## Continuous Integration
`.github/workflows/android-build.yml` builds a debug APK on every push/PR to `main`
(and can be triggered manually). No Gradle wrapper is committed — the workflow
provisions Gradle directly via `gradle/actions/setup-gradle`. The built APK is
uploaded as a workflow artifact (`vs-analyzer-debug-apk`), downloadable from the
Actions run page.
