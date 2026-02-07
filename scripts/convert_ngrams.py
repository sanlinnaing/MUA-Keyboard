#!/usr/bin/env python3
"""
Convert n-gram text files to compact binary format for mobile keyboard.

Binary format:
- Header: magic(4) + version(4) + vocab_size(4) + bigram_count(4)
- Vocabulary: [word_len(2) + word_bytes(var)] × vocab_size
- Word index mapping stored separately for lookup
- Bigrams: [word1_idx(4) + word2_idx(4) + count(4)] × bigram_count

This creates two files:
1. en_ngram_vocab.bin - Vocabulary with frequencies
2. en_ngram_bigram.bin - Bigram data
"""

import struct
import os
from collections import defaultdict

# Configuration
MAX_VOCAB_SIZE = 50000  # Top 50K words
MAX_BIGRAM_COUNT = 200000  # Top 200K bigrams
MIN_WORD_FREQ = 1000000  # Minimum frequency for vocabulary
MIN_BIGRAM_FREQ = 100000  # Minimum frequency for bigrams

MAGIC = 0x4E47524D  # "NGRM"
VERSION = 1

def load_unigrams(filepath):
    """Load unigrams and return dict of word -> frequency."""
    unigrams = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                word = parts[0].lower()
                try:
                    freq = int(parts[1])
                    # Filter: only alphabetic words, length 1-20
                    if word.isalpha() and 1 <= len(word) <= 20:
                        unigrams[word] = freq
                except ValueError:
                    continue
    return unigrams

def load_bigrams(filepath, vocab_set):
    """Load bigrams where both words are in vocabulary."""
    bigrams = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                words = parts[0].lower().split()
                if len(words) == 2:
                    word1, word2 = words
                    try:
                        freq = int(parts[1])
                        # Both words must be in vocabulary
                        if word1 in vocab_set and word2 in vocab_set:
                            bigrams[(word1, word2)] = freq
                    except ValueError:
                        continue
    return bigrams

def write_vocab_binary(vocab_list, filepath):
    """
    Write vocabulary to binary file.
    Format: magic(4) + version(4) + count(4) + [len(2) + word + freq(8)] × count
    """
    with open(filepath, 'wb') as f:
        # Header
        f.write(struct.pack('<I', MAGIC))
        f.write(struct.pack('<I', VERSION))
        f.write(struct.pack('<I', len(vocab_list)))

        # Words with frequencies
        for word, freq in vocab_list:
            word_bytes = word.encode('utf-8')
            f.write(struct.pack('<H', len(word_bytes)))
            f.write(word_bytes)
            # Normalize frequency to log scale (0-10000)
            import math
            norm_freq = min(10000, int(math.log10(freq + 1) * 1000))
            f.write(struct.pack('<H', norm_freq))

    print(f"Wrote {len(vocab_list)} words to {filepath}")
    print(f"File size: {os.path.getsize(filepath) / 1024:.1f} KB")

def write_bigram_binary(bigrams_list, word_to_idx, filepath):
    """
    Write bigrams to binary file.
    Format: magic(4) + version(4) + count(4) + [idx1(2) + idx2(2) + freq(2)] × count
    """
    with open(filepath, 'wb') as f:
        # Header
        f.write(struct.pack('<I', MAGIC))
        f.write(struct.pack('<I', VERSION))
        f.write(struct.pack('<I', len(bigrams_list)))

        # Bigrams
        import math
        for (word1, word2), freq in bigrams_list:
            idx1 = word_to_idx.get(word1, 0)
            idx2 = word_to_idx.get(word2, 0)
            # Normalize frequency to log scale (0-10000)
            norm_freq = min(10000, int(math.log10(freq + 1) * 1000))
            f.write(struct.pack('<HHH', idx1, idx2, norm_freq))

    print(f"Wrote {len(bigrams_list)} bigrams to {filepath}")
    print(f"File size: {os.path.getsize(filepath) / 1024:.1f} KB")

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    assets_dir = os.path.join(script_dir, '..', 'app', 'src', 'main', 'assets')

    # Load unigrams
    print("Loading unigrams...")
    unigrams = load_unigrams(os.path.join(script_dir, 'unigrams_raw.txt'))
    print(f"Loaded {len(unigrams)} unigrams")

    # Sort by frequency and take top N
    sorted_unigrams = sorted(unigrams.items(), key=lambda x: -x[1])
    top_unigrams = sorted_unigrams[:MAX_VOCAB_SIZE]
    vocab_set = set(word for word, _ in top_unigrams)
    word_to_idx = {word: idx for idx, (word, _) in enumerate(top_unigrams)}

    print(f"Selected top {len(top_unigrams)} words")
    print(f"Top 10 words: {[w for w, _ in top_unigrams[:10]]}")

    # Load bigrams
    print("\nLoading bigrams...")
    bigrams = load_bigrams(os.path.join(script_dir, 'bigrams_raw.txt'), vocab_set)
    print(f"Loaded {len(bigrams)} bigrams (filtered to vocabulary)")

    # Sort by frequency and take top N
    sorted_bigrams = sorted(bigrams.items(), key=lambda x: -x[1])
    top_bigrams = sorted_bigrams[:MAX_BIGRAM_COUNT]

    print(f"Selected top {len(top_bigrams)} bigrams")
    print(f"Top 10 bigrams: {[f'{w1} {w2}' for (w1, w2), _ in top_bigrams[:10]]}")

    # Write binary files
    print("\nWriting binary files...")
    os.makedirs(assets_dir, exist_ok=True)

    write_vocab_binary(top_unigrams, os.path.join(assets_dir, 'en_ngram_vocab.bin'))
    write_bigram_binary(top_bigrams, word_to_idx, os.path.join(assets_dir, 'en_ngram_bigram.bin'))

    # Also create a word-to-index mapping for the native code
    # This will be loaded alongside the vocab

    print("\nDone!")
    print(f"\nTotal asset size: {(os.path.getsize(os.path.join(assets_dir, 'en_ngram_vocab.bin')) + os.path.getsize(os.path.join(assets_dir, 'en_ngram_bigram.bin'))) / 1024:.1f} KB")

if __name__ == '__main__':
    main()
