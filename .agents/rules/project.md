# Rules: open-polar-h10-ecg-logger

## About the Project

Android application for recording ECG and accelerometer streaming data from the **Polar H10** sensor via Bluetooth BLE.

- **Package:** `org.circadiaware.open_polar_h10_ecg_logger`
- **Language:** Java (not Kotlin)
- **minSdk:** 16 (Android 4.1+)
- **targetSdk:** 36 (Android 16)
- **Architecture:** MVVM (ViewModel + LiveData)

---

## Language and Code Style

- All source code must be written strictly in **Java**. Do not use Kotlin.
- Follow standard Java naming conventions:
  - Classes: `PascalCase`
  - Methods and variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`
  - Packages: `lowercase`
- Comments on public classes and methods must be in **Javadoc** format.
- Maximum line length: **120 characters**.
- Indentation: **4 spaces** (no tabs).

---

## Architecture

- Follow the **MVVM** pattern: Activity/Fragment → ViewModel → Repository.
- `ViewModel` must not hold direct references to Android Context unless it is `ApplicationContext`.
- Keep UI logic in Fragments/Activities; business logic belongs in ViewModels and Repositories.
- Package structure:
  ```
  org.circadiaware.open_polar_h10_ecg_logger/
  ├── ui/           # Fragments, Activities, Adapters
  │   └── main/
  ├── viewmodel/    # ViewModels
  ├── repository/   # Data repositories
  ├── model/        # Data classes (POJO)
  ├── ble/          # Bluetooth BLE logic (Polar SDK)
  └── util/         # Utility classes
  ```

---

## Bluetooth BLE and Polar H10

- Isolate all BLE code within the `ble/` package.
- Make Bluetooth error handling explicit — do not swallow exceptions silently.
- Always check and request Bluetooth runtime permissions before connecting (Android 12+ requires `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).
- Run BLE operations on background threads; post results via `LiveData` or callbacks to the main thread.

---

## Build and Dependencies

- **AGP:** 8.7.0 — do not downgrade.
- **Gradle:** 8.9 — do not downgrade.
- **Repositories:** only `google()` and `mavenCentral()`. `jcenter()` is **forbidden** (deprecated).
- Pin dependency versions explicitly (avoid dynamic versions like `4.+`).
- Before adding a new dependency, check if a similar library is already declared in `app/build.gradle`.
- Do not specify `buildToolsVersion` explicitly — AGP 8.x manages this automatically.

---

## CI/CD (GitHub Actions)

- Workflow file: `.github/workflows/android_build.yml`.
- **Stable release:** tags starting with `v` (e.g. `v1.0.0`).
- **Pre-release:** tags starting with `alpha*`, `beta*`, `pre*`, `rc*`.
- Never store secrets or keystores directly in the repository — use **GitHub Secrets**.
- Build only **debug APK** until a production release keystore is configured.

---

## Resources and XML

- Extract all strings to `res/values/strings.xml` — hardcoded strings in Java code are forbidden.
- Layout file naming conventions:
  - Activity: `activity_*.xml`
  - Fragment: `fragment_*.xml`
  - List item: `item_*.xml`
- View IDs must use `camelCase` (e.g., `@+id/buttonConnect`).
- Use `ConstraintLayout` as the root container for complex screens.

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

- ❌ Using `jcenter()` — use `mavenCentral()` only.
- ❌ Writing code in Kotlin — the project is Java-only.
- ❌ Lowering `minSdk` below 16.
- ❌ Lowering `compileSdk` / `targetSdk` below 36.
- ❌ Storing keystores or passwords in the repository.
- ❌ Performing network or BLE operations on the main (UI) thread.
- ❌ Silently ignoring BLE connection errors without notifying the user.
