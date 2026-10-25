# Technical Audit & Architecture Baseline (Task 001)

Date: 2026-10-25  
Target Project: HeliBoard Fork (`alex-kassel/heli-board`)  
Status: Complete & Verified

---

## 1. Executive Summary & Verification

- **Codebase Baseline**: HeliBoard fork cleanly synchronized with upstream `HeliBorg/HeliBoard:main` (`behind: 0`, `ahead: 14`).
- **Baseline Build Verification**:
  - CI Workflow `Build debug APK` executed via `gh workflow run` on GitHub Actions runner.
  - Successfully compiled all 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) via NDK 28.
  - Artifact produced and downloaded: `HeliBoard_4.3-beta1-debug.apk` (27,260,556 bytes, ~27.2 MB).
- **CI Test Suite Finding**:
  - Upstream `.github/workflows/build-test-auto.yml` failed because the task was invoked as `testRunTestsUnitTest` instead of `:app:testRunTestsUnitTest`.

---

## 2. Project Architecture & Technology Stack

| Layer | Technology | Details / Notes |
| :--- | :--- | :--- |
| **Language Mix** | Kotlin + Java + C++ | Core IME is Java/Kotlin; Settings/Toolbar UI is Jetpack Compose; Native C++ NDK for dictionary / gesture engine |
| **Build System** | Gradle 9.7.1 | Android Gradle Plugin `com.android.tools.build:gradle:9.1.1` |
| **Kotlin Version** | 2.4.0 | Serialization & Compose compiler plugins |
| **Java Compatibility** | Java 17 | `sourceCompatibility = JavaVersion.VERSION_17`, `targetCompatibility = JavaVersion.VERSION_17` |
| **Android SDK** | `compileSdk = 37`, `targetSdk = 37`, `minSdk = 21` | Compatible with Android 5.0 (Lollipop) through Android 15+ |
| **Native NDK** | `28.0.13004108` | `ndkBuild` compiles `libjni_latinime.so` for 4 ABIs |
| **UI Frameworks** | Android Views + Jetpack Compose | Legacy keyboard views use XML/Custom Views; Settings use Jetpack Compose |

---

## 3. Key Components & Exact Symbols

### 3.1 Input Method Service (IME Lifecycle)
- **Class**: [`helium314.keyboard.latin.LatinIME`](app/src/main/java/helium314/keyboard/latin/LatinIME.java)
- **Base Class**: `android.inputmethodservice.InputMethodService`
- **Key Method**: `onEvent(@NonNull final Event event)` (line 1340)
  - Currently handles voice via:
    ```java
    if (KeyCode.VOICE_INPUT == event.getKeyCode()) {
        mRichImm.switchToShortcutIme(this);
    }
    ```
  - **Integration Point**: This is the exact location to intercept `KeyCode.VOICE_INPUT` to trigger our custom in-keyboard recording and AI pipeline instead of switching to an external IME.

### 3.2 Keyboard Toolbar & Voice Button
- **View Container**: [`helium314.keyboard.latin.suggestions.SuggestionStripView`](app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt)
- **Toolbar Configuration**: [`helium314.keyboard.latin.utils.ToolbarUtils`](app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt)
  - `ToolbarKey.VOICE` is already present in `enum class ToolbarKey`.
  - Icon mappings defined in `KeyboardIconsSet.kt` (`sym_keyboard_voice_holo`, `sym_keyboard_voice_lxx`, `sym_keyboard_voice_rounded`).
  - Click handling: `onClickToolbarKey` (line 253) and `onLongClickToolbarKey` (line 261).
  - Key mapping: `getCodeForToolbarKey(ToolbarKey.VOICE) -> KeyCode.VOICE_INPUT`.
- **Ergonomics Decision (D-006)**: The microphone button must be positioned on the **far right (top-right)** of the toolbar to match Gboard conventions for right-hand thumb reach.

### 3.3 Text Insertion Path
- **Wrapper**: [`helium314.keyboard.latin.RichInputConnection`](app/src/main/java/helium314/keyboard/latin/RichInputConnection.java)
- **Method**: `commitText(CharSequence text, int newCursorPosition)`
- **Behavior**: Calls Android's `InputConnection.commitText(text, 1)`. Text is inserted cleanly at the current cursor position in the active editor. **Zero auto-sending**.

### 3.4 Clipboard Retention
- **Manager**: [`helium314.keyboard.latin.ClipboardHistoryManager`](app/src/main/java/helium314/keyboard/latin/ClipboardHistoryManager.kt)
- **Database / DAO**: [`helium314.keyboard.latin.database.ClipboardDao`](app/src/main/java/helium314/keyboard/latin/database/ClipboardDao.kt)
- **Current Behavior**: Time-based retention via `clearOldClips()` (checks `mClipboardHistoryRetentionTime` in minutes).
- **Target (D-005)**: Add count-based prune to retain 20 recent non-pinned items plus pinned items.

### 3.5 Settings Persistence
- **Storage**: Standard Android `SharedPreferences` via `context.prefs()`.
- **UI Architecture**: Modern Jetpack Compose navigation (`SettingsNavHost.kt`, `ToolbarScreen.kt`).
- **Integration Point**: Persistent settings for output language (RU/DE/EN), recipient tone, and polish level can be stored cleanly in `SharedPreferences`.

---

## 4. Permissions, Security & Privacy Audit

1. **Current Permissions**:
   - `READ_USER_DICTIONARY`, `WRITE_USER_DICTIONARY`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`, `READ_CONTACTS`.
   - **No `RECORD_AUDIO`** permission currently exists.
   - **No `INTERNET`** permission currently exists.
2. **Permission Strategy**:
   - **Task 002 (Voice Button Shell)**: No permissions required.
   - **Task 004 (Speech Recognition)**: Requires adding `android.permission.RECORD_AUDIO` to `AndroidManifest.xml` and implementing runtime permission request dialog.
   - **Task 005 (AI Processing)**: Requires adding `android.permission.INTERNET`.
3. **Security Safeguards**:
   - **No Plaintext API Keys**: Secrets must never be committed or compiled into public APKs.
   - **Sensitive Field Exclusions**: Disable AI and voice preview in password and sensitive input types (`InputTypeUtils.isPasswordInputType`).
   - **Zero Keystroke / Clipboard Logging**: Ordinary typing and clipboard content must never be logged or transmitted.

---

## 5. Local Dev Environment Status (`mac-air-m1`)

- **Host Status**: `mac-air-m1` has no local Java Runtime or Android SDK commandline tools installed.
- **CI / Build Pipeline**: Fully operational via GitHub Actions (`Build debug APK`).
- **Local Dev Recommendation**: For fast local development, install OpenJDK 17 (`brew install openjdk@17`) and Android Commandline Tools / Studio when required, while continuing to use CI for reproducible verification.

---

## 6. Minimal Implementation Plan for Task 002 (Voice Button Shell)

1. **Goal**: Introduce the visible microphone button in the top-right toolbar position without permissions or network calls.
2. **Steps**:
   - Verify `ToolbarKey.VOICE` visibility in default toolbar configurations (`defaultToolbarPref`, `defaultPinnedToolbarPref`).
   - Enforce top-right position in the toolbar row (`SuggestionStripView.kt`).
   - Hook `onClickToolbarKey` for `ToolbarKey.VOICE` to show a harmless placeholder indicator (toast or visual active state toggle).
   - Hook `onLongClickToolbarKey` for `ToolbarKey.VOICE` to show a placeholder dialog / bottom sheet for mode selection.
3. **Acceptance Criteria**:
   - Normal keyboard typing remains 100% functional.
   - Clipboard history works with zero regression.
   - Short tap and long press are distinctly handled.
   - CI build succeeds and produces a valid APK.
