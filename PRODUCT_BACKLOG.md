# MUA-Keyboard Product Backlog & Issues

**Project:** Myanmar Unicode Area Keyboard (ဗေ ကီးဘုတ်)
**Package:** com.sanlin.mkeyboard
**Current Version:** 3.0 (versionCode 11)
**Last Updated:** 2026-02-02

---

## Project Overview

A Myanmar Unicode-compliant Android Input Method Editor (IME) supporting multiple Myanmar languages:
- English (QWERTY)
- Myanmar/Burmese (Bamar)
- Shan
- Mon
- Karen (Sgaw, West Pwo, East Pwo)

---

## Environment & Rules

### Build Configuration
| Setting | Current Value | Status |
|---------|---------------|--------|
| compileSdk | 36 (Android 15) | OK |
| targetSdkVersion | 36 | OK |
| minSdkVersion | 26 (Android 8.0) | OK |
| Gradle | 8.13 | OK |
| Kotlin | 2.1.0 | OK |
| Java Target | 17 | OK |

### Dependencies Status
| Dependency | Version | Status |
|------------|---------|--------|
| androidx.core:core-ktx | 1.12.0 | ✅ OK |
| androidx.appcompat:appcompat | 1.6.1 | ✅ OK |
| androidx.preference:preference-ktx | 1.2.1 | ✅ OK |
| junit | 4.13.2 | ✅ OK |
| mockito-core | 5.8.0 | ✅ OK |

### Code Rules
1. **Language:** Prefer Kotlin for new code; migrate Java files progressively
2. **Unicode:** Follow Myanmar Unicode standards for character reordering
3. **API Level:** Ensure compatibility with Android 8.0+ (API 26)
4. **No deprecated APIs:** Replace all deprecated Android APIs

---

## Issues

### CRITICAL (Must Fix)

| ID | Issue | File(s) | Status |
|----|-------|---------|--------|
| C-01 | Deprecated Support Library | app/build.gradle | ✅ DONE - Migrated to AndroidX |
| C-02 | Version Mismatch | AndroidManifest.xml | ✅ DONE - Fixed |

### HIGH (Should Fix Soon)

| ID | Issue | File(s) | Status |
|----|-------|---------|--------|
| H-01 | Deprecated PreferenceActivity | ImePreferences.java | ✅ DONE - Migrated to PreferenceFragmentCompat |
| H-02 | Deprecated getDrawable() | MyIME.java | ✅ DONE - Using ContextCompat.getDrawable() |
| H-03 | Mixed Codebase | Multiple | ⏳ IN PROGRESS - ~80% Kotlin now |

### MEDIUM (Technical Debt)

| ID | Issue | File(s) | Status |
|----|-------|---------|--------|
| M-01 | No Unit Tests | - | ⏳ PENDING - Character reordering needs tests |
| M-02 | Magic Unicode Numbers | BamarKeyboard.kt, etc. | ✅ DONE - MyanmarUnicode constants created |
| M-03 | TODO Comments | Multiple | ⏳ PARTIAL - Some cleanup done |
| M-04 | Excessive Logging | Various | ⏳ PARTIAL - Some cleanup done |
| M-05 | HTTP URL | ime_preferences.xml | ⏳ PENDING |

### LOW (Nice to Have)

| ID | Issue | File(s) | Status |
|----|-------|---------|--------|
| L-01 | Commented Code | Various | ⏳ PARTIAL |
| L-02 | ProGuard Disabled | app/build.gradle | ⏳ PENDING |

---

## Product Backlog

### Epic 1: AndroidX Migration (Priority: CRITICAL) ✅ COMPLETED

| Task | Description | Status |
|------|-------------|--------|
| 1.1 | Add `android.useAndroidX=true` to gradle.properties | ✅ Done |
| 1.2 | Replace `com.android.support:support-v4` with `androidx.core:core` | ✅ Done |
| 1.3 | Replace `com.android.support:appcompat-v7` with `androidx.appcompat:appcompat` | ✅ Done |
| 1.4 | Update all import statements | ✅ Done |
| 1.5 | Test all features after migration | ✅ Done |

### Epic 2: Deprecation Fixes (Priority: HIGH) ✅ COMPLETED

| Task | Description | Status |
|------|-------------|--------|
| 2.1 | Migrate ImePreferences to PreferenceFragmentCompat | ✅ Done |
| 2.2 | Fix getDrawable() deprecation in MyIME.java | ✅ Done |
| 2.3 | Sync version codes (manifest vs build.gradle) | ✅ Done |
| 2.4 | Review and update AndroidManifest.xml for API 36 | ✅ Done |

### Epic 3: Kotlin Migration (Priority: HIGH) ⏳ IN PROGRESS (~80%)

| Task | Description | Status |
|------|-------------|--------|
| 3.1 | Convert MyIME.java to Kotlin | ✅ Done (MuaKeyboardService.kt) |
| 3.2 | Convert MyKeyboardView.java to Kotlin | ✅ Done (MuaKeyboardView.kt) |
| 3.3 | Convert MyKeyboard.java to Kotlin | ✅ Done (Keyboard.kt) |
| 3.4 | Convert MyConfig.java to Kotlin (object) | ✅ Done (KeyboardConfig.kt) |
| 3.5 | Convert ImePreferences.java to Kotlin | ⏳ Pending |

### Epic 4: Code Quality (Priority: MEDIUM) ⏳ PARTIAL

| Task | Description | Status |
|------|-------------|--------|
| 4.1 | Create Unicode constants file for Myanmar characters | ✅ Done (MyanmarUnicode.kt) |
| 4.2 | Remove TODO/auto-generated comments | ⏳ Partial |
| 4.3 | Add BuildConfig.DEBUG checks for logging | ⏳ Partial |
| 4.4 | Remove commented-out code | ⏳ Partial |
| 4.5 | Fix HTTP to HTTPS in preferences | ⏳ Pending |

### Epic 5: Testing (Priority: MEDIUM) ⏳ PENDING

| Task | Description | Status |
|------|-------------|--------|
| 5.1 | Add unit tests for BamarKeyboard character reordering | ⏳ Pending |
| 5.2 | Add unit tests for MonKeyboard character reordering | ⏳ Pending |
| 5.3 | Add unit tests for ShanKeyboard | ⏳ Pending |
| 5.4 | Add instrumentation tests for keyboard input | ⏳ Pending |

### Epic 6: Build Optimization (Priority: LOW) ⏳ PENDING

| Task | Description | Status |
|------|-------------|--------|
| 6.1 | Enable ProGuard/R8 minification for release builds | ⏳ Pending |
| 6.2 | Configure build variants (debug/release) properly | ⏳ Pending |
| 6.3 | Add lint checks and fix warnings | ⏳ Pending |

---

## Effort Legend
- **S (Small):** < 1 hour
- **M (Medium):** 1-4 hours
- **L (Large):** 4-8 hours
- **XL (Extra Large):** 8+ hours

---

## Recommended Priority Order

1. ~~**Immediate:** C-01 (AndroidX Migration) - Blocks modern Android compatibility~~ ✅ DONE
2. ~~**Next:** C-02, H-02 (Version sync, deprecation fixes) - Quick wins~~ ✅ DONE
3. ~~**Soon:** H-01 (PreferenceActivity migration) - User-facing settings~~ ✅ DONE
4. **Ongoing:** Complete Kotlin migration (~20% remaining)
5. **Next Priority:** K-01 (Delete selection bug fix)
6. **When Time Allows:** Unit tests, ProGuard, Future enhancements

---

## File Statistics

| Category | Count |
|----------|-------|
| Total Source Files | 15+ |
| Kotlin Files | 12+ |
| Java Files | 3 |
| XML Layout Files | 10 |
| XML Keyboard Definitions | 65 |
| Supported Languages | 7 |
| Theme Options | 6 (including system-following default) |

---

## Completed Fixes (2026-01-31)

| Fix | Description | Files Modified |
|-----|-------------|----------------|
| AndroidX Migration | Migrated from Support Library to AndroidX | build.gradle, gradle.properties, all Java/Kotlin files |
| Version Mismatch | Removed duplicate version from manifest | AndroidManifest.xml |
| Deprecated getDrawable() | Fixed with ContextCompat.getDrawable() | MyIME.kt |
| PreferenceActivity | Migrated to PreferenceFragmentCompat | ImePreferences.java, InputMethodSettingsFragment.java |
| Kotlin Migration | Converted MyIME, MyConfig, MyKeyboard, MyKeyboardView to Kotlin | Multiple .kt files |
| Theme Crash | Fixed Holo theme conflict with AppCompat | styles.xml, deleted values-v11/v14 folders |
| Toolbar Overlap | Fixed preferences toolbar covering content | activity_preferences.xml, ImePreferences.java |
| IME Nav Bar | Added padding for IME navigation bar | MyIME.kt |
| Language Selection | Fixed subtype not saving when keyboard not enabled | InputMethodSettingsImpl.java |
| Subtype UI Refresh | Fixed preference not updating after selecting languages | InputMethodSettingsFragment.java, ImePreferences.java |

---

## Completed Features (v3.0 - 2026-02-02)

### New Features

| Feature | Description | Files Added/Modified |
|---------|-------------|----------------------|
| Modern Emoji Keyboard | Complete redesign with scrollable grid, 9 categories, RecyclerView | emoji/EmojiCategory.kt, emoji/EmojiView.kt |
| Recent Emojis | Stores up to 50 recent emojis with persistence | emoji/RecentEmojiManager.kt |
| Vector Icons | High-resolution vector drawables for special keys | sym_keyboard_shift.xml, sym_keyboard_delete.xml, sym_keyboard_return.xml, sym_keyboard_language_switch.xml |
| Light Theme | New explicit light theme option | light_*.xml drawables, light_keyboard.xml |
| System Theme Following | Default theme follows system dark/light mode | MuaKeyboardService.kt, EmojiView.kt |
| Settings Page Theming | Settings follows system theme | styles.xml (DayNight theme) |
| Popup Keyboard | Long press shows popup for multi-character keys | MuaKeyboardView.kt |
| Uppercase Labels | English keyboard shows uppercase when shifted | MuaKeyboardView.kt |
| Space Long Press | Shows system keyboard picker | MuaKeyboardView.kt, MuaKeyboardService.kt, OnKeyboardActionListener.kt |
| Proximity Correction | Smart key detection for nearby keys | MuaKeyboardView.kt, KeyboardConfig.kt |
| Haptic Feedback | Configurable vibration with strength control | HapticManager.kt, settings |

### Bug Fixes

| Fix | Description | Files Modified |
|-----|-------------|----------------|
| Navigation Bar | Fixed transparent nav bar showing content behind keyboard | MuaKeyboardService.kt (WindowInsets API) |
| Settings Access | Fixed settings not opening from system keyboard settings | method.xml |
| Popup in IME | Fixed popup keyboard not showing (switched to canvas drawing) | MuaKeyboardView.kt |

### UI/UX Improvements

| Improvement | Description |
|-------------|-------------|
| Flat Key Design | Modern borderless keys with 8dp rounded corners |
| Color Scheme | Distinct colors for normal vs special keys |
| Key Gaps | Consistent uniform spacing between keys |
| Pressed States | Visual feedback with lighter shade on press |
| Hint Labels | Repositioned to top-right corner as superscript |
| Top Gap | Added gap at top of keyboard matching key spacing |
| Emoji Tab Styling | Bottom border line and bordered selected tab |
| Theme Names | Renamed for clarity (Dark, Green, Blue Gray, Golden Yellow, Light) |

---

## Remaining Backlog

### Known Issues (To Fix)

| ID | Issue | Priority | Description |
|----|-------|----------|-------------|
| K-01 | Delete Selection | MEDIUM | Delete key does not delete selected text in some apps |

### Future Enhancements (Nice to Have)

| ID | Feature | Priority | Description |
|----|---------|----------|-------------|
| F-01 | Emoji Search | LOW | Add search functionality within emoji keyboard |
| F-02 | Emoji Skin Tones | LOW | Support skin tone variants for applicable emojis |
| F-03 | Keyboard Height Adjustment | LOW | Allow users to adjust keyboard height |
| F-04 | Custom Theme Colors | LOW | Let users customize theme colors |
| F-05 | Word Prediction | MEDIUM | Add predictive text input |
| F-06 | Swipe Typing | MEDIUM | Add gesture/swipe typing support |
| F-07 | Clipboard Manager | LOW | Built-in clipboard history |
| F-08 | One-handed Mode | LOW | Compact keyboard for one-handed use |

### Technical Debt (Ongoing)

| ID | Task | Priority | Status |
|----|------|----------|--------|
| T-01 | Unit Tests for Character Reordering | MEDIUM | Not Started |
| T-02 | Remove Remaining TODO Comments | LOW | Partial |
| T-03 | Enable ProGuard for Release | LOW | Not Started |
| T-04 | Complete Kotlin Migration | LOW | ~80% Complete |
| T-05 | Add Instrumentation Tests | LOW | Not Started |

---

## Notes

- Project was originally written ~10 years ago
- Recent updates (2 years ago) upgraded to SDK 34, now 36
- v3.0 is a major UI/UX overhaul release
- Core keyboard logic is functional and well-structured
- AndroidX migration completed
- Most critical technical debt resolved
