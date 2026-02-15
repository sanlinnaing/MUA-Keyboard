# MUA Keyboard v3.2 Release Notes

**Release Date:** February 2026
**Version Code:** 13
**Version Name:** 3.2

---

## Overview

This release adds a shift layout for the flick keyboard with extra Myanmar characters, a real-time flick preview popup, improved suggestion intelligence, English spelling correction, and various UX refinements and bug fixes.

---

## New Features

### Flick Keyboard Shift Layout
- **Shift key** on the top-left side key switches the 3x4 main grid to a secondary layout of extra characters
- **12 extra characters** available via shift: ၎င်း, င်္, ဏ္ဍ, ဪ, ဋ္ဌ, ဏ္ဌ, +, ×, ., -, ÷, ,
- **Auto-unshift** - tapping any shifted character inputs it and returns to the normal layout
- **Visual indicator** - shift key highlights blue when active

### Flick Preview Popup
- **Real-time character preview** - a small popup bubble appears above the touched flick key showing the currently selected character
- **Direction tracking** - preview updates instantly as the user changes flick direction
- **Compact design** - small, pill-shaped popup with blue border matching the flick highlight color
- Helps users see what character will be input even when their finger covers the key

### LSTM-Guided Suggestion Pipeline
- **Hybrid suggestion system** - LSTM model guides Trie-based dictionary lookups for better word predictions
- **User Dictionary integration** - learned words are incorporated into suggestion results
- **Both mode** - new default suggestion method combines Word (Trie) and Syllable (LSTM) for best results

### English Spelling Correction
- **SymSpell-based correction** - fast symmetric delete spelling correction for English text
- **Integrated with suggestion bar** - spelling corrections appear alongside word suggestions

### Navigation Bar Space Setting
- **Auto/On/Off control** - new setting to control the space below the keyboard for navigation bar
- **Auto mode** (default) - automatically detects gesture navigation vs button navigation

### Clipboard Paste UX Improvement
- **Suppressed in sensitive fields** - suggestion bar and paste chip are hidden in password and sensitive input fields

### Auto-Space After Punctuation
- **Automatic spacing** - space is inserted automatically after sentence-ending punctuation (. ! ?)
- **Skip-after-delete** - autocorrect skip state is properly reset when user deletes text

---

## UX Improvements

### Punctuation Key (Flick Keyboard)
- **Swapped tap behavior** - single tap now outputs ။ (more common), double tap outputs ၊
- **Updated popup** - long-press popup reflects the new tap order

### Tuned Touch Parameters
- **Proximity threshold** reduced from 20dp to 10dp - fixes occasional key mismatches on high-density screens
- **Flick threshold** reduced from 40dp to 24dp - flick gestures feel more responsive, especially vertical flicks

### Default Settings Updated
- Suggestion method defaults to "Both" (Word + Syllable)
- Haptic feedback on by default with low vibration strength
- Hint key labels shown by default

---

## Bug Fixes

### Myanmar Text Handling
- **Fixed double-tap composing** - composing underline now properly maintained during double-tap sequences
- **Fixed locale initialization** - keyboard correctly loads saved layout on first start after switching from another IME
- **Fixed E-vowel reordering** - ေ reordering works correctly in all edge cases
- **Fixed input field detection** - proper identification of text field types for composing behavior

### Composing State
- **Improved composing state management** - composing region correctly tracks Myanmar syllable boundaries
- **Fixed suggestion commit behavior** - suggestions properly commit and reset composing state

### Settings
- **Separated autocorrect from auto-capitalize** - these are now independent settings

---

## Compatibility
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 36 (Android 15)
- NDK required for native LSTM/Trie engines
