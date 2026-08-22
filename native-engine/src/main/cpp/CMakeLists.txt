# Chotobela native engine
cmake_minimum_required(VERSION 3.22.1)
project(chotobela_engine C)

set(CMAKE_C_STANDARD 17)

add_library(chotobela_engine SHARED
        jni/chotobela_jni.c
        engine-host/engine_host.c
        cores/chip8/chip8.c
        renderer/renderer_backend.c
        audio/audio_backend.c
        input/input_state.c
)

target_include_directories(chotobela_engine PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/engine-host
        ${CMAKE_CURRENT_SOURCE_DIR}/cores
        ${CMAKE_CURRENT_SOURCE_DIR}/renderer
        ${CMAKE_CURRENT_SOURCE_DIR}/audio
        ${CMAKE_CURRENT_SOURCE_DIR}/input
)

find_library(log-lib log)
find_library(aaudio-lib aaudio)
target_link_libraries(chotobela_engine ${log-lib} ${aaudio-lib})
