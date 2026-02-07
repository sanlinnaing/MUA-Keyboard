#!/usr/bin/env python3
"""
Export trained English LSTM model to native binary format.

This converts the Keras model to the custom binary format that
the native LSTM engine can load.

Usage:
    python export_to_native.py --model models/en_lstm_best.keras --output assets/

The output files will be:
- en_lstm_model.bin - LSTM weights in native binary format
- en_word_indices.json - Vocabulary mapping (already exists from prepare_dataset.py)

Architecture must match:
- Embedding: vocab_size x 256
- LSTM: 256 units
- Dense: vocab_size (softmax)
"""

import argparse
import json
import os
import struct
import sys
from pathlib import Path

import numpy as np

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

try:
    import tensorflow as tf
    from tensorflow import keras
except ImportError:
    print("Error: TensorFlow required. pip install tensorflow")
    sys.exit(1)

# Model constants (must match native engine)
MAGIC = 0x4C53544D  # "LSTM"
VERSION = 1
EMBEDDING_DIM = 256
HIDDEN_SIZE = 256
SEQUENCE_LENGTH = 5


def export_model(keras_path, output_dir, vocab_path=None):
    """Export Keras model to native binary format."""
    print(f"Loading Keras model: {keras_path}")

    model = keras.models.load_model(keras_path)
    model.summary()

    # Find layers by name or type
    embedding_layer = None
    lstm_layer = None
    dense_layer = None

    for layer in model.layers:
        if isinstance(layer, keras.layers.Embedding):
            embedding_layer = layer
        elif isinstance(layer, keras.layers.LSTM):
            lstm_layer = layer
        elif isinstance(layer, keras.layers.Dense):
            dense_layer = layer

    if embedding_layer is None:
        print("ERROR: Could not find embedding layer")
        return False

    if lstm_layer is None:
        print("ERROR: Could not find LSTM layer")
        return False

    if dense_layer is None:
        print("ERROR: Could not find dense layer")
        return False

    print(f"\nFound layers:")
    print(f"  Embedding: {embedding_layer.name}")
    print(f"  LSTM: {lstm_layer.name}")
    print(f"  Dense: {dense_layer.name}")

    # Extract weights
    embedding_weights = embedding_layer.get_weights()[0]  # [vocab, embed]
    vocab_size = embedding_weights.shape[0]

    lstm_weights = lstm_layer.get_weights()
    # LSTM weights: [kernel, recurrent_kernel, bias]
    lstm_kernel = lstm_weights[0]      # [embed, 4*hidden]
    lstm_recurrent = lstm_weights[1]   # [hidden, 4*hidden]
    lstm_bias = lstm_weights[2]        # [4*hidden]

    dense_weights = dense_layer.get_weights()
    dense_kernel = dense_weights[0]    # [hidden, vocab]
    dense_bias = dense_weights[1]      # [vocab]

    print(f"\nWeight shapes:")
    print(f"  Embedding: {embedding_weights.shape}")
    print(f"  LSTM kernel: {lstm_kernel.shape}")
    print(f"  LSTM recurrent: {lstm_recurrent.shape}")
    print(f"  LSTM bias: {lstm_bias.shape}")
    print(f"  Dense kernel: {dense_kernel.shape}")
    print(f"  Dense bias: {dense_bias.shape}")
    print(f"\nVocab size: {vocab_size}")

    # Validate shapes
    embed_dim = embedding_weights.shape[1]
    hidden_size = lstm_kernel.shape[1] // 4

    print(f"Detected: embedding_dim={embed_dim}, hidden_size={hidden_size}")

    if embed_dim != EMBEDDING_DIM:
        print(f"WARNING: Embedding dim {embed_dim} != expected {EMBEDDING_DIM}")
    if hidden_size != HIDDEN_SIZE:
        print(f"WARNING: Hidden size {hidden_size} != expected {HIDDEN_SIZE}")

    # Create output directory
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Write binary format
    model_path = output_dir / 'en_lstm_model.bin'
    print(f"\nWriting: {model_path}")

    with open(model_path, 'wb') as f:
        # Header
        f.write(struct.pack('<I', MAGIC))
        f.write(struct.pack('<I', VERSION))
        f.write(struct.pack('<I', vocab_size))
        f.write(struct.pack('<I', embed_dim))
        f.write(struct.pack('<I', hidden_size))
        f.write(struct.pack('<I', SEQUENCE_LENGTH))

        # Embedding [vocab, embed]
        f.write(embedding_weights.astype(np.float32).tobytes())

        # LSTM kernel - transpose to [4*hidden, embed] for native format
        f.write(lstm_kernel.T.astype(np.float32).tobytes())

        # LSTM recurrent - transpose to [4*hidden, hidden]
        f.write(lstm_recurrent.T.astype(np.float32).tobytes())

        # LSTM bias [4*hidden]
        f.write(lstm_bias.astype(np.float32).tobytes())

        # Dense weights - transpose to [vocab, hidden] for native format
        f.write(dense_kernel.T.astype(np.float32).tobytes())

        # Dense bias [vocab]
        f.write(dense_bias.astype(np.float32).tobytes())

    size = os.path.getsize(model_path)
    print(f"Model file size: {size / 1024 / 1024:.2f} MB")

    # Verify file size
    expected_size = 24 + \
        vocab_size * embed_dim * 4 + \
        4 * hidden_size * embed_dim * 4 + \
        4 * hidden_size * hidden_size * 4 + \
        4 * hidden_size * 4 + \
        vocab_size * hidden_size * 4 + \
        vocab_size * 4

    if expected_size == size:
        print("Size matches expected")
    else:
        print(f"WARNING: Size mismatch! Expected {expected_size}, got {size}")

    # Copy vocabulary file if provided
    if vocab_path:
        import shutil
        vocab_out = output_dir / 'en_word_indices.json'
        if Path(vocab_path).exists():
            shutil.copy(vocab_path, vocab_out)
            print(f"Copied vocabulary to: {vocab_out}")

    print("\nDone!")
    print(f"\nTo use in the app, copy to assets:")
    print(f"  cp {model_path} app/src/main/assets/")
    print(f"  cp processed/en_word_indices.json app/src/main/assets/")

    return True


def main():
    parser = argparse.ArgumentParser(description='Export English LSTM to native format')
    parser.add_argument('--model', '-m', default='models/en_lstm_best.keras',
                        help='Input Keras model path')
    parser.add_argument('--output', '-o', default='output',
                        help='Output directory')
    parser.add_argument('--vocab', '-v', default='processed/en_word_indices.json',
                        help='Vocabulary JSON file')

    args = parser.parse_args()

    if not os.path.exists(args.model):
        print(f"ERROR: Model file not found: {args.model}")
        print("\nPlease train the model first:")
        print("  python prepare_dataset.py --input your_data.txt --output processed/")
        print("  python train_lstm.py --input processed/ --output models/")
        sys.exit(1)

    export_model(args.model, args.output, args.vocab)


if __name__ == '__main__':
    main()
