/*
 * Lightweight LSTM inference engine for Myanmar syllable prediction.
 * Custom implementation without TFLite dependency.
 */
#ifndef LSTM_ENGINE_H_
#define LSTM_ENGINE_H_

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque handle to LSTM engine
typedef struct lstm_engine lstm_engine;

// Create a new LSTM engine instance
lstm_engine* lstm_engine_create(void);

// Destroy the engine and free resources
void lstm_engine_destroy(lstm_engine* engine);

// Load model weights from memory buffer
// Expected format: Custom binary format with embedding, LSTM, and dense weights
// Returns 1 on success, 0 on failure
int lstm_engine_load_model(lstm_engine* engine, const uint8_t* model_data, size_t model_size);

// Load vocabulary from JSON string
// JSON format: {"syllable": index, ...}
// Returns 1 on success, 0 on failure
int lstm_engine_load_vocab(lstm_engine* engine, const char* json_str);

// Predict next syllable probabilities given input indices
// input_indices: array of syllable indices (last N syllables, 0-padded)
// input_count: number of indices provided (max = sequence_length)
// output_probs: output array for probabilities (must be vocab_size elements)
// Returns vocab_size on success, 0 on failure
int lstm_engine_predict(lstm_engine* engine,
                        const int32_t* input_indices,
                        int input_count,
                        float* output_probs);

// Get vocabulary size
int lstm_engine_get_vocab_size(lstm_engine* engine);

// Get syllable for index (returns pointer to internal string, do not free)
const char* lstm_engine_get_syllable(lstm_engine* engine, int index);

// Get index for syllable (returns -1 if not found)
int lstm_engine_get_index(lstm_engine* engine, const char* syllable);

// Get sequence length expected by the model
int lstm_engine_get_sequence_length(lstm_engine* engine);

#ifdef __cplusplus
}
#endif

#endif  // LSTM_ENGINE_H_
