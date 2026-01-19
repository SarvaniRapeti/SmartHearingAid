cmake_minimum_required(VERSION 3.22.1)
project(nativeaudio C CXX)

# =========================
# RNNoise static library
# =========================
add_library(
        rnnoise
        STATIC

        rnnoise/external/rnnoise/src/celt_lpc.c
        rnnoise/external/rnnoise/src/denoise.c
        rnnoise/external/rnnoise/src/kiss_fft.c
        rnnoise/external/rnnoise/src/nnet.c
        rnnoise/external/rnnoise/src/nnet_default.c
        rnnoise/external/rnnoise/src/pitch.c
        rnnoise/external/rnnoise/src/rnn.c
        rnnoise/external/rnnoise/src/rnnoise_tables.c
        rnnoise/external/rnnoise/src/rnnoise_data.c
        rnnoise/external/rnnoise/src/parse_lpcnet_weights.c
)

target_include_directories(
        rnnoise
        PUBLIC
        rnnoise/external/rnnoise/include
        rnnoise/external/rnnoise/src
)

target_compile_definitions(
        rnnoise
        PRIVATE
        TRAINING=0
)

# =========================
# JNI wrapper
# =========================
add_library(
        nativeaudio
        SHARED
        native_rnnoise.cpp
)

target_link_libraries(
        nativeaudio
        rnnoise
        log
        m
        atomic
)
