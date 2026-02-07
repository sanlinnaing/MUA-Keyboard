/*
 * Lightweight LSTM inference engine for Myanmar syllable prediction.
 * Custom implementation without TFLite dependency.
 *
 * Model architecture:
 * - Embedding layer: vocab_size x embedding_dim
 * - LSTM layer: hidden_size units
 * - Dense layer: hidden_size → vocab_size
 * - Softmax output
 *
 * Binary weight format:
 * - Magic: 0x4C53544D ("LSTM")
 * - Version: uint32_t
 * - vocab_size: uint32_t
 * - embedding_dim: uint32_t
 * - hidden_size: uint32_t
 * - sequence_length: uint32_t
 * - Embedding weights: vocab_size * embedding_dim * sizeof(float)
 * - LSTM weights (kernel): 4 * hidden_size * embedding_dim * sizeof(float)
 * - LSTM weights (recurrent): 4 * hidden_size * hidden_size * sizeof(float)
 * - LSTM bias: 4 * hidden_size * sizeof(float)
 * - Dense weights: vocab_size * hidden_size * sizeof(float)
 * - Dense bias: vocab_size * sizeof(float)
 */

#include "lstm_engine.h"
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <string>
#include <vector>
#include <unordered_map>
#include <android/log.h>

#define LOG_TAG "LstmEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const uint32_t LSTM_MAGIC = 0x4C53544D;  // "LSTM"
static const uint32_t LSTM_VERSION = 1;

struct lstm_engine {
    // Model parameters
    int vocab_size;
    int embedding_dim;
    int hidden_size;
    int sequence_length;

    // Weights
    float* embedding_weights;      // [vocab_size, embedding_dim]
    float* lstm_kernel;           // [embedding_dim, 4 * hidden_size] (input weights)
    float* lstm_recurrent;        // [hidden_size, 4 * hidden_size] (recurrent weights)
    float* lstm_bias;             // [4 * hidden_size]
    float* dense_weights;         // [hidden_size, vocab_size]
    float* dense_bias;            // [vocab_size]

    // Working buffers (allocated once)
    float* h_state;              // [hidden_size]
    float* c_state;              // [hidden_size]
    float* gates;                // [4 * hidden_size]
    float* embedded;             // [embedding_dim]
    float* output_probs;         // [vocab_size]

    // Vocabulary
    std::unordered_map<std::string, int>* syll_to_idx;
    std::vector<std::string>* idx_to_syll;

    bool model_loaded;
    bool vocab_loaded;

    lstm_engine() :
        vocab_size(0), embedding_dim(0), hidden_size(0), sequence_length(5),
        embedding_weights(nullptr), lstm_kernel(nullptr), lstm_recurrent(nullptr),
        lstm_bias(nullptr), dense_weights(nullptr), dense_bias(nullptr),
        h_state(nullptr), c_state(nullptr), gates(nullptr), embedded(nullptr),
        output_probs(nullptr), syll_to_idx(nullptr), idx_to_syll(nullptr),
        model_loaded(false), vocab_loaded(false) {}

    ~lstm_engine() {
        free(embedding_weights);
        free(lstm_kernel);
        free(lstm_recurrent);
        free(lstm_bias);
        free(dense_weights);
        free(dense_bias);
        free(h_state);
        free(c_state);
        free(gates);
        free(embedded);
        free(output_probs);
        delete syll_to_idx;
        delete idx_to_syll;
    }
};

// Sigmoid activation
static inline float sigmoid(float x) {
    return 1.0f / (1.0f + expf(-x));
}

// Tanh activation (using standard library)
static inline float tanh_act(float x) {
    return tanhf(x);
}

// Matrix-vector multiplication: out = mat * vec
// mat: [rows, cols], vec: [cols], out: [rows]
static void matvec(const float* mat, const float* vec, float* out, int rows, int cols) {
    for (int i = 0; i < rows; i++) {
        float sum = 0.0f;
        for (int j = 0; j < cols; j++) {
            sum += mat[i * cols + j] * vec[j];
        }
        out[i] = sum;
    }
}

// Add vectors: out += vec
static void vec_add(float* out, const float* vec, int size) {
    for (int i = 0; i < size; i++) {
        out[i] += vec[i];
    }
}

// Softmax in-place
static void softmax(float* vec, int size) {
    float max_val = vec[0];
    for (int i = 1; i < size; i++) {
        if (vec[i] > max_val) max_val = vec[i];
    }

    float sum = 0.0f;
    for (int i = 0; i < size; i++) {
        vec[i] = expf(vec[i] - max_val);
        sum += vec[i];
    }

    for (int i = 0; i < size; i++) {
        vec[i] /= sum;
    }
}

// LSTM cell forward pass
// Input: x[embedding_dim], h_prev[hidden_size], c_prev[hidden_size]
// Output: h_next[hidden_size], c_next[hidden_size]
static void lstm_cell(
    lstm_engine* engine,
    const float* x,
    float* h, float* c,
    float* gates_buf) {

    int H = engine->hidden_size;
    int E = engine->embedding_dim;

    // Compute gates: gates = kernel * x + recurrent * h + bias
    // gates layout: [i, f, c_candidate, o] each of size H

    // Start with bias
    memcpy(gates_buf, engine->lstm_bias, 4 * H * sizeof(float));

    // Add kernel * x
    for (int i = 0; i < 4 * H; i++) {
        float sum = 0.0f;
        for (int j = 0; j < E; j++) {
            sum += engine->lstm_kernel[i * E + j] * x[j];
        }
        gates_buf[i] += sum;
    }

    // Add recurrent * h
    for (int i = 0; i < 4 * H; i++) {
        float sum = 0.0f;
        for (int j = 0; j < H; j++) {
            sum += engine->lstm_recurrent[i * H + j] * h[j];
        }
        gates_buf[i] += sum;
    }

    // Apply activations
    // i = sigmoid(gates[0:H])
    // f = sigmoid(gates[H:2H])
    // c_candidate = tanh(gates[2H:3H])
    // o = sigmoid(gates[3H:4H])
    float* gate_i = gates_buf;
    float* gate_f = gates_buf + H;
    float* gate_c = gates_buf + 2 * H;
    float* gate_o = gates_buf + 3 * H;

    for (int i = 0; i < H; i++) {
        gate_i[i] = sigmoid(gate_i[i]);
        gate_f[i] = sigmoid(gate_f[i]);
        gate_c[i] = tanh_act(gate_c[i]);
        gate_o[i] = sigmoid(gate_o[i]);
    }

    // Update cell state: c = f * c_prev + i * c_candidate
    for (int i = 0; i < H; i++) {
        c[i] = gate_f[i] * c[i] + gate_i[i] * gate_c[i];
    }

    // Update hidden state: h = o * tanh(c)
    for (int i = 0; i < H; i++) {
        h[i] = gate_o[i] * tanh_act(c[i]);
    }
}

extern "C" {

lstm_engine* lstm_engine_create(void) {
    return new lstm_engine();
}

void lstm_engine_destroy(lstm_engine* engine) {
    delete engine;
}

int lstm_engine_load_model(lstm_engine* engine, const uint8_t* model_data, size_t model_size) {
    if (!engine || !model_data || model_size < 24) {
        LOGE("Invalid parameters for load_model");
        return 0;
    }

    const uint8_t* ptr = model_data;

    // Read header
    uint32_t magic = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t version = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;

    if (magic != LSTM_MAGIC) {
        LOGE("Invalid magic: expected 0x%X, got 0x%X", LSTM_MAGIC, magic);
        return 0;
    }
    if (version != LSTM_VERSION) {
        LOGE("Unsupported version: %u", version);
        return 0;
    }

    uint32_t vocab_size = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t embedding_dim = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t hidden_size = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;
    uint32_t sequence_length = *reinterpret_cast<const uint32_t*>(ptr); ptr += 4;

    LOGI("Loading model: vocab=%u, embed=%u, hidden=%u, seq_len=%u",
         vocab_size, embedding_dim, hidden_size, sequence_length);

    // Calculate expected size
    size_t embedding_size = vocab_size * embedding_dim * sizeof(float);
    size_t kernel_size = 4 * hidden_size * embedding_dim * sizeof(float);
    size_t recurrent_size = 4 * hidden_size * hidden_size * sizeof(float);
    size_t lstm_bias_size = 4 * hidden_size * sizeof(float);
    size_t dense_weights_size = vocab_size * hidden_size * sizeof(float);
    size_t dense_bias_size = vocab_size * sizeof(float);
    size_t header_size = 24;
    size_t expected_size = header_size + embedding_size + kernel_size +
                          recurrent_size + lstm_bias_size +
                          dense_weights_size + dense_bias_size;

    if (model_size < expected_size) {
        LOGE("Model too small: expected %zu, got %zu", expected_size, model_size);
        return 0;
    }

    engine->vocab_size = vocab_size;
    engine->embedding_dim = embedding_dim;
    engine->hidden_size = hidden_size;
    engine->sequence_length = sequence_length;

    // Allocate and copy weights
    engine->embedding_weights = static_cast<float*>(malloc(embedding_size));
    engine->lstm_kernel = static_cast<float*>(malloc(kernel_size));
    engine->lstm_recurrent = static_cast<float*>(malloc(recurrent_size));
    engine->lstm_bias = static_cast<float*>(malloc(lstm_bias_size));
    engine->dense_weights = static_cast<float*>(malloc(dense_weights_size));
    engine->dense_bias = static_cast<float*>(malloc(dense_bias_size));

    // Allocate working buffers
    engine->h_state = static_cast<float*>(calloc(hidden_size, sizeof(float)));
    engine->c_state = static_cast<float*>(calloc(hidden_size, sizeof(float)));
    engine->gates = static_cast<float*>(malloc(4 * hidden_size * sizeof(float)));
    engine->embedded = static_cast<float*>(malloc(embedding_dim * sizeof(float)));
    engine->output_probs = static_cast<float*>(malloc(vocab_size * sizeof(float)));

    if (!engine->embedding_weights || !engine->lstm_kernel ||
        !engine->lstm_recurrent || !engine->lstm_bias ||
        !engine->dense_weights || !engine->dense_bias ||
        !engine->h_state || !engine->c_state ||
        !engine->gates || !engine->embedded || !engine->output_probs) {
        LOGE("Failed to allocate memory");
        return 0;
    }

    // Copy weights
    memcpy(engine->embedding_weights, ptr, embedding_size); ptr += embedding_size;
    memcpy(engine->lstm_kernel, ptr, kernel_size); ptr += kernel_size;
    memcpy(engine->lstm_recurrent, ptr, recurrent_size); ptr += recurrent_size;
    memcpy(engine->lstm_bias, ptr, lstm_bias_size); ptr += lstm_bias_size;
    memcpy(engine->dense_weights, ptr, dense_weights_size); ptr += dense_weights_size;
    memcpy(engine->dense_bias, ptr, dense_bias_size);

    engine->model_loaded = true;
    LOGI("Model loaded successfully");
    return 1;
}

int lstm_engine_load_vocab(lstm_engine* engine, const char* json_str) {
    if (!engine || !json_str) {
        LOGE("Invalid parameters for load_vocab");
        return 0;
    }

    // Simple JSON parsing for {"key": int, ...} format
    engine->syll_to_idx = new std::unordered_map<std::string, int>();
    engine->idx_to_syll = new std::vector<std::string>();

    const char* p = json_str;

    // Find opening brace
    while (*p && *p != '{') p++;
    if (!*p) return 0;
    p++;

    int max_idx = -1;

    while (*p) {
        // Skip whitespace
        while (*p && (*p == ' ' || *p == '\n' || *p == '\r' || *p == '\t' || *p == ',')) p++;
        if (*p == '}') break;
        if (!*p) break;

        // Expect quote for key
        if (*p != '"') {
            p++;
            continue;
        }
        p++;

        // Read key (syllable)
        std::string key;
        while (*p && *p != '"') {
            if (*p == '\\' && *(p+1)) {
                p++;
                if (*p == 'u' && *(p+1) && *(p+2) && *(p+3) && *(p+4)) {
                    // Unicode escape \uXXXX
                    char hex[5] = {p[1], p[2], p[3], p[4], 0};
                    uint32_t codepoint = strtoul(hex, nullptr, 16);
                    // Convert codepoint to UTF-8
                    if (codepoint < 0x80) {
                        key += static_cast<char>(codepoint);
                    } else if (codepoint < 0x800) {
                        key += static_cast<char>(0xC0 | (codepoint >> 6));
                        key += static_cast<char>(0x80 | (codepoint & 0x3F));
                    } else {
                        key += static_cast<char>(0xE0 | (codepoint >> 12));
                        key += static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F));
                        key += static_cast<char>(0x80 | (codepoint & 0x3F));
                    }
                    p += 4;
                } else {
                    key += *p;
                }
            } else {
                key += *p;
            }
            p++;
        }
        if (*p == '"') p++;

        // Skip colon
        while (*p && *p != ':') p++;
        if (*p == ':') p++;

        // Skip whitespace
        while (*p && (*p == ' ' || *p == '\n' || *p == '\r' || *p == '\t')) p++;

        // Read value (integer)
        int value = 0;
        bool negative = false;
        if (*p == '-') {
            negative = true;
            p++;
        }
        while (*p && *p >= '0' && *p <= '9') {
            value = value * 10 + (*p - '0');
            p++;
        }
        if (negative) value = -value;

        (*engine->syll_to_idx)[key] = value;
        if (value > max_idx) max_idx = value;
    }

    // Build reverse mapping
    engine->idx_to_syll->resize(max_idx + 1);
    for (const auto& pair : *engine->syll_to_idx) {
        if (pair.second >= 0 && pair.second <= max_idx) {
            (*engine->idx_to_syll)[pair.second] = pair.first;
        }
    }

    engine->vocab_loaded = true;
    LOGI("Vocabulary loaded: %zu entries", engine->syll_to_idx->size());
    return 1;
}

int lstm_engine_predict(lstm_engine* engine,
                        const int32_t* input_indices,
                        int input_count,
                        float* output_probs) {
    if (!engine || !engine->model_loaded || !input_indices || !output_probs) {
        return 0;
    }

    int V = engine->vocab_size;
    int E = engine->embedding_dim;
    int H = engine->hidden_size;
    int seq_len = engine->sequence_length;

    // Reset LSTM states
    memset(engine->h_state, 0, H * sizeof(float));
    memset(engine->c_state, 0, H * sizeof(float));

    // Process sequence (with right-aligned zero padding)
    int padding = seq_len - input_count;
    for (int t = 0; t < seq_len; t++) {
        int idx;
        if (t < padding) {
            idx = 0;  // Zero padding
        } else {
            idx = input_indices[t - padding];
            if (idx < 0 || idx >= V) idx = 0;
        }

        // Look up embedding
        memcpy(engine->embedded,
               engine->embedding_weights + idx * E,
               E * sizeof(float));

        // LSTM step
        lstm_cell(engine, engine->embedded,
                  engine->h_state, engine->c_state,
                  engine->gates);
    }

    // Dense layer: output = h * dense_weights^T + dense_bias
    // dense_weights: [vocab_size, hidden_size] (row-major)
    for (int i = 0; i < V; i++) {
        float sum = engine->dense_bias[i];
        for (int j = 0; j < H; j++) {
            sum += engine->h_state[j] * engine->dense_weights[i * H + j];
        }
        output_probs[i] = sum;
    }

    // Softmax
    softmax(output_probs, V);

    return V;
}

int lstm_engine_get_vocab_size(lstm_engine* engine) {
    if (!engine || !engine->model_loaded) return 0;
    return engine->vocab_size;
}

const char* lstm_engine_get_syllable(lstm_engine* engine, int index) {
    if (!engine || !engine->vocab_loaded || !engine->idx_to_syll) return nullptr;
    if (index < 0 || index >= static_cast<int>(engine->idx_to_syll->size())) return nullptr;
    return (*engine->idx_to_syll)[index].c_str();
}

int lstm_engine_get_index(lstm_engine* engine, const char* syllable) {
    if (!engine || !engine->vocab_loaded || !engine->syll_to_idx || !syllable) return -1;
    auto it = engine->syll_to_idx->find(syllable);
    if (it == engine->syll_to_idx->end()) return -1;
    return it->second;
}

int lstm_engine_get_sequence_length(lstm_engine* engine) {
    if (!engine || !engine->model_loaded) return 5;
    return engine->sequence_length;
}

}  // extern "C"
