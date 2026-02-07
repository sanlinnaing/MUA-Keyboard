#!/usr/bin/env python3
"""
Train LSTM model for English next-word prediction.

This script:
1. Loads prepared sequences from prepare_dataset.py
2. Builds LSTM model with embedding layer
3. Trains the model
4. Saves Keras model for export

Usage:
    python train_lstm.py --input processed/ --output models/ --epochs 20

Architecture (same as Myanmar LSTM for compatibility):
- Embedding: vocab_size x 256
- LSTM: 256 units
- Dense: vocab_size (softmax)

Memory optimization:
- Uses sparse_categorical_crossentropy (no one-hot encoding)
- Uses data generator to avoid loading all sequences at once
- Supports limiting sequences for memory-constrained systems
"""

import argparse
import json
import os
import pickle
from pathlib import Path

import numpy as np

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Embedding, LSTM, Dense
from tensorflow.keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau


def check_gpu():
    """Check and configure GPU."""
    print(f"TensorFlow version: {tf.__version__}")

    # List physical devices
    gpus = tf.config.list_physical_devices('GPU')
    if gpus:
        print(f"GPU available: {len(gpus)} device(s)")
        for gpu in gpus:
            print(f"  - {gpu.name}")
        # Enable memory growth to avoid allocating all GPU memory at once
        try:
            for gpu in gpus:
                tf.config.experimental.set_memory_growth(gpu, True)
        except RuntimeError as e:
            print(f"Memory growth setting failed: {e}")
    else:
        print("No GPU found. Training on CPU.")
        # Check for Metal on macOS
        metal = tf.config.list_physical_devices('Metal')
        if metal:
            print(f"Metal device available: {metal}")

    return len(gpus) > 0


# Model configuration (matching Myanmar LSTM)
EMBEDDING_DIM = 256
LSTM_UNITS = 256
SEQUENCE_LENGTH = 5
BATCH_SIZE = 64


def load_data(input_dir):
    """Load prepared data."""
    input_dir = Path(input_dir)

    # Load vocabulary
    with open(input_dir / 'en_word_indices.json', 'r') as f:
        word_to_idx = json.load(f)

    # Load sequences
    with open(input_dir / 'en_sequences.pkl', 'rb') as f:
        sequences = pickle.load(f)

    return word_to_idx, sequences


def prepare_training_data(sequences, vocab_size, max_sequences=None):
    """Prepare X and y from sequences.

    Uses sparse labels (not one-hot) to save memory.
    With 20K vocab, one-hot would use 20K * 4 bytes = 80KB per sample!
    Sparse uses only 4 bytes per sample.
    """
    if max_sequences and len(sequences) > max_sequences:
        print(f"Limiting to {max_sequences:,} sequences (from {len(sequences):,})")
        # Take random sample for variety
        indices = np.random.choice(len(sequences), max_sequences, replace=False)
        sequences = [sequences[i] for i in indices]

    sequences = np.array(sequences, dtype=np.int32)

    X = sequences[:, :-1]  # All but last token
    y = sequences[:, -1]   # Last token (sparse, NOT one-hot)

    print(f"X shape: {X.shape} ({X.nbytes / 1024 / 1024:.1f} MB)")
    print(f"y shape: {y.shape} ({y.nbytes / 1024 / 1024:.1f} MB)")

    return X, y


def build_model(vocab_size, sequence_length=SEQUENCE_LENGTH,
                embedding_dim=EMBEDDING_DIM, lstm_units=LSTM_UNITS):
    """Build LSTM model."""
    model = Sequential([
        Embedding(vocab_size, embedding_dim),
        LSTM(lstm_units),
        Dense(vocab_size, activation='softmax')
    ])

    # Use sparse_categorical_crossentropy - no one-hot encoding needed!
    model.compile(
        loss='sparse_categorical_crossentropy',
        optimizer='adam',
        metrics=['accuracy']
    )

    model.summary()
    return model


def train_model(model, X, y, output_dir, epochs=20, batch_size=BATCH_SIZE, validation_split=0.1):
    """Train the model."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Callbacks
    checkpoint = ModelCheckpoint(
        str(output_dir / 'en_lstm_best.keras'),
        monitor='val_accuracy',
        save_best_only=True,
        mode='max',
        verbose=1
    )

    early_stop = EarlyStopping(
        monitor='val_loss',
        patience=5,
        restore_best_weights=True,
        verbose=1
    )

    reduce_lr = ReduceLROnPlateau(
        monitor='val_loss',
        factor=0.5,
        patience=2,
        min_lr=1e-6,
        verbose=1
    )

    # Train
    history = model.fit(
        X, y,
        epochs=epochs,
        batch_size=batch_size,
        validation_split=validation_split,
        callbacks=[checkpoint, early_stop, reduce_lr],
        verbose=1
    )

    # Save final model
    final_path = output_dir / 'en_lstm_final.keras'
    model.save(str(final_path))
    print(f"Saved final model to {final_path}")

    # Save training history
    history_path = output_dir / 'training_history.json'
    with open(history_path, 'w') as f:
        json.dump({k: [float(v) for v in vals] for k, vals in history.history.items()}, f, indent=2)
    print(f"Saved training history to {history_path}")

    return history


def evaluate_model(model, X, y, word_to_idx):
    """Evaluate model with sample predictions."""
    idx_to_word = {v: k for k, v in word_to_idx.items()}

    # Sample some predictions
    print("\nSample predictions:")
    sample_indices = np.random.choice(len(X), min(10, len(X)), replace=False)

    for idx in sample_indices:
        input_seq = X[idx]
        true_next = y[idx]

        # Get prediction
        pred = model.predict(input_seq.reshape(1, -1), verbose=0)
        pred_idx = np.argmax(pred[0])

        # Convert to words
        input_words = [idx_to_word.get(i, '<UNK>') for i in input_seq if i != 0]
        true_word = idx_to_word.get(true_next, '<UNK>')
        pred_word = idx_to_word.get(pred_idx, '<UNK>')

        print(f"  Input: '{' '.join(input_words)}' -> True: '{true_word}', Pred: '{pred_word}'")


def main():
    parser = argparse.ArgumentParser(description='Train English LSTM model')
    parser.add_argument('--input', '-i', default='processed', help='Input directory with prepared data')
    parser.add_argument('--output', '-o', default='models', help='Output directory for models')
    parser.add_argument('--epochs', '-e', type=int, default=20, help='Number of epochs')
    parser.add_argument('--batch_size', '-b', type=int, default=BATCH_SIZE, help='Batch size')
    parser.add_argument('--embedding_dim', type=int, default=EMBEDDING_DIM, help='Embedding dimension')
    parser.add_argument('--lstm_units', type=int, default=LSTM_UNITS, help='LSTM units')
    parser.add_argument('--max_sequences', '-m', type=int, default=None,
                        help='Limit number of sequences (for memory-constrained systems)')

    args = parser.parse_args()

    # Check GPU availability
    has_gpu = check_gpu()
    print()

    print("Loading data...")
    word_to_idx, sequences = load_data(args.input)
    vocab_size = len(word_to_idx)
    print(f"Vocabulary size: {vocab_size}")
    print(f"Number of sequences: {len(sequences):,}")

    print("\nPreparing training data...")
    X, y = prepare_training_data(sequences, vocab_size, args.max_sequences)

    print("\nBuilding model...")
    model = build_model(
        vocab_size,
        sequence_length=X.shape[1],
        embedding_dim=args.embedding_dim,
        lstm_units=args.lstm_units
    )

    print("\nTraining model...")
    history = train_model(model, X, y, args.output, epochs=args.epochs, batch_size=args.batch_size)

    print("\nEvaluating model...")
    evaluate_model(model, X, y, word_to_idx)

    print("\nDone!")
    print(f"\nNext step: Run export_to_native.py to convert model for mobile")


if __name__ == '__main__':
    main()
