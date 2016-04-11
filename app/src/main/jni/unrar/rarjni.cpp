#include "rar.hpp"

#ifdef _ANDROID

#include <android/log.h>

FileHandle JniOpenFile(const wchar *filename)
{
#ifdef _DEBUG
	__android_log_print(ANDROID_LOG_DEBUG, "unrar", "JniOpenFile");
#endif

	return FILE_BAD_HANDLE;
}

bool JniCharToWide(const char *Src, wchar *Dest, size_t DestSize, bool unk)
{
#ifdef _DEBUG
	__android_log_print(ANDROID_LOG_DEBUG, "unrar", "JniCharToWide %s %d", Src, (int)DestSize);
#endif

	// TODO: don't know what to do there
	return UtfToWide(Src, Dest, DestSize);
}

#endif
