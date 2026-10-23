cmake_minimum_required(VERSION 3.10)
project(lafz_whisper)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ggml builds one CPU backend per instruction-set level (dot product, i8mm, SVE, ...) as separate libraries and the right one is loaded at run time,
# so a phone without e.g. SVE never executes SVE code. The Kotlin side calls ggml_backend_load_all_from_path(nativeLibraryDir) before loading a model.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR}/ggml ggml)

find_library(LOG_LIB log)

add_library(lafz_whisper SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    ${WHISPER_DIR}/src/aneforge/whisper-aneforge.cpp
    lafz_whisper_jni.cpp)
target_compile_definitions(lafz_whisper PRIVATE WHISPER_VERSION="1.9.5")
target_include_directories(lafz_whisper PRIVATE ${WHISPER_DIR}/include ${WHISPER_DIR}/src ${WHISPER_DIR}/ggml/include)
target_compile_options(lafz_whisper PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(lafz_whisper PRIVATE -Wl,--gc-sections)
target_link_libraries(lafz_whisper ${LOG_LIB} ggml)
