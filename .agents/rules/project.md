# Rules: open-polar-h10-ecg-logger

## About the Project

Android application for recording ECG and accelerometer streaming data from the **Polar H10** sensor via Bluetooth BLE.

- **Package:** `org.circadiaware.open_polar_h10_ecg_logger`
- **Languages:** Java and Kotlin (Polar BLE SDK 6+ is Kotlin-based; Kotlin is used for BLE and ViewModel, Java for UI/components)
- **minSdk:** 24 (Android 7.0+)
- **targetSdk:** 36 (Android 16)
- **Architecture:** MVVM (ViewModel + LiveData)

---

## Language and Code Style

- **Language Requirement:** All Git commit messages, code comments, documentation (README, KDoc, Javadoc), rule/customization files, and CI/CD workflow comments must be written strictly in **English**.
- Source code may be written in **Java** or **Kotlin** (Kotlin is preferred for Polar BLE SDK 6+ and coroutines/Flow; Java is supported for UI/components).
- Follow standard naming conventions:
  - Classes: `PascalCase`
  - Methods and variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase`
- Comments on public classes and methods must be in **Javadoc** or **KDoc** format (in English).
- Maximum line length: **120 characters**.
- Indentation: **4 spaces** (no tabs).

---

## Architecture

- Follow the **MVVM** pattern: Activity/Fragment → ViewModel → Repository.
- `ViewModel` must not hold direct references to Android Context unless it is `ApplicationContext`.
- Keep UI logic in Fragments/Activities; business logic belongs in ViewModels and Repositories.
- Current package structure (expand as the project grows):
  ```
  org.circadiaware.open_polar_h10_ecg_logger/
  ├── ui/           # Fragments, Activities, Adapters
  │   └── main/     # StatusFragment, Metrics1Fragment, Metrics2Fragment, RealtimeLineChartView
  ├── util/         # Utility classes (e.g. DemoDataGenerator)
  ├── PolarViewModel.kt  # BLE logic + ViewModel (Polar SDK, coroutines)
  └── MainActivity.java  # Single activity host
  ```
- Target package structure (as features are added):
  ```
  org.circadiaware.open_polar_h10_ecg_logger/
  ├── ui/           # Fragments, Activities, Adapters
  │   └── main/
  ├── viewmodel/    # ViewModels
  ├── repository/   # Data repositories
  ├── model/        # Data classes (POJO / data classes)
  ├── ble/          # Bluetooth BLE logic (Polar SDK)
  └── util/         # Utility classes
  ```

---

## Bluetooth BLE and Polar H10

- Isolate all BLE code within the `ble/` package or dedicated ViewModel.
- Make Bluetooth error handling explicit — do not swallow exceptions silently.
- Always check and request Bluetooth runtime permissions before connecting (Android 12+ requires `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).
- Run BLE operations on background threads/coroutines; post results via `LiveData` or callbacks to the main thread.

---

## Build and Verification

- **Do NOT build APKs (`assembleDebug` or `assembleRelease`)**:
  - Packaging the APK artifact is not needed during development and task verification.
  - Only compilation (`./gradlew compileDebugSources`) and tests (`./gradlew testDebugUnitTest`) are required.
- **AGP:** 8.13.2 — do not downgrade.
- **Gradle:** 8.14.5+ — do not downgrade.
- **Repositories:** `google()`, `mavenCentral()`, and `https://jitpack.io` (required for Polar BLE SDK). `jcenter()` is **forbidden** (deprecated).
- Pin dependency versions explicitly (avoid dynamic versions like `4.+`).
- Before adding a new dependency, check if a similar library is already declared in `app/build.gradle`.
- Do not specify `buildToolsVersion` explicitly — AGP 8.x manages this automatically.

---

## CI/CD (GitHub Actions)

- Workflow file: `.github/workflows/android_build.yml`.
- **Stable release:** tags starting with `v` (e.g. `v1.0.0`).
- **Pre-release:** tags starting with `alpha*`, `beta*`, `pre*`, `rc*`.
- Never store secrets or keystores directly in the repository — use **GitHub Secrets**.

---

## Resources and XML

- Extract all strings to `res/values/strings.xml` — hardcoded strings in code are forbidden.
- Layout file naming conventions:
  - Activity: `activity_*.xml`
  - Fragment: `fragment_*.xml`
  - List item: `item_*.xml`
- View IDs must use `camelCase` (e.g., `@+id/buttonConnect`).
- Use `ConstraintLayout` or structured `LinearLayout` as the root container.

---

## Permissions

- Request only necessary permissions.
- Required permissions for BLE:
  - `BLUETOOTH`, `BLUETOOTH_ADMIN` — for Android < 12
  - `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` — for Android 12+
  - `ACCESS_FINE_LOCATION` — may be required for BLE scanning
- Always handle permission rejection gracefully.

---

## Prohibited

- ❌ Writing commit messages, comments, documentation, or CI workflow comments in any language other than English.
- ❌ Assembling/building APKs (`assembleDebug`, `assembleRelease`) during development/verification — only compilation and unit tests are needed.
- ❌ Using `jcenter()` — use `mavenCentral()` only.
- ❌ Lowering `minSdk` below 24.
- ❌ Lowering `compileSdk` / `targetSdk` below 36.
- ❌ Storing keystores or passwords in the repository.
- ❌ Performing network or BLE operations on the main (UI) thread.
- ❌ Silently ignoring BLE connection errors without notifying the user.
