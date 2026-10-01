# Open Polar H10 ECG Logger

[![Android CI](https://github.com/unixty/h10ecg/actions/workflows/android_build.yml/badge.svg)](https://github.com/unixty/h10ecg/actions/workflows/android_build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

An open-source Android application for recording ECG and accelerometer streaming data from the **Polar H10** heart rate sensor via Bluetooth BLE.

---

## Features

- **Real-time ECG streaming** — 130 Hz ECG data streamed over BLE to your Android device
- **Accelerometer streaming** — up to 200 Hz 3-axis accelerometer data
- **Live chart** — real-time ECG waveform visualization
- **Polar H10 internal memory** — launch recording sessions on the sensor's internal memory (stores HR and RR intervals at 1 Hz; ECG/accelerometer streaming requires an Android receiver)
- **MVVM architecture** — ViewModel + LiveData, non-blocking BLE operations via Kotlin coroutines

---

## Requirements

| Component       | Version                          |
|-----------------|----------------------------------|
| Android SDK     | API 24+ (Android 7.0 Nougat)     |
| Target SDK      | API 36 (Android 16)              |
| Bluetooth       | BLE required                     |
| Polar H10       | Firmware ≥ 3.x                   |
| Build tools     | AGP 8.13.2, Gradle 8.14.5        |
| JDK             | 21 (Temurin)                     |

---

## Building

```bash
# Clone the repository
git clone https://github.com/unixty/h10ecg.git
cd h10ecg

# Build debug APK
./gradlew assembleDebug

# Run unit tests only
./gradlew testDebugUnitTest
```

The debug APK is output to `app/build/outputs/apk/debug/`.

---

## Permissions

The app requests the following Android permissions:

| Permission                    | When required                          |
|-------------------------------|----------------------------------------|
| `BLUETOOTH_SCAN`              | Android 12+ (API 31+), BLE scanning   |
| `BLUETOOTH_CONNECT`           | Android 12+ (API 31+), BLE connection |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Android < 12 (legacy)              |
| `ACCESS_FINE_LOCATION`        | Android 6–11 (required for BLE scan)  |

---

## Architecture

```
org.circadiaware.open_polar_h10_ecg_logger/
├── ui/main/          # Fragments (StatusFragment, Metrics1/2Fragment)
│                     # RealtimeLineChartView, SectionsPagerAdapter
├── util/             # Utility classes (DemoDataGenerator, …)
├── PolarViewModel.kt # BLE logic + ViewModel (Polar SDK, coroutines/RxJava3)
└── MainActivity.java # Single activity host
```

---

## Dependencies

- [Polar BLE SDK 6.4.0](https://github.com/polarofficial/polar-ble-sdk) — official Polar BLE library
- [RxJava 3](https://github.com/ReactiveX/RxJava) / [RxAndroid](https://github.com/ReactiveX/RxAndroid) — reactive streams
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) — async BLE operations
- AndroidX AppCompat, Material, ConstraintLayout, Lifecycle (LiveData/ViewModel)

---

## CI/CD

GitHub Actions workflow (`.github/workflows/android_build.yml`) automatically:

- Builds a debug APK on every push to `main`/`master` and on pull requests
- Creates a GitHub Release and uploads the APK when a tag is pushed (`v*`, `alpha*`, `beta*`, `pre*`, `rc*`)

---

## License

This project is licensed under the **GNU General Public License v3.0**.

> You are free to use, study, modify, and distribute this software under the terms of the GPL-3.0.  
> Any derivative work must also be distributed under the same license.

See the [LICENSE](LICENSE) file for the full license text, or visit [https://www.gnu.org/licenses/gpl-3.0](https://www.gnu.org/licenses/gpl-3.0).

---

## Related Resources

- [Polar BLE SDK documentation](https://github.com/polarofficial/polar-ble-sdk/)
