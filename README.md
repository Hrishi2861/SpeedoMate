<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="SpeedoMate Logo"/>

# 🚗 SpeedoMate

### *Your smart GPS speedometer — on your phone and in your car.*

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android Auto](https://img.shields.io/badge/Android%20Auto-Supported-3DDC84?style=for-the-badge&logo=android-auto&logoColor=white)](https://www.android.com/auto/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-API%2026-orange?style=for-the-badge)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

<br/>

> A beautiful, customizable GPS speedometer for Android with an analog + digital display,
> full trip tracking, theming, and native **Android Auto** support.

<br/>

---

## 📸 Screenshots

| 📱 Phone | 🚗 Android Auto |
|:---:|:---:|
| <img src="screenshots/phone.jpg" width="260" alt="Phone UI"/> | <img src="screenshots/auto.png" width="420" alt="Android Auto UI"/> |
| *Analog + digital speedometer* | *Live data on your car screen* |

---

</div>

## ✨ Features

- 🎯 **Real-time GPS Speed** — Accurate speed using `FusedLocationProviderClient`, same API as Google Maps
- ⚡ **Analog + Digital Speedometer** — Beautiful glowing needle gauge with smooth animations
- 🏎️ **Smooth Animations** — Startup sweep, needle overshoot, rolling digits, glow pulse & danger zone flicker
- 🎨 **Custom Themes** — Pick your accent color from 7 presets (Cyan, Electric Blue, Neon Green, Amber, Hot Pink, Lime, Gold) or auto-detect from your wallpaper on Android 12+
- 📊 **Trip Statistics** — Max speed, average speed, and trip distance tracked automatically
- 💾 **Persistent Data** — Trip data saved with DataStore, survives app close and phone restart
- 🔄 **km/h ↔ mph Toggle** — Switch units instantly from Settings
- 🚗 **Android Auto** — Native Car App integration, view live speed & stats on your car's display
- 🚦 **Speed Limit Alerts** — Set a custom speed limit threshold in settings; the danger zone on the gauge starts exactly at your limit. When exceeded, triggers a red gauge, vibration, and double-beep (1s apart)
- 🧭 **Real-time Compass** — Xiaomi-style fixed arrow pointing UP with cardinal + degree heading display, powered by `ROTATION_VECTOR` sensor for instant response
- 📤 **Trip Sharing** — Share trips as text summary (with plain URL) or PNG image (with speed graph, stats, and branding) — accessible from Trip History
- 📈 **Speed Graph** — Visual speed-over-time chart in Trip History with themed accent colors
- 🔴 **Danger Zone Sync** — The red arc on the gauge starts at your configured speed limit (e.g., 80 km/h), not a fixed percentage
- 🗑️ **Discard Trip** — Reset trip without saving, with confirmation dialog
- 🌙 **Dark Theme** — Sleek dark UI optimized for driving visibility

---

## 🎬 How It Works

```
GPS Satellite
     │
     ▼
FusedLocationProvider (500ms updates)
     │
     ▼
SpeedTrackingService (Background Foreground Service)
     │
     ├──► StateFlow (speed, max, avg, distance)
     │         │
     │    ┌────┴────┐
     │    ▼         ▼
     │  Phone UI  Android Auto
     │  (Canvas)  (ListTemplate)
     │
     └──► DataStore (persistent storage)
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Architecture | MVVM + StateFlow |
| Location | Google FusedLocationProviderClient |
| Sensors | ROTATION_VECTOR for compass heading |
| Car Integration | AndroidX Car App Library |
| Persistence | Jetpack DataStore + Room |
| Background | Foreground Service |
| UI | Custom Canvas View + ConstraintLayout |
| Animations | ValueAnimator + ObjectAnimator |
| Audio | ToneGenerator + AudioManager focus for AA beep routing |
| Sharing | FileProvider + Bitmap generation for PNG export |
| Theming | DataStore-driven accent color flow across all views |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android device running **API 26+** (Android 8.0 Oreo)
- For Android Auto: Android Auto app installed on device

### Installation

```bash
# Clone the repository
git clone https://github.com/Hrishi2861/SpeedoMate.git

# Open in Android Studio
# Let Gradle sync
# Run on device or emulator
```

### Build

```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

---

## 🚗 Android Auto Setup

1. Install **Android Auto** on your phone
2. Enable **Developer Mode** in Android Auto settings (tap version 10 times)
3. Enable **Unknown sources** in Android Auto Developer settings
4. Connect phone to car or use **Desktop Head Unit (DHU)** for testing:

```bash
# Run DHU emulator
~/Android/Sdk/extras/google/auto/desktop-head-unit
```

### AA Features
- Live speed, max/avg, distance, heading, and unit toggle in a scrollable list
- Speed limit exceeded warning row shown when threshold is breached
- Double-beep alert routed through car speakers via `AudioManager` audio focus
- Independent Flow collectors ensure UI stays live even when GPS is idle
- Unit toggle embedded as a list row (bypasses ActionStrip 1-titled-action limit)

---

## 📁 Project Structure

```
com.speedomate/
├── data/
│   ├── PrefsManager.kt          # DataStore for settings & trip persistence
│   ├── TripData.kt              # Trip data model
│   ├── TripDao.kt               # Room DAO for trip history
│   └── TripDatabase.kt          # Room database
├── service/
│   ├── SpeedTrackingService.kt  # Background GPS + speed tracking + alert broadcast
│   └── SpeedAutoService.kt      # Android Auto CarAppService with live updates & beep
├── ui/
│   ├── MainActivity.kt          # Main phone UI with sensor listener & alert handling
│   ├── SettingsActivity.kt      # Speed limit, unit toggle, theme picker, seekbar
│   ├── TripHistoryActivity.kt   # Trip list with share (text/image) & delete
│   ├── SpeedGraphView.kt        # Custom canvas view for speed-over-time graph
│   ├── SpeedViewModel.kt        # MVVM ViewModel
│   ├── SpeedometerView.kt       # Custom analog gauge with compass & danger zone
│   └── ThemeApplier.kt          # Utility for dynamic accent color application
└── util/
    └── TripShareHelper.kt       # PNG bitmap generation & text share intent
```

---

## 🔒 Permissions

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS speed tracking |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `FOREGROUND_SERVICE` | Background speed tracking |
| `FOREGROUND_SERVICE_LOCATION` | Location while in background |
| `POST_NOTIFICATIONS` | Foreground service notification |
| `VIBRATE` | Speed limit alert vibration |

> ✅ No internet permission required — everything works offline!

---

## 🔔 Speed Limit Alerts

Set a custom speed limit in **Settings** using the seekbar. When your speed exceeds the threshold:
1. The danger zone on the gauge starts exactly at your limit (not a fixed 80%)
2. The gauge turns red with a flickering danger arc
3. Your phone vibrates (200ms)
4. A double-beep sounds (two 200ms beeps, 1 second apart)
5. Android Auto plays the same double-beep through car speakers

The alert fires once per crossing — it won't repeat until your speed drops below the limit and rises above it again. Speed limit updates apply at runtime without restarting the app.

---

## 🧭 Compass

The speedometer includes a real-time compass display:
- Fixed arrow pointing UP at the top center of the gauge
- Cardinal direction (N, NE, E, SE, S, SW, W, NW) + degree label above the arrow
- Powered by `Sensor.TYPE_ROTATION_VECTOR` for fused, drift-free heading
- Updates instantly at `SENSOR_DELAY_FASTEST` (~50Hz)
- Works on both phone and Android Auto

---

## 🎨 Custom Themes

Personalize SpeedoMate's appearance from the **Settings** screen:

- **7 Preset Colors** — Cyan, Electric Blue, Neon Green, Amber, Hot Pink, Lime, Gold
- **Monet Integration** — On Android 12+ (A12+), your wallpaper's primary accent color is auto-detected and shown as an extra swatch
- **What Changes** — The speedometer needle, arc fill, glow, center dot, compass arrow, unit text, stats card background tint, top accent bar, save button text/icon, seekbar progress, trip date text, and graph line/labels all follow your selected theme

Selecting a new color applies it instantly — no restart required.

---

## 📤 Trip Sharing

From Trip History, share any trip:
- **Text**: Speed summary with max/avg speed, distance, duration, and a plain URL
- **Image**: PNG bitmap with speed graph, stats, and SpeedoMate branding (non-overlapping layout)

---

## 🤝 Contributing

Contributions are welcome! Feel free to:

---

## 📄 License

```
MIT License — feel free to use, modify and distribute.
See LICENSE file for details.
```

---

<div align="center">

Made with ❤️ by **Hrishi**

*If SpeedoMate saved you from a speeding ticket, give it a ⭐*

[![GitHub stars](https://img.shields.io/github/stars/Hrishi2861/SpeedoMate?style=social)](https://github.com/Hrishi2861/SpeedoMate)

</div>
