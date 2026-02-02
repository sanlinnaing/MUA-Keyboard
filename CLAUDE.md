# CLAUDE.md - Project Guidelines for Claude Code

## Project Overview

**Name:** MUA-Keyboard (Myanmar Unicode Area Keyboard / ဗေ ကီးဘုတ်)
**Type:** Android Input Method Editor (IME)
**Package:** com.sanlin.mkeyboard
**Min SDK:** 26 (Android 8.0)
**Target SDK:** 36 (Android 15)

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run lint checks
./gradlew lint

# Run unit tests
./gradlew test

# Install on connected device
./gradlew installDebug
```

## Project Structure

```
app/src/main/
├── java/com/sanlin/mkeyboard/
│   ├── MyIME.java              # Main IME Service (545 lines)
│   ├── MyKeyboard.java         # Base keyboard class
│   ├── MyKeyboardView.java     # Custom keyboard view
│   ├── MyConfig.java           # Runtime configuration
│   ├── ImePreferences.java     # Settings activity
│   ├── BamarKeyboard.kt        # Burmese keyboard logic
│   ├── MonKeyboard.kt          # Mon keyboard logic
│   ├── ShanKeyboard.kt         # Shan keyboard logic
│   └── EastPwoKarenKeyboard.kt # Karen keyboard logic
├── res/
│   ├── xml/                    # 65 keyboard layout definitions
│   ├── layout/                 # UI layouts
│   ├── drawable-*/             # Icons and graphics
│   └── values/                 # Strings, colors, styles
└── AndroidManifest.xml
```

## Key Files

| File | Purpose |
|------|---------|
| `app/build.gradle` | App dependencies and SDK config |
| `build.gradle` | Root Gradle config |
| `gradle.properties` | Gradle settings (AndroidX flags go here) |
| `app/src/main/AndroidManifest.xml` | App manifest |
| `app/src/main/res/xml/method.xml` | IME subtype definitions |

## Coding Standards

### Language
- **Prefer Kotlin** for all new code
- Migrate existing Java files to Kotlin when modifying them
- Use Kotlin idioms (null safety, extension functions, etc.)

### Unicode Handling
- Myanmar Unicode range: U+1000 to U+109F
- Character reordering is critical - test thoroughly
- Key constants (define in a constants file):
  - Vowel E: 0x1031
  - Asat: 0x103A
  - Medials: 0x103B-0x103E

### Android Best Practices
- Use AndroidX libraries (not legacy support library)
- Use ContextCompat for backward-compatible APIs
- Check BuildConfig.DEBUG before logging
- Follow Material Design guidelines

## Current Issues (Priority Order)

1. **CRITICAL:** Migrate from Support Library to AndroidX
2. **HIGH:** Fix deprecated APIs (PreferenceActivity, getDrawable)
3. **MEDIUM:** Complete Kotlin migration
4. **MEDIUM:** Add unit tests for character reordering

## Testing

### Manual Testing
1. Install APK on device/emulator
2. Enable keyboard in Settings > System > Languages & Input > On-screen keyboard
3. Test each language: English, Burmese, Shan, Mon, Karen
4. Test character reordering with complex sequences
5. Test all 5 themes

### Character Reordering Test Cases
```
# Burmese - Vowel E should reorder
Input: က + ေ → Output: ကေ (visually ေက)

# Asat + Dot Below correction
Input: ် + ့ → Output: ့် (Dot below before Asat)
```

## Dependencies

### Current (Needs Migration)
```groovy
implementation 'com.android.support:support-v4:28.0.0'      // DEPRECATED
implementation 'com.android.support:appcompat-v7:28.0.0'    // DEPRECATED
```

### Target (After Migration)
```groovy
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.preference:preference-ktx:1.2.1'
```

## Git Workflow

- **Main branch:** develop
- Commit messages: Use conventional commits (feat:, fix:, refactor:, etc.)
- Test builds before committing

## Notes for Claude

- This is a Myanmar language keyboard - Unicode compliance is critical
- Character reordering logic is complex - be careful when modifying
- The project uses a mix of Java and Kotlin - migration is in progress
- Always run `./gradlew assembleDebug` to verify changes compile
- IME services require device testing - emulator may not show all issues
