cmake_minimum_required(VERSION 3.4.1)

PROJECT(Unrar CXX)

# additional compiler flags
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -fvisibility=hidden -fPIC")

IF(ANDROID_ABI STREQUAL "armeabi-v7a")
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D__ARM_V7__")
ENDIF()

IF(CMAKE_BUILD_TYPE STREQUAL "Debug")
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D_DEBUG")
ELSE()
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
ENDIF()

SET(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--as-needed")

ADD_SUBDIRECTORY(unrar)
ADD_SUBDIRECTORY(unrar-jni)
