#!/usr/bin/env python3
"""
Download sample datasets for English LSTM training.

Usage:
    python download_data.py --dataset brown
    python download_data.py --dataset wikitext

Datasets:
    brown     - NLTK Brown corpus (~1M words) - Quick training
    wikitext  - Wikitext-2 (~2M words) - Better quality
"""

import argparse
import os
import sys
from pathlib import Path


def download_brown():
    """Download and save Brown corpus from NLTK."""
    try:
        import nltk
    except ImportError:
        print("NLTK not installed. Installing...")
        os.system(f"{sys.executable} -m pip install nltk")
        import nltk

    print("Downloading Brown corpus...")
    nltk.download('brown', quiet=True)

    from nltk.corpus import brown
    text = ' '.join(brown.words())

    output_file = Path('brown_corpus.txt')
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(text)

    print(f"Saved: {output_file} ({len(text):,} characters, ~{len(text.split()):,} words)")
    return str(output_file)


def download_wikitext():
    """Download Wikitext-2 dataset."""
    import urllib.request

    url = "https://raw.githubusercontent.com/pytorch/examples/main/word_language_model/data/wikitext-2/train.txt"
    output_file = Path('wikitext2_train.txt')

    print("Downloading Wikitext-2 train set...")
    try:
        urllib.request.urlretrieve(url, output_file)
    except Exception as e:
        print(f"Failed to download from {url}")
        print(f"Error: {e}")
        print("\nAlternative: Download manually from:")
        print("  https://huggingface.co/datasets/wikitext/tree/main/wikitext-2-v1")
        return None

    # Count words
    with open(output_file, 'r', encoding='utf-8') as f:
        text = f.read()

    print(f"Saved: {output_file} ({len(text):,} characters, ~{len(text.split()):,} words)")
    return str(output_file)


def main():
    parser = argparse.ArgumentParser(description='Download training data for English LSTM')
    parser.add_argument('--dataset', '-d', default='brown',
                        choices=['brown', 'wikitext'],
                        help='Dataset to download (default: brown)')

    args = parser.parse_args()

    if args.dataset == 'brown':
        output_file = download_brown()
    elif args.dataset == 'wikitext':
        output_file = download_wikitext()

    if output_file:
        print("\nNext steps:")
        print(f"  python prepare_dataset.py --input {output_file} --output processed/ --vocab_size 20000")
        print(f"  python train_lstm.py --input processed/ --output models/ --epochs 20")
        print(f"  python export_to_native.py --model models/en_lstm_best.keras --output output/")


if __name__ == '__main__':
    main()
