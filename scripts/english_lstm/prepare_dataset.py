#!/usr/bin/env python3
"""
Prepare English text dataset for LSTM next-word prediction training.

This script:
1. Loads text data from various sources (txt, csv, json)
2. Tokenizes into words
3. Builds vocabulary (top N words)
4. Creates training sequences
5. Saves processed data for training

Usage:
    python prepare_dataset.py --input data.txt --output processed/ --vocab_size 20000
"""

import argparse
import json
import os
import re
from collections import Counter
from pathlib import Path
import pickle

# Configuration
DEFAULT_VOCAB_SIZE = 20000
DEFAULT_SEQUENCE_LENGTH = 5
MIN_WORD_FREQ = 5


def clean_text(text):
    """Clean and normalize text."""
    # Convert to lowercase
    text = text.lower()

    # Replace common contractions
    contractions = {
        "n't": " not",
        "'re": " are",
        "'s": " is",
        "'d": " would",
        "'ll": " will",
        "'ve": " have",
        "'m": " am",
    }
    for contraction, expansion in contractions.items():
        text = text.replace(contraction, expansion)

    # Keep only letters, numbers, basic punctuation, and spaces
    text = re.sub(r"[^a-z0-9\s.,!?']", " ", text)

    # Normalize whitespace
    text = re.sub(r'\s+', ' ', text)

    return text.strip()


def tokenize(text):
    """Tokenize text into words."""
    # Split on whitespace and punctuation
    tokens = re.findall(r"[a-z]+|[.,!?]", text.lower())
    return tokens


def load_text_file(filepath):
    """Load text from a .txt file."""
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        return f.read()


def load_csv_file(filepath, text_column=None):
    """Load text from a CSV file."""
    import csv
    texts = []
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if text_column and text_column in row:
                texts.append(row[text_column])
            else:
                # Try common column names
                for col in ['text', 'content', 'message', 'body', 'comment']:
                    if col in row:
                        texts.append(row[col])
                        break
    return '\n'.join(texts)


def load_json_file(filepath, text_field=None):
    """Load text from a JSON/JSONL file."""
    texts = []
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            try:
                data = json.loads(line)
                if isinstance(data, dict):
                    if text_field and text_field in data:
                        texts.append(data[text_field])
                    else:
                        for field in ['text', 'content', 'body', 'message']:
                            if field in data:
                                texts.append(data[field])
                                break
                elif isinstance(data, str):
                    texts.append(data)
            except json.JSONDecodeError:
                continue
    return '\n'.join(texts)


def load_data(input_path, text_column=None):
    """Load data from file or directory."""
    input_path = Path(input_path)
    all_text = []

    if input_path.is_file():
        files = [input_path]
    else:
        files = list(input_path.glob('**/*'))

    for filepath in files:
        if filepath.suffix == '.txt':
            all_text.append(load_text_file(filepath))
        elif filepath.suffix == '.csv':
            all_text.append(load_csv_file(filepath, text_column))
        elif filepath.suffix in ['.json', '.jsonl']:
            all_text.append(load_json_file(filepath, text_column))

    return '\n'.join(all_text)


def build_vocabulary(tokens, vocab_size, min_freq=MIN_WORD_FREQ):
    """Build vocabulary from tokens."""
    # Count word frequencies
    word_counts = Counter(tokens)

    # Filter by minimum frequency
    filtered_counts = {w: c for w, c in word_counts.items() if c >= min_freq}

    # Sort by frequency and take top N
    sorted_words = sorted(filtered_counts.items(), key=lambda x: -x[1])
    top_words = sorted_words[:vocab_size - 2]  # Reserve 2 for special tokens

    # Build word-to-index mapping
    # 0 = <PAD>, 1 = <UNK>
    word_to_idx = {'<PAD>': 0, '<UNK>': 1}
    for idx, (word, _) in enumerate(top_words, start=2):
        word_to_idx[word] = idx

    idx_to_word = {idx: word for word, idx in word_to_idx.items()}

    print(f"Vocabulary size: {len(word_to_idx)}")
    print(f"Top 20 words: {[w for w, _ in top_words[:20]]}")

    return word_to_idx, idx_to_word, dict(top_words)


def create_sequences(tokens, word_to_idx, sequence_length):
    """Create training sequences from tokens."""
    sequences = []
    unk_idx = word_to_idx['<UNK>']

    # Convert tokens to indices
    indices = [word_to_idx.get(token, unk_idx) for token in tokens]

    # Create sequences
    for i in range(len(indices) - sequence_length):
        seq = indices[i:i + sequence_length + 1]
        sequences.append(seq)

    print(f"Created {len(sequences)} sequences")
    return sequences


def save_processed_data(output_dir, word_to_idx, idx_to_word, sequences, word_freqs):
    """Save processed data."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Save vocabulary as JSON (for native engine)
    vocab_file = output_dir / 'en_word_indices.json'
    with open(vocab_file, 'w', encoding='utf-8') as f:
        json.dump(word_to_idx, f, ensure_ascii=False, indent=2)
    print(f"Saved vocabulary to {vocab_file}")

    # Save reverse mapping
    idx_file = output_dir / 'en_idx_to_word.json'
    with open(idx_file, 'w', encoding='utf-8') as f:
        json.dump({str(k): v for k, v in idx_to_word.items()}, f, ensure_ascii=False, indent=2)
    print(f"Saved index mapping to {idx_file}")

    # Save sequences as pickle (for training)
    seq_file = output_dir / 'en_sequences.pkl'
    with open(seq_file, 'wb') as f:
        pickle.dump(sequences, f)
    print(f"Saved sequences to {seq_file}")

    # Save word frequencies
    freq_file = output_dir / 'en_word_freqs.json'
    with open(freq_file, 'w', encoding='utf-8') as f:
        json.dump(word_freqs, f, ensure_ascii=False, indent=2)
    print(f"Saved word frequencies to {freq_file}")


def main():
    parser = argparse.ArgumentParser(description='Prepare English dataset for LSTM training')
    parser.add_argument('--input', '-i', required=True, help='Input file or directory')
    parser.add_argument('--output', '-o', default='processed', help='Output directory')
    parser.add_argument('--vocab_size', '-v', type=int, default=DEFAULT_VOCAB_SIZE,
                        help=f'Vocabulary size (default: {DEFAULT_VOCAB_SIZE})')
    parser.add_argument('--seq_length', '-s', type=int, default=DEFAULT_SEQUENCE_LENGTH,
                        help=f'Sequence length (default: {DEFAULT_SEQUENCE_LENGTH})')
    parser.add_argument('--text_column', '-c', help='Column name for CSV/JSON files')
    parser.add_argument('--min_freq', '-m', type=int, default=MIN_WORD_FREQ,
                        help=f'Minimum word frequency (default: {MIN_WORD_FREQ})')

    args = parser.parse_args()

    print(f"Loading data from {args.input}...")
    raw_text = load_data(args.input, args.text_column)
    print(f"Loaded {len(raw_text):,} characters")

    print("Cleaning text...")
    cleaned_text = clean_text(raw_text)

    print("Tokenizing...")
    tokens = tokenize(cleaned_text)
    print(f"Found {len(tokens):,} tokens")

    print(f"Building vocabulary (top {args.vocab_size} words)...")
    word_to_idx, idx_to_word, word_freqs = build_vocabulary(
        tokens, args.vocab_size, args.min_freq
    )

    print(f"Creating sequences (length {args.seq_length})...")
    sequences = create_sequences(tokens, word_to_idx, args.seq_length)

    print(f"Saving to {args.output}...")
    save_processed_data(args.output, word_to_idx, idx_to_word, sequences, word_freqs)

    print("Done!")


if __name__ == '__main__':
    main()
