LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := unrar
LOCAL_CFLAGS += -Wall -DNOFILECREATE -DGUI -DSILENT -DNOVOLUME -DRARDLL -DUNRAR -DRAR_NOCRYPT \
	-D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -fvisibility=hidden
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)

LOCAL_SRC_FILES += \
	rar.cpp strlist.cpp strfn.cpp pathfn.cpp smallfn.cpp \
	global.cpp file.cpp filefn.cpp cmddata.cpp \
	archive.cpp arcread.cpp unicode.cpp system.cpp \
	isnt.cpp crypt.cpp crc.cpp rawread.cpp encname.cpp \
	match.cpp timefn.cpp rdwrfn.cpp options.cpp \
	errhnd.cpp rarvm.cpp secpassword.cpp rijndael.cpp getbits.cpp sha1.cpp \
	sha256.cpp blake2s.cpp hash.cpp extinfo.cpp extract.cpp volume.cpp \
	find.cpp unpack.cpp headers.cpp threadpool.cpp rs16.cpp \
	filestr.cpp scantree.cpp dll.cpp qopen.cpp
	# resource.cpp list.cpp not used
	# rarpch.cpp recvol.cpp rs.cpp only used under Windows
	# filcreat.cpp consio.cpp remove to earn some space
 
LOCAL_SHARED_LIBRARIES := 
LOCAL_STATIC_LIBRARIES := 

include $(BUILD_STATIC_LIBRARY)
