LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := unrar-jni
LOCAL_CFLAGS += -Wall -fvisibility=hidden

LOCAL_SRC_FILES := strings.cpp net_kervala_comicsreader_RarFile.cpp
LOCAL_SHARED_LIBRARIES :=
LOCAL_STATIC_LIBRARIES := unrar

LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS := -Wl,--as-needed

include $(BUILD_SHARED_LIBRARY)
