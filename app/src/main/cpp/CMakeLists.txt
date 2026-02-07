cmake_minimum_required(VERSION 3.22.1)
project(myanmar_keyboard_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Myanmar Trie library
add_library(myanmar_trie SHARED
    trie.cpp
    trie_c_api.cpp
    trie_jni.cpp
)
target_link_options(myanmar_trie PRIVATE "-Wl,-z,max-page-size=16384")

# Myanmar LSTM library (uses embedded TFLite-compatible inference)
add_library(myanmar_lstm SHARED
    lstm_engine.cpp
    lstm_jni.cpp
)
target_link_options(myanmar_lstm PRIVATE "-Wl,-z,max-page-size=16384")

# Find and link Android log library
find_library(log-lib log)
target_link_libraries(myanmar_trie ${log-lib})
target_link_libraries(myanmar_lstm ${log-lib})
