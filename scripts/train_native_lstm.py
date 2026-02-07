#!/usr/bin/env python3
"""
Train a simple unidirectional LSTM model compatible with MUA Keyboard native engine.

Model architecture (must match native engine):
- Embedding: vocab_size → 256
- LSTM: 256 units (single layer, unidirectional)
- Dense: 256 → vocab_size (softmax)

Usage:
    python train_native_lstm.py --data_dir /path/to/data --output_dir /path/to/output

After training, use convert_to_native.py to export weights.
"""

import os
import json
import argparse
import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

# Model hyperparameters - MUST match native engine
EMBEDDING_DIM = 256
HIDDEN_SIZE = 256
SEQUENCE_LENGTH = 5


def create_model(vocab_size):
    """
    Create a simple LSTM model compatible with native engine.

    Architecture:
    - Embedding(vocab_size, 256)
    - LSTM(256)  # Single layer, unidirectional
    - Dense(vocab_size, softmax)
    """
    model = keras.Sequential([
        # Input: sequence of syllable indices
        layers.Input(shape=(SEQUENCE_LENGTH,), dtype=tf.int32, name='input'),

        # Embedding layer
        layers.Embedding(
            input_dim=vocab_size,
            output_dim=EMBEDDING_DIM,
            name='embedding'
        ),

        # Single LSTM layer (NOT bidirectional)
        layers.LSTM(
            units=HIDDEN_SIZE,
            return_sequences=False,  # Only output final state
            name='lstm'
        ),

        # Output layer
        layers.Dense(
            vocab_size,
            activation='softmax',
            name='dense'
        )
    ])

    return model


def export_to_native_format(model, vocab_size, output_path):
    """
    Export model weights to native binary format.

    Binary format:
    - Magic: 0x4C53544D ("LSTM")
    - Version: 1
    - vocab_size, embedding_dim, hidden_size, sequence_length (uint32 each)
    - Embedding weights: [vocab_size, embedding_dim]
    - LSTM kernel: [4*hidden_size, embedding_dim]
    - LSTM recurrent: [4*hidden_size, hidden_size]
    - LSTM bias: [4*hidden_size]
    - Dense weights: [vocab_size, hidden_size]
    - Dense bias: [vocab_size]
    """
    import struct

    # Get weights from layers
    embedding_weights = model.get_layer('embedding').get_weights()[0]  # [vocab, embed]
    lstm_weights = model.get_layer('lstm').get_weights()
    # LSTM weights: [kernel, recurrent_kernel, bias]
    # kernel shape: [embedding_dim, 4*hidden_size]
    # recurrent shape: [hidden_size, 4*hidden_size]
    # bias shape: [4*hidden_size]
    lstm_kernel = lstm_weights[0]      # [embed, 4*hidden]
    lstm_recurrent = lstm_weights[1]   # [hidden, 4*hidden]
    lstm_bias = lstm_weights[2]        # [4*hidden]

    dense_weights = model.get_layer('dense').get_weights()
    dense_kernel = dense_weights[0]    # [hidden, vocab]
    dense_bias = dense_weights[1]      # [vocab]

    print(f"Embedding: {embedding_weights.shape}")
    print(f"LSTM kernel: {lstm_kernel.shape}")
    print(f"LSTM recurrent: {lstm_recurrent.shape}")
    print(f"LSTM bias: {lstm_bias.shape}")
    print(f"Dense kernel: {dense_kernel.shape}")
    print(f"Dense bias: {dense_bias.shape}")

    with open(output_path, 'wb') as f:
        # Header
        f.write(struct.pack('<I', 0x4C53544D))  # Magic
        f.write(struct.pack('<I', 1))            # Version
        f.write(struct.pack('<I', vocab_size))
        f.write(struct.pack('<I', EMBEDDING_DIM))
        f.write(struct.pack('<I', HIDDEN_SIZE))
        f.write(struct.pack('<I', SEQUENCE_LENGTH))

        # Embedding weights [vocab_size, embedding_dim]
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

    print(f"Exported to {output_path} ({os.path.getsize(output_path) / 1024 / 1024:.2f} MB)")


def main():
    parser = argparse.ArgumentParser(description='Train native-compatible LSTM')
    parser.add_argument('--data_dir', type=str, required=True, help='Directory with X_train.npy, y_train.npy, etc.')
    parser.add_argument('--output_dir', type=str, default='./output', help='Output directory')
    parser.add_argument('--vocab_file', type=str, help='Path to syll_indices.json')
    parser.add_argument('--epochs', type=int, default=20)
    parser.add_argument('--batch_size', type=int, default=4096)
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)

    # Load vocabulary
    if args.vocab_file:
        with open(args.vocab_file) as f:
            vocab = json.load(f)
        vocab_size = len(vocab)
    else:
        vocab_file = os.path.join(args.data_dir, 'syll_indices.json')
        if os.path.exists(vocab_file):
            with open(vocab_file) as f:
                vocab = json.load(f)
            vocab_size = len(vocab)
        else:
            raise ValueError("Vocabulary file not found. Provide --vocab_file")

    print(f"Vocabulary size: {vocab_size}")

    # Load training data
    X_train = np.load(os.path.join(args.data_dir, 'X_train.npy'))
    y_train = np.load(os.path.join(args.data_dir, 'y_train.npy'))

    # Load validation data if exists
    val_data = None
    X_val_path = os.path.join(args.data_dir, 'X_val.npy')
    y_val_path = os.path.join(args.data_dir, 'y_val.npy')
    if os.path.exists(X_val_path) and os.path.exists(y_val_path):
        X_val = np.load(X_val_path)
        y_val = np.load(y_val_path)
        val_data = (X_val, y_val)
        print(f"Training samples: {len(X_train)}, Validation samples: {len(X_val)}")
    else:
        print(f"Training samples: {len(X_train)}")

    # Create model
    model = create_model(vocab_size)
    model.summary()

    # Compile
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )

    # Callbacks
    callbacks = [
        keras.callbacks.ModelCheckpoint(
            os.path.join(args.output_dir, 'best_model.keras'),
            monitor='val_accuracy' if val_data else 'accuracy',
            save_best_only=True,
            verbose=1
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor='val_loss' if val_data else 'loss',
            factor=0.5,
            patience=3,
            min_lr=1e-6,
            verbose=1
        ),
        keras.callbacks.EarlyStopping(
            monitor='val_loss' if val_data else 'loss',
            patience=5,
            restore_best_weights=True,
            verbose=1
        )
    ]

    # Train
    history = model.fit(
        X_train, y_train,
        validation_data=val_data,
        epochs=args.epochs,
        batch_size=args.batch_size,
        callbacks=callbacks
    )

    # Save final model
    model.save(os.path.join(args.output_dir, 'final_model.keras'))

    # Export to native format
    native_path = os.path.join(args.output_dir, 'syll_predict_model.bin')
    export_to_native_format(model, vocab_size, native_path)

    print(f"\nTraining complete!")
    print(f"Native model saved to: {native_path}")
    print(f"Copy this file to: app/src/main/assets/syll_predict_model.bin")


if __name__ == '__main__':
    main()
