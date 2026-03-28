cmake_minimum_required(VERSION 3.22.1)
project(llama-android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../external/llama.cpp)

# Check if llama.cpp exists, if not use a fallback path
if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../../external/llama.cpp)
endif()

# Core llama.cpp sources
set(GGML_SOURCES
    ${LLAMA_CPP_DIR}/ggml/src/ggml.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-alloc.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-backend.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-quants.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-aarch64.c
)

set(LLAMA_SOURCES
    ${LLAMA_CPP_DIR}/src/llama.cpp
    ${LLAMA_CPP_DIR}/src/llama-vocab.cpp
    ${LLAMA_CPP_DIR}/src/llama-grammar.cpp
    ${LLAMA_CPP_DIR}/src/llama-sampling.cpp
    ${LLAMA_CPP_DIR}/src/unicode.cpp
    ${LLAMA_CPP_DIR}/src/unicode-data.cpp
    ${LLAMA_CPP_DIR}/common/common.cpp
    ${LLAMA_CPP_DIR}/common/sampling.cpp
    ${LLAMA_CPP_DIR}/common/grammar-parser.cpp
    ${LLAMA_CPP_DIR}/common/json-schema-to-grammar.cpp
)

# Build the JNI bridge
add_library(llama-android SHARED
    src/main/cpp/llama-android.cpp
    ${GGML_SOURCES}
    ${LLAMA_SOURCES}
)

target_include_directories(llama-android PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/src
)

target_compile_definitions(llama-android PRIVATE
    GGML_USE_CPU
)

# ARM NEON optimizations
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(llama-android PRIVATE -mcpu=cortex-a76)
    target_compile_definitions(llama-android PRIVATE GGML_USE_CPU_HBM)
endif()

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

target_link_libraries(llama-android
    ${LOG_LIB}
    ${ANDROID_LIB}
)
