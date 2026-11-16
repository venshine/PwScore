# C 核心与 C++ 包装。放在仓库根目录，便于用 FetchContent 或 add_subdirectory 直接引入：
#   target_link_libraries(app PRIVATE pwscore)       # C
#   target_link_libraries(app PRIVATE pwscore::cpp)  # C++17
cmake_minimum_required(VERSION 3.14)
project(pwscore VERSION 3.0.0 LANGUAGES C CXX)

add_library(pwscore STATIC c/pwscore.c)
target_include_directories(pwscore PUBLIC c)
set_target_properties(pwscore PROPERTIES C_STANDARD 99 C_STANDARD_REQUIRED ON)
add_library(pwscore::c ALIAS pwscore)

add_library(pwscore_cpp INTERFACE)
target_include_directories(pwscore_cpp INTERFACE cpp)
target_link_libraries(pwscore_cpp INTERFACE pwscore)
target_compile_features(pwscore_cpp INTERFACE cxx_std_17)
add_library(pwscore::cpp ALIAS pwscore_cpp)

if(PROJECT_IS_TOP_LEVEL)
  enable_testing()
  add_executable(test_pwscore_c c/test_pwscore.c)
  target_link_libraries(test_pwscore_c PRIVATE pwscore)
  add_executable(test_pwscore_cpp cpp/test_pwscore.cpp)
  target_link_libraries(test_pwscore_cpp PRIVATE pwscore::cpp)
  if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(pwscore PRIVATE -Wall -Wextra -pedantic)
    target_compile_options(test_pwscore_c PRIVATE -Wall -Wextra)
    target_compile_options(test_pwscore_cpp PRIVATE -Wall -Wextra)
  endif()
  add_test(NAME c COMMAND test_pwscore_c ${CMAKE_CURRENT_SOURCE_DIR}/spec/score-snapshot.tsv)
  add_test(NAME cpp COMMAND test_pwscore_cpp ${CMAKE_CURRENT_SOURCE_DIR}/spec/score-snapshot.tsv)
endif()
