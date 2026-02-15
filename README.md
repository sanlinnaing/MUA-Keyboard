# MUA Keyboard - ဗေ ကီးဘုတ်

A Myanmar Unicode keyboard for Android that follows the standards of Unicode.org and Myanmar basic writing system rules.

## Features

### Multi-Language Support
- **Bamar, Mon, Shan, PaOh, Palaung, Karen** (Sgaw, West Pwo, East Pwo) keyboards
- Proper Myanmar Unicode output

### Flick Keyboard
- Japanese-style flick input with 4x3 grid layout
- 5 characters per key — center tap + 4 directional flicks
- **Shift layout** with 12 extra characters (၎င်း, င်္, ဏ္ဍ, ဪ, ဋ္ဌ, ဏ္ဌ, +, ×, ., -, ÷, ,)
- Real-time preview popup showing selected character above the key
- Long press support (e.g., ာ → ါ)
- Directional visual feedback with shadow/mist effect

### Intelligent Suggestions
- **LSTM-based prediction** for Myanmar syllables and English words
- **Trie-based completion** with dictionary lookups
- **Hybrid mode** — LSTM guides Trie for best results (default)
- **User Dictionary** — learned words integrated into suggestions
- **SymSpell spelling correction** for English

### English Typing
- Word suggestions with LSTM prediction
- Autocorrect for 40+ common contractions (dont → don't, Im → I'm, etc.)
- Auto-capitalization after sentence endings
- Standalone "i" → "I" correction

### Emoji Keyboard
- 9 categories: Recent, Smileys, Gestures, Animals, Food, Activities, Travel, Objects, Symbols, Flags
- Recently used emojis stored and persisted across sessions

### Clipboard Integration
- Paste chip in suggestion bar with clipboard preview
- Auto-hidden in sensitive/password fields

### Input Method
- **PrimeBook typing** — type as learned in childhood (Thin Pone Gyi)
- **Logical Order** — Unicode sequence order typing
- Double-tap fast typing
- Smart delete — single character or whole word based on cursor position
- Auto-correct sequences (e.g., Asat + Dot below → Dot below + Asat)
- Proximity correction for reduced typos
- Haptic feedback with adjustable strength

### Themes
- 6 themes: System (auto light/dark), Dark, Green, Blue Gray, Golden Yellow, Light
- Modern flat design with rounded keys and high-resolution vector icons

### Settings
- Suggestion method (Word, Syllable, Both)
- Navigation bar space (Auto/On/Off)
- Hint key labels toggle
- Haptic feedback with strength control
- English autocorrect and auto-capitalization toggles
- Personalization with learned word management

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on device
./gradlew installDebug
```

## Requirements
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 36 (Android 15)
- NDK required for native LSTM/Trie engines

## Release Notes
- [v3.2](RELEASE_NOTES_v3.2.md) — Flick shift layout, preview popup, SymSpell, tuned touch parameters
- [v3.1](RELEASE_NOTES_v3.1.md) — Flick keyboard, LSTM suggestions, English autocorrect, clipboard
- [v3.0](RELEASE_NOTES_v3.0.md) — UI/UX overhaul, emoji keyboard, themes, vector icons

## Contributing

Anyone can fork or reference this source code openly. Pull requests are welcome. For requests or feedback, contact [Myanmar Unicode Area](https://www.facebook.com/groups/mmUnicode).

### Contributors

**Great Help**
- [MUA Admins and Core members](https://www.facebook.com/groups/mmUnicode)

**Language Resources**
- [Ye Zar Ni Aung](https://www.facebook.com/yezarniaung.mdy)
- [Shwun Mi](https://www.facebook.com/shwunmi)
- [Sai Zin Didizone](https://www.facebook.com/saizddzone)
- [Lwin Moe](https://www.facebook.com/lwinmoe)
- [Anonta Mon](https://www.facebook.com/anonta.mon)
- [Talachan Mon](https://www.facebook.com/talachan)
- [Tin Win Htoo](https://www.facebook.com/tinwin.htoo)
- [Tin Maung Thein](https://www.facebook.com/mgtinmaung7)
- [Saw Kyaw Zin Myat](https://www.facebook.com/saw.kyawmyat)
