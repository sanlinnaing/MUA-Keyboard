# MUA Keyboard v3.1 Release Notes

**Release Date:** February 2026
**Version Code:** 12
**Version Name:** 3.1

---

## Overview

This release introduces the Myanmar Flick Keyboard, intelligent word suggestions with LSTM-based prediction, English autocorrect and auto-capitalization, and various bug fixes for Myanmar text handling.

---

## New Features

### Myanmar Flick Keyboard
- **Japanese-style flick input** - Gesture-based keyboard with 4x3 grid layout
- **5 characters per key** - Center tap + 4 directional flicks (up/down/left/right)
- **Directional visual feedback** - Shadow/mist effect with edge highlight bar when flicking
- **Long press support** - Long press ာ to input ါ
- **Double-tap punctuation** - Single tap for ၊, double tap for ။
- **Optimized vowel layout**:
  - Key 6: ေ/ိ/ီ/ဲ/း (vowel signs)
  - Key 9: ာ/ံ/့/ု/ူ (vowel signs, long press for ါ)

### Suggestion Bar
- **LSTM-based prediction** - Neural network model for Myanmar syllable and English word prediction
- **Trie-based completion** - Fast prefix matching for word completions
- **N-gram fallback** - Bigram model when LSTM is unavailable
- **Theme support** - Matches keyboard theme (light/dark variants)
- **Scrollable chips** - Horizontally scrollable suggestion chips

### English Typing Enhancements
- **Word suggestions** - LSTM-based next word prediction with prefix completion
- **Autocorrect for contractions** - Automatically fixes common contractions:
  - Im → I'm, Ive → I've, Id → I'd, Ill → I'll
  - dont → don't, wont → won't, cant → can't
  - youre → you're, theyre → they're, hes → he's
  - And 40+ more common contractions
- **Auto-capitalization** - Automatically capitalizes after sentence endings (. ! ?)
- **Keyboard shift sync** - Keyboard shows shift state when auto-capitalize is active
- **Standalone "i" fix** - Automatically capitalizes standalone "i" to "I"

### Clipboard Integration
- **Paste chip** - Shows clipboard preview in suggestion bar (initially centered)
- **Clipboard icon** - Left-side paste button appears after user starts typing
- **Real-time updates** - Clipboard content updates in real-time via listener
- **Text preview** - Shows up to 30 characters of clipboard content with ellipsis

### Settings
- **Suggestion method** - Choose between LSTM, N-gram, or disabled
- **Suggestion order** - Option to reverse suggestion order
- **English suggestions** - Toggle English word suggestions
- **Auto-capitalize** - Toggle automatic capitalization
- **Autocorrect** - Toggle contraction autocorrection

---

## Bug Fixes

### Myanmar Text Handling
- **Fixed medial reordering in double-tap** - When typing ေ+consonant then double-tapping to get a medial (e.g., ေ+တ+ွ via double-tap), the medial now correctly inserts between consonant and ေ (produces တွေ not တေွ)
- **Fixed ZWSP handling on delete** - When deleting consonant before ေ (e.g., ကယ်အေ → delete အ), ZWSP is now added to prevent ေ from visually attaching to previous cluster
- **Fixed medial delete** - When deleting medial before ေ (e.g., တွေ → delete ွ), no ZWSP is added since consonant remains (produces တေ correctly)
- **Fixed flick keyboard long press** - Long press characters now go through input handler for proper Myanmar text processing

---

## Technical Changes

### Native C++ Engines
- `lstm_engine.cpp` - LSTM inference engine for word/syllable prediction
- `ngram_engine.cpp` - N-gram (bigram) model for word prediction
- `trie.cpp` - Trie data structure for fast prefix matching

### New Files
- `keyboard/model/FlickKey.kt` - Flick key data model with 5-direction character mapping
- `keyboard/model/FlickKeyboard.kt` - Flick keyboard layout model
- `view/FlickKeyboardView.kt` - Custom view for flick keyboard rendering
- `view/FlickPreviewPopup.kt` - Preview popup for flick gestures
- `view/SuggestionBarView.kt` - Suggestion bar UI component
- `suggestion/SuggestionManager.kt` - Manages suggestion engines and routing
- `suggestion/LstmSuggestionEngine.kt` - LSTM-based Myanmar suggestion engine
- `suggestion/EnglishLstmSuggestionEngine.kt` - LSTM-based English suggestion engine
- `suggestion/NgramSuggestionEngine.kt` - N-gram based suggestion engine
- `autocorrect/AutoCorrector.kt` - Contraction autocorrection with skip tracking
- `autocorrect/AutoCapitalizer.kt` - Auto-capitalization logic

### Model Files (in assets/)
- `syll_predict_model.bin` - Myanmar LSTM model (~22MB)
- `en_lstm_model.bin` - English LSTM model (~29MB)
- `en_word_freq.txt` - English word frequency dictionary (333K words)
- `myanmar_dict_trie.bin` - Myanmar dictionary trie (~4.6MB)

---

## Compatibility
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 36 (Android 15)
- NDK required for native LSTM/Trie engines

---

## Upgrade Notes
- First launch may take a few seconds to initialize suggestion engines
- English suggestions require ~30MB additional storage for model files
- Flick keyboard can be accessed via language switch or settings
