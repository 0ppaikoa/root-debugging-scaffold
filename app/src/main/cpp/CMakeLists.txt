cmake_minimum_required(VERSION 3.22.1)
project(rootdebug)

include(FetchContent)

# Dobby is an external dependency. It is fetched at build time into the
# build directory and is never copied into this source tree.
set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    dobby
    GIT_REPOSITORY https://github.com/LSPosed/Dobby
    GIT_TAG c1da0315d7a2069bde2ab432e45d4ab6df91237a
)
FetchContent_MakeAvailable(dobby)

add_library(rootdebug SHARED rootdebug_native.c)
target_include_directories(rootdebug PRIVATE ${dobby_SOURCE_DIR}/include)
target_link_libraries(rootdebug dobby log dl)
