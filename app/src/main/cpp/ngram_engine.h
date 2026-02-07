#ifndef NGRAM_ENGINE_H
#define NGRAM_ENGINE_H

#include <string>
#include <vector>
#include <unordered_map>
#include <cstdint>

namespace ngram {

struct WordEntry {
    std::string word;
    uint16_t frequency;  // Log-scaled frequency (0-10000)
};

struct BigramEntry {
    uint16_t word1_idx;
    uint16_t word2_idx;
    uint16_t frequency;  // Log-scaled frequency (0-10000)
};

struct Suggestion {
    std::string word;
    int score;
};

class NgramEngine {
public:
    NgramEngine();
    ~NgramEngine();

    // Load vocabulary from binary data
    bool loadVocabulary(const uint8_t* data, size_t size);

    // Load bigrams from binary data
    bool loadBigrams(const uint8_t* data, size_t size);

    // Check if engine is ready
    bool isReady() const { return vocabularyLoaded_ && bigramsLoaded_; }

    // Get next word predictions based on previous word
    std::vector<Suggestion> predict(const std::string& prevWord, int topK = 5);

    // Get word completions (prefix matching)
    std::vector<Suggestion> complete(const std::string& prefix, int topK = 5);

    // Get combined predictions (bigram + completion)
    std::vector<Suggestion> getSuggestions(const std::string& text, int topK = 5);

    // Get vocabulary size
    size_t getVocabSize() const { return vocabulary_.size(); }

    // Get bigram count
    size_t getBigramCount() const { return bigrams_.size(); }

private:
    static constexpr uint32_t MAGIC = 0x4E47524D;  // "NGRM"
    static constexpr uint32_t VERSION = 1;

    std::vector<WordEntry> vocabulary_;
    std::unordered_map<std::string, uint16_t> wordToIndex_;

    // Bigrams indexed by first word for fast lookup
    std::unordered_map<uint16_t, std::vector<std::pair<uint16_t, uint16_t>>> bigramsByFirst_;
    std::vector<BigramEntry> bigrams_;

    bool vocabularyLoaded_ = false;
    bool bigramsLoaded_ = false;

    // Extract last word from text
    std::string extractLastWord(const std::string& text);

    // Extract current (incomplete) word being typed
    std::string extractCurrentWord(const std::string& text);
};

}  // namespace ngram

#endif  // NGRAM_ENGINE_H
