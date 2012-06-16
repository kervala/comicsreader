LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := unrar
LOCAL_CFLAGS += -Wall -DNOVOLUME -DRARDLL -DRAR_NOCRYPT \
	-D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -fvisibility=hidden
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

LOCAL_SRC_FILES += filestr.cpp scantree.cpp dll.cpp \
	rar.cpp strlist.cpp strfn.cpp pathfn.cpp savepos.cpp smallfn.cpp \
	global.cpp file.cpp filefn.cpp filcreat.cpp archive.cpp arcread.cpp \
	unicode.cpp system.cpp isnt.cpp crypt.cpp crc.cpp rawread.cpp encname.cpp \
	resource.cpp match.cpp timefn.cpp rdwrfn.cpp consio.cpp options.cpp \
	ulinks.cpp errhnd.cpp rarvm.cpp rijndael.cpp getbits.cpp sha1.cpp \
	extinfo.cpp extract.cpp volume.cpp list.cpp find.cpp unpack.cpp \
	cmddata.cpp secpassword.cpp

LOCAL_SHARED_LIBRARIES := 
LOCAL_STATIC_LIBRARIES := 

LOCAL_LDFLAGS := -Wl,--as-needed

include $(BUILD_STATIC_LIBRARY)
