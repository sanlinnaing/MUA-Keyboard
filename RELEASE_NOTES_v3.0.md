# MUA Keyboard v3.0 Release Notes

**Release Date:** February 2026
**Version Code:** 11
**Version Name:** 3.0

---

## Overview

This is a major release featuring a complete UI/UX overhaul, modern emoji keyboard, improved theming system, and numerous quality-of-life improvements.

---

## New Features

### Modern Emoji Keyboard
- **Complete redesign** with scrollable grid layout and category tabs
- **9 emoji categories**: Recent, Smileys, Gestures, Animals, Food, Activities, Travel, Objects, Symbols, and Flags
- **Recent Emojis tab** - Automatically stores up to 50 recently used emojis, persisted across app restarts
- **Fixed height layout** (4 rows) that works consistently across all phone models
- **Category tab styling** with bottom border line and bordered selected tab for better visual distinction
- **Language-specific back button** - Shows "ABC" for English, "ကခဂ" for Burmese/Mon/Karen, "ၵၶင" for Shan

### Theme Improvements
- **New Light theme** added as an explicit option in theme selection
- **System theme following** - Default theme now automatically switches between light and dark based on system settings
- **Renamed themes** for clarity:
  - "Flat (Black)" → "Dark"
  - "Flat (Green)" → "Green"
  - "Blue Gray Normal" → "Blue Gray"
  - "Gold Normal" → "Golden Yellow"
- **Settings page theming** - Settings activity now follows system dark/light mode using DayNight theme

### Keyboard UI Enhancements
- **High-resolution vector icons** for Shift, Enter, Backspace, and Globe keys (replacing old low-res PNGs)
- **Popup keyboard for long press** - Long pressing keys with multiple characters shows a popup selector drawn directly on the keyboard canvas with elevation effect
- **Uppercase key labels** - English keyboard now displays uppercase letters when Shift is pressed
- **Navigation bar handling** - Fixed transparent navigation bar issue; keyboard background now extends properly to cover the navigation area
- **Top gap added** - Consistent gap at the top of keyboard matching the spacing between keys

### Keyboard Layout Design Improvements
- **Modern flat design** - Clean, borderless key design with rounded corners (8dp radius)
- **Improved color scheme** - Distinct colors for normal keys and special keys (Shift, Delete, Enter, etc.)
- **Consistent key gaps** - Uniform spacing between keys for better visual appearance
- **Better pressed states** - Lighter shade feedback when keys are pressed
- **Hint labels repositioned** - Popup character hints now displayed in top-right corner as superscript style

### Input Method Improvements
- **Proximity correction** - Smart key detection that considers nearby keys when typing, reducing typos from slightly off-center taps (configurable in settings)
- **Haptic feedback** - Configurable vibration feedback with adjustable strength
- **Space bar long press** - Long pressing the space bar now opens the system input method picker for quick keyboard switching
- **Settings accessibility** - MUA Keyboard settings can now be accessed directly from System → Language → Keyboards → MUA Keyboard

---

## Technical Changes

### Architecture
- Popup keyboard implemented using direct canvas drawing instead of PopupWindow for better IME compatibility
- WindowInsets API used for proper navigation bar handling across different devices and navigation modes
- RecentEmojiManager using SharedPreferences for persistent emoji history storage

### Files Added
- `emoji/EmojiCategory.kt` - Enum containing all emoji categories with curated emoji lists
- `emoji/EmojiView.kt` - Modern emoji keyboard view with RecyclerView grid
- `emoji/RecentEmojiManager.kt` - Manager for storing and retrieving recent emojis
- `drawable/sym_keyboard_shift.xml` - Vector drawable for shift key
- `drawable/sym_keyboard_delete.xml` - Vector drawable for backspace key
- `drawable/sym_keyboard_return.xml` - Vector drawable for enter key
- `drawable/sym_keyboard_language_switch.xml` - Vector drawable for globe/language switch key
- `drawable/light_*.xml` - Light theme drawable resources
- `layout/light_keyboard.xml` - Light theme keyboard layout

### Files Modified
- `service/MuaKeyboardService.kt` - Theme selection, emoji view integration, space long press handler
- `view/MuaKeyboardView.kt` - Popup keyboard drawing, uppercase labels, long press handling
- `view/OnKeyboardActionListener.kt` - Added onSpaceLongPress callback
- `res/xml/method.xml` - Fixed settings activity path
- `res/values/styles.xml` - Changed to DayNight theme
- `res/values/array.xml` - Updated theme names

---

## Bug Fixes
- Fixed navigation bar transparency showing content behind keyboard
- Fixed settings not opening from system keyboard settings
- Fixed popup keyboard not showing in IME context (switched from PopupWindow to canvas drawing)

---

## Compatibility
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 36 (Android 15)
- Tested on various devices with different navigation modes (gesture, 3-button, 2-button)

---

## Known Issues
- Delete key does not delete selected text in some apps (deferred for future investigation)

---

## Upgrade Notes
- Users upgrading from v2.x will have their theme preference preserved
- Recent emojis will start empty and populate as users select emojis
