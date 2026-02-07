# English LSTM Training

Train an LSTM model for English next-word prediction, matching the Myanmar LSTM architecture for native engine compatibility.

## Architecture

- **Embedding**: vocab_size x 256
- **LSTM**: 256 units
- **Dense**: vocab_size (softmax)
- **Sequence Length**: 5 words

## Requirements

```bash
pip install tensorflow numpy
```

## Datasets

### Recommended Datasets

1. **Wikitext-103** (500MB, 103M tokens) - Best for general English
   ```bash
   # Download from Hugging Face
   wget https://huggingface.co/datasets/wikitext/resolve/main/wikitext-103-raw-v1/wiki.train.raw
   ```

2. **1 Billion Word Benchmark** (1.2GB) - Large, diverse corpus
   ```bash
   # Download from Google
   wget https://huggingface.co/datasets/billsum/resolve/main/billsum-train.jsonl
   # Or use the prepared version
   wget http://www.statmt.org/lm-benchmark/1-billion-word-language-modeling-benchmark-r13output.tar.gz
   ```

3. **NLTK Brown Corpus** (~1M words) - Quick training, smaller vocab
   ```python
   import nltk
   nltk.download('brown')
   from nltk.corpus import brown
   text = ' '.join(brown.words())
   with open('brown_corpus.txt', 'w') as f:
       f.write(text)
   ```

4. **Blog Authorship Corpus** - Conversational English
   ```bash
   # Available from: https://www.kaggle.com/datasets/rtatman/blog-authorship-corpus
   ```

5. **Reddit Comments** - Informal conversational text
   ```bash
   # Pushshift archives: https://files.pushshift.io/reddit/comments/
   ```

### Custom Text

You can also use any plain text file (.txt), CSV with a text column, or JSONL with a text field.

## Training Pipeline

### 1. Prepare Dataset

```bash
cd scripts/english_lstm/

# For text file
python prepare_dataset.py --input wiki.train.raw --output processed/ --vocab_size 20000

# For CSV
python prepare_dataset.py --input data.csv --output processed/ --text_column content

# For JSONL
python prepare_dataset.py --input data.jsonl --output processed/ --text_column text
```

Output files:
- `processed/en_word_indices.json` - Word to index mapping
- `processed/en_idx_to_word.json` - Index to word mapping
- `processed/en_sequences.pkl` - Training sequences
- `processed/en_word_freqs.json` - Word frequencies

### 2. Train Model

```bash
python train_lstm.py --input processed/ --output models/ --epochs 20

# With custom parameters
python train_lstm.py \
    --input processed/ \
    --output models/ \
    --epochs 30 \
    --batch_size 256 \
    --embedding_dim 256 \
    --lstm_units 256
```

Output files:
- `models/en_lstm_best.keras` - Best model by validation accuracy
- `models/en_lstm_final.keras` - Final model after training
- `models/training_history.json` - Training metrics

### 3. Export to Native Format

```bash
python export_to_native.py --model models/en_lstm_best.keras --output output/

# Files are created in output/
ls output/
# en_lstm_model.bin
```

### 4. Copy to App Assets

```bash
# Copy model and vocabulary to Android assets
cp output/en_lstm_model.bin ../../app/src/main/assets/
cp processed/en_word_indices.json ../../app/src/main/assets/
```

## Quick Start with Brown Corpus

For quick testing with a smaller dataset:

```bash
cd scripts/english_lstm/

# Download Brown corpus
python -c "
import nltk
nltk.download('brown')
from nltk.corpus import brown
with open('brown_corpus.txt', 'w') as f:
    f.write(' '.join(brown.words()))
print('Saved brown_corpus.txt')
"

# Prepare (smaller vocab for testing)
python prepare_dataset.py --input brown_corpus.txt --output processed/ --vocab_size 10000

# Train (fewer epochs for testing)
python train_lstm.py --input processed/ --output models/ --epochs 10

# Export
python export_to_native.py --model models/en_lstm_best.keras --output output/

# Copy to assets
cp output/en_lstm_model.bin ../../app/src/main/assets/
cp processed/en_word_indices.json ../../app/src/main/assets/
```

## Model Size Estimation

With 20K vocabulary:
- **Embedding**: 20,000 x 256 x 4 bytes = ~20 MB
- **LSTM**: ~3 MB
- **Dense**: ~20 MB
- **Total**: ~43 MB

To reduce size, use smaller vocabulary (10K = ~22 MB, 5K = ~12 MB).

## Integration with App

After copying the files to assets, the `LstmSuggestionEngine` in the app will automatically load the English model when:
1. `en_lstm_model.bin` exists in assets
2. `en_word_indices.json` exists in assets

The native LSTM engine handles both Myanmar and English models with the same binary format.

## Tips for Better Results

1. **More data = better predictions**: Use larger datasets like Wikitext-103
2. **Domain-specific training**: For chat/SMS style, use conversational datasets
3. **Minimum frequency**: Increase `--min_freq` to reduce noise from rare words
4. **Epochs**: Start with 20, increase if validation accuracy keeps improving
5. **Early stopping**: Training automatically stops if no improvement for 5 epochs
