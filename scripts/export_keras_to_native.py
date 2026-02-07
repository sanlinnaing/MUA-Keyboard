#!/usr/bin/env python3
"""
Export trained Keras LSTM model directly to native binary format.

This bypasses TFLite which fuses LSTM weights internally.

Usage:
    python export_keras_to_native.py model.keras syll_predict_model.bin

The Keras model must have this architecture:
- layers.Embedding(vocab_size, 256, name='embedding')
- layers.LSTM(256, name='lstm')
- layers.Dense(vocab_size, name='dense')
"""

import sys
import struct
import os
import numpy as np

try:
    import tensorflow as tf
    from tensorflow import keras
except ImportError:
    print("Error: TensorFlow required. pip install tensorflow")
    sys.exit(1)

MAGIC = 0x4C53544D  # "LSTM"
VERSION = 1
EMBEDDING_DIM = 256
HIDDEN_SIZE = 256
SEQUENCE_LENGTH = 5


def export_model(keras_path, output_path):
    print(f"Loading Keras model: {keras_path}")

    model = keras.models.load_model(keras_path)
    model.summary()

    # Find layers by name or type
    embedding_layer = None
    lstm_layer = None
    dense_layer = None

    for layer in model.layers:
        if 'embedding' in layer.name.lower():
            embedding_layer = layer
        elif 'lstm' in layer.name.lower():
            lstm_layer = layer
        elif 'dense' in layer.name.lower():
            dense_layer = layer

    if embedding_layer is None:
        # Try to find by type
        for layer in model.layers:
            if isinstance(layer, keras.layers.Embedding):
                embedding_layer = layer
                break

    if lstm_layer is None:
        for layer in model.layers:
            if isinstance(layer, keras.layers.LSTM):
                lstm_layer = layer
                break

    if dense_layer is None:
        for layer in model.layers:
            if isinstance(layer, keras.layers.Dense):
                dense_layer = layer
                break

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

    # Validate shapes
    assert embedding_weights.shape == (vocab_size, EMBEDDING_DIM), \
        f"Embedding shape mismatch: expected ({vocab_size}, {EMBEDDING_DIM}), got {embedding_weights.shape}"
    assert lstm_kernel.shape == (EMBEDDING_DIM, 4 * HIDDEN_SIZE), \
        f"LSTM kernel shape mismatch: expected ({EMBEDDING_DIM}, {4 * HIDDEN_SIZE}), got {lstm_kernel.shape}"
    assert lstm_recurrent.shape == (HIDDEN_SIZE, 4 * HIDDEN_SIZE), \
        f"LSTM recurrent shape mismatch: expected ({HIDDEN_SIZE}, {4 * HIDDEN_SIZE}), got {lstm_recurrent.shape}"
    assert lstm_bias.shape == (4 * HIDDEN_SIZE,), \
        f"LSTM bias shape mismatch: expected ({4 * HIDDEN_SIZE},), got {lstm_bias.shape}"
    assert dense_kernel.shape == (HIDDEN_SIZE, vocab_size), \
        f"Dense kernel shape mismatch: expected ({HIDDEN_SIZE}, {vocab_size}), got {dense_kernel.shape}"
    assert dense_bias.shape == (vocab_size,), \
        f"Dense bias shape mismatch: expected ({vocab_size},), got {dense_bias.shape}"

    print(f"\nVocab size: {vocab_size}")

    # Write binary format
    print(f"\nWriting: {output_path}")
    with open(output_path, 'wb') as f:
        # Header
        f.write(struct.pack('<I', MAGIC))
        f.write(struct.pack('<I', VERSION))
        f.write(struct.pack('<I', vocab_size))
        f.write(struct.pack('<I', EMBEDDING_DIM))
        f.write(struct.pack('<I', HIDDEN_SIZE))
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

    size = os.path.getsize(output_path)
    print(f"Done! Size: {size / 1024 / 1024:.2f} MB")

    # Verify file size
    expected_size = 24 + \
        vocab_size * EMBEDDING_DIM * 4 + \
        4 * HIDDEN_SIZE * EMBEDDING_DIM * 4 + \
        4 * HIDDEN_SIZE * HIDDEN_SIZE * 4 + \
        4 * HIDDEN_SIZE * 4 + \
        vocab_size * HIDDEN_SIZE * 4 + \
        vocab_size * 4

    print(f"Expected size: {expected_size} bytes")
    print(f"Actual size: {size} bytes")
    if expected_size == size:
        print("✓ Size matches!")
    else:
        print("✗ Size mismatch!")
        return False

    return True


if __name__ == '__main__':
    if len(sys.argv) < 3:
        # Default paths
        keras_path = 'model.keras'
        output_path = 'app/src/main/assets/syll_predict_model.bin'

        # Check for common locations
        if os.path.exists('best_model.keras'):
            keras_path = 'best_model.keras'
        elif os.path.exists('final_model.keras'):
            keras_path = 'final_model.keras'

        print(f"Usage: {sys.argv[0]} <model.keras> <output.bin>")
        print(f"Using defaults: {keras_path} -> {output_path}")
    else:
        keras_path = sys.argv[1]
        output_path = sys.argv[2]

    if not os.path.exists(keras_path):
        print(f"ERROR: Model file not found: {keras_path}")
        print("\nPlease provide the trained Keras model file (.keras or .h5)")
        print("This should be the model BEFORE converting to TFLite.")
        sys.exit(1)

    export_model(keras_path, output_path)
