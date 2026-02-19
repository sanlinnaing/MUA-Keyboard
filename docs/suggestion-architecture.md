# Suggestion System Architecture

## 1. Problem Statement

```mermaid
flowchart TB
    subgraph Problem["The Problem"]
        P1["LSTM/Trie trained on general corpus"]
        P2["Missing: User's personal vocabulary"]
        P3["Examples:<br/>Names: မောင်မောင်, သူဇာ<br/>Places: သာကေတ, လှိုင်သာယာ<br/>Technical terms, slang"]
        P1 --> P2 --> P3
    end

    subgraph Goal["Personalization Goal"]
        G1["Learn words user types<br/>that are NOT in LSTM/Trie"]
        G2["Suggest these words<br/>in appropriate contexts"]
        G1 --> G2
    end

    Problem --> Goal
```

## 2. Current Implementation and Its Limitation

```mermaid
flowchart TB
    subgraph Current["Current: Only Boosts Existing Suggestions"]
        C1["LSTM suggests: A, B, C"]
        C2["Personalization boosts: A -> A+"]
        C3["User wants: D (not in LSTM/Trie)"]
        C4["D never gets suggested!"]
        C1 --> C2 --> C3 --> C4
    end

    subgraph Needed["Needed: Add New Words"]
        N1["LSTM suggests: A, B, C"]
        N2["User Dictionary has: D, E"]
        N3["Combined: A, B, C, D, E"]
        N1 --> N3
        N2 --> N3
    end
```

Current n-gram boost also risks conflicting with LSTM's context understanding:

| Component | Context Understanding |
|-----------|---------------------|
| LSTM | Full sentence (5 syllables) |
| Current Personalization | Simple frequency counts |
| Result | May boost contextually wrong word |

---

## 3. Key Challenge: Myanmar Word Boundary Detection

```mermaid
flowchart TB
    subgraph English["English: Easy"]
        E1["'Hello world'"]
        E2["Split by space"]
        E3["Words: 'Hello', 'world'"]
        E1 --> E2 --> E3
    end

    subgraph Myanmar["Myanmar: Hard"]
        M1["'ကျွန်တော်စာဖတ်တယ်'"]
        M2["No spaces between words!"]
        M3["Syllable is NOT a word"]
        M1 --> M2 --> M3
    end

    subgraph Clarification["Syllable vs Word"]
        C1["LSTM vocab = 10,186 syllables"]
        C2["'ကျွန်' + 'တော်' = 2 syllables"]
        C3["'ကျွန်တော်' = 1 word (I/me)"]
        C1 --> C2 --> C3
    end
```

## 4. Solution: Composing Text for Word Detection

```mermaid
flowchart TB
    subgraph IME["Android IME Composing Concept"]
        I1["setComposingText() - Text being typed"]
        I2["commitText() - Text finalized"]
        I3["finishComposingText() - End composition"]
    end

    subgraph Apply["Apply to Myanmar"]
        A1["User starts typing after space/punctuation"]
        A2["Composing begins, capture context"]
        A3["User types syllables"]
        A4["Space / punctuation / suggestion selected"]
        A5["Commit = WORD boundary detected"]
        A1 --> A2 --> A3 --> A4 --> A5
    end
```

```mermaid
sequenceDiagram
    participant User
    participant IME as MuaKeyboardService
    participant IC as InputConnection
    participant UD as UserDictionary

    Note over IME: composing = false

    User->>IME: Types "သ" (after space)
    IME->>IME: composing = true, capture context
    IME->>IC: setComposingText("သ")

    User->>IME: Types "ူ"
    IME->>IC: setComposingText("သူ")

    User->>IME: Types "ဇ"
    IME->>IC: setComposingText("သူဇ")

    User->>IME: Types "ာ"
    IME->>IC: setComposingText("သူဇာ")

    User->>IME: Presses SPACE
    IME->>IC: commitText("သူဇာ ")
    IME->>IME: composedWord = "သူဇာ"
    IME->>UD: recordIfUnknown(context, "သူဇာ")
    IME->>IME: composing = false
```

---

## 5. Design Decisions

### Context: Use 2+ Syllables

```mermaid
flowchart LR
    subgraph Bad["Single syllable = too broad"]
        B1["Context: 'ကို'"]
        B2["Hundreds of possibilities"]
    end

    subgraph Good["2+ syllables = specific"]
        G1["Context: 'ပေး' + 'ကို'"]
        G2["Fewer, relevant matches"]
    end
```

### Efficient Storage: Context-Keyed Trie

```mermaid
classDiagram
    class UserDictionary {
        -HashMap~String, SyllableTrie~ contextToTrie
        +addWord(context: List~String~, word: String)
        +getSuggestions(context: List~String~, prefix: String) List
    }

    class SyllableTrie {
        -TrieNode root
        +insert(syllables: List~String~, frequency: Int)
        +searchByPrefix(prefixSyllables: List~String~) List~WordEntry~
    }

    class TrieNode {
        -HashMap~String, TrieNode~ children
        -WordEntry wordEntry
        -boolean isEndOfWord
    }

    class WordEntry {
        +String fullWord
        +List~String~ syllables
        +int frequency
        +long lastUsed
    }

    UserDictionary --> SyllableTrie
    SyllableTrie --> TrieNode
    TrieNode --> WordEntry
```

```
Example Storage:

contextKey = "ပေး|ကို"
    |
SyllableTrie:
    root
    +-- "သူ"
    |   +-- "ဇာ" -> WordEntry("သူဇာ", freq=5) [END]
    |   +-- "မ"  -> WordEntry("သူမ", freq=3)   [END]
    |   +-- "ငယ်"
    |       +-- "ချင်း" -> WordEntry("သူငယ်ချင်း", freq=2) [END]

Search prefix "သူ":
-> Returns: ["သူဇာ"(5), "သူမ"(3), "သူငယ်ချင်း"(2)]
-> Time: O(prefix_length + results_count)
```

### Vocabulary Check (Already Available)

```kotlin
// LstmNative.kt - returns -1 if syllable NOT in vocabulary
fun getIndex(syllable: String): Int

// LstmSuggestionEngine.kt - loaded from syll_indices_uni.json
private var syllableToIndex: Map<String, Int>  // 10,186 entries
```

---

## 6. Proposed Architecture

```mermaid
flowchart TB
    subgraph Compose["1. Composing State"]
        CS1["Track start/end of word composition"]
        CS2["Capture 2+ syllable context at start"]
        CS3["On commit: composed word ready"]
    end

    subgraph Record["2. Record to User Dictionary"]
        R1["Break composed word into syllables"]
        R2{"Any syllable<br/>NOT in LSTM vocab?"}
        R3["Store word + context in Trie"]
        R4["Skip (all known)"]
        R1 --> R2
        R2 -->|Yes| R3
        R2 -->|No| R4
    end

    subgraph Suggest["3. Generate Suggestions"]
        S1["LSTM: context-aware predictions"]
        S2["Trie: dictionary matches"]
        S3["User Dict: prefix + context search"]
        S4["Merge, deduplicate, rank"]
        S5["Top 5 suggestions"]
        S1 --> S4
        S2 --> S4
        S3 --> S4
        S4 --> S5
    end

    Compose --> Record --> Suggest
```

### Scoring User Dictionary Matches

```mermaid
flowchart LR
    subgraph Factors["Scoring Factors"]
        F1["Frequency"]
        F2["Recency"]
        F3["Context match"]
        F4["Prefix match length"]
    end

    subgraph Formula["Score"]
        S1["base = frequency x recencyDecay"]
        S2["x contextBoost (1.5 if match)"]
        S3["x prefixRatio"]
    end

    Factors --> Formula
```

### End-to-End Example

```mermaid
flowchart TB
    subgraph Input["User Types: 'ကို သူ'"]
        I1["Context: 'ပေး' + 'ကို'"]
        I2["Current prefix: 'သူ'"]
    end

    subgraph Sources["Get Suggestions From All Sources"]
        S1["LSTM: 'သူက'=80, 'သူမ'=75"]
        S2["Trie: 'သူငယ်ချင်း'=70"]
        S3["User Dict: 'သူဇာ'=85<br/>(context 'ကို' matches, freq=15)"]
    end

    subgraph Result["Merged Result"]
        R1["သူဇာ | သူက | သူမ | သူငယ်ချင်း | သူတို့"]
    end

    Input --> Sources --> Result
```

---

## 7. Comparison

| Aspect | Current Implementation | Proposed Implementation |
|--------|----------------------|------------------------|
| **Purpose** | Boost existing suggestions | Add new words + context matching |
| **Unknown words** | Cannot suggest | Can suggest from User Dict |
| **Context** | Simple n-gram frequency | 2+ syllable context key |
| **Word detection** | N/A | Composing state tracking |
| **Storage** | HashMap frequency counts | Context-keyed Trie |
| **Search** | N/A | Trie prefix search O(m+k) |
| **LSTM conflict** | May override LSTM | Works alongside LSTM |

---

## 8. Implementation Plan

```mermaid
flowchart TB
    subgraph Phase1["Phase 1: User Dictionary + Trie"]
        P1A["Create SyllableTrie class"]
        P1B["Create UserDictionary class"]
        P1C["Context-keyed storage + prefix search"]
    end

    subgraph Phase2["Phase 2: Composing State + Recording"]
        P2A["Track composing start/end in service"]
        P2B["Expose vocab check in SuggestionManager"]
        P2C["Record unknown words with 2+ syllable context"]
    end

    subgraph Phase3["Phase 3: Suggestion Integration"]
        P3A["Query User Dictionary during suggestion"]
        P3B["Score by frequency + context + prefix"]
        P3C["Merge with LSTM/Trie results"]
    end

    subgraph Phase4["Phase 4: Persistence + UI"]
        P4A["Binary serialization for Trie"]
        P4B["Update View Learned Words dialog"]
        P4C["Load/save lifecycle management"]
    end

    Phase1 --> Phase2 --> Phase3 --> Phase4
```

### Next Steps

1. **Create `SyllableTrie.kt`** - Trie with syllable-level nodes and prefix search
2. **Create `UserDictionary.kt`** - Context-keyed Trie storage with frequency tracking
3. **Add composing state tracking** in `MuaKeyboardService.kt`
4. **Expose vocab check** in `SuggestionManager.kt` (delegate to LSTM engine)
5. **Record unknown words** on commit with 2+ syllable context
6. **Query User Dictionary** during suggestion generation, merge results
7. **Add persistence** (binary serialization for the Trie structures)
