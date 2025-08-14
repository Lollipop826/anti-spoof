cmake_minimum_required(VERSION 3.22)
project(antispoof_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(antispoof SHARED
    antispoof_jni.cpp
)

# Include our local headers (e.g., onnxruntime_c_api.h if provided)
target_include_directories(antispoof PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Link only system libs; we will load libonnxruntime.so at runtime (or link implicitly)
find_library(log-lib log)
find_library(android-lib android)

# dlopen for dynamic loading
find_library(dl-lib dl)

# Keep linkage minimal to avoid adding a second ORT copy
# Do NOT link onnxruntime here; APK already carries libonnxruntime.so via sherpa-onnx

target_link_libraries(antispoof
    ${log-lib}
    ${android-lib}
    ${dl-lib}
)

