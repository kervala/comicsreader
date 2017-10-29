/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2018 Cedric OCHS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

#include "net_kervala_comicsreader_RarFile.h"

#include <android/log.h>
#include <string.h>
#include "strings.h"

#include "rartypes.hpp"
#include "rar.hpp"
#include "version.hpp"
#include "dll.hpp"

//#define USE_DIRECTBYTEBUFFER

static jclass stringClass = NULL;

#define  LOG_TAG    "libunrar-jni"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#ifdef __arm__
typedef long unsigned int *_Unwind_Ptr;

/* Stubbed out in libdl and defined in the dynamic linker.
 * Same semantics as __gnu_Unwind_Find_exidx().
 */
extern "C" _Unwind_Ptr dl_unwind_find_exidx(_Unwind_Ptr pc, int *pcount);
extern "C" _Unwind_Ptr __gnu_Unwind_Find_exidx(_Unwind_Ptr pc, int *pcount)
{
	return dl_unwind_find_exidx(pc, pcount);
}

static void* g_func_ptr;

#endif

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
#ifdef __arm__
	// when i throw exception, linker maybe can't find __gnu_Unwind_Find_exidx(lazy binding issue??)
	// so I force to bind this symbol at shared object load time
	g_func_ptr = (void*)__gnu_Unwind_Find_exidx;
#endif

	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) return -1;

	jclass c = env->FindClass("java/lang/String");

	if (c == NULL)
	{
		LOGE("Can't find class java.lang.String");
	}
	else
	{
		stringClass = (jclass)env->NewGlobalRef(c);

		// release local reference to String class
		env->DeleteLocalRef(c);
	}

#ifdef __arm__
	LOGI("Loading unrar ARM library");
#else
	LOGI("Loading unrar non-ARM library");
#endif
	
	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved)
{
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) return;

	if (stringClass == NULL) return;

	env->DeleteGlobalRef(stringClass);

	stringClass = NULL;
}

void displayError(int error, const char *filename)
{
	switch(error)
	{
		case ERAR_END_ARCHIVE:
		LOGE("Unable to open %s, ERAR_END_ARCHIVE", filename);
		break;

		case ERAR_NO_MEMORY:
		LOGE("Unable to open %s, ERAR_NO_MEMORY", filename);
		break;

		case ERAR_BAD_DATA:
		LOGE("Unable to open %s, ERAR_BAD_DATA", filename);
		break;

		case ERAR_BAD_ARCHIVE:
		LOGE("Unable to open %s, ERAR_BAD_ARCHIVE", filename);
		break;

		case ERAR_UNKNOWN_FORMAT:
		LOGE("Unable to open %s, ERAR_UNKNOWN_FORMAT", filename);
		break;

		case ERAR_EOPEN:
		LOGE("Unable to open %s, ERAR_EOPEN", filename);
		break;

		case ERAR_ECREATE:
		LOGE("Unable to open %s, ERAR_ECREATE", filename);
		break;

		case ERAR_ECLOSE:
		LOGE("Unable to open %s, ERAR_ECLOSE", filename);
		break;

		case ERAR_EREAD:
		LOGE("Unable to open %s, ERAR_EREAD", filename);
		break;

		case ERAR_EWRITE:
		LOGE("Unable to open %s, ERAR_EWRITE", filename);
		break;

		case ERAR_SMALL_BUF:
		LOGE("Unable to open %s, ERAR_SMALL_BUF", filename);
		break;

		case ERAR_UNKNOWN:
		LOGE("Unable to open %s, ERAR_UNKNOWN", filename);
		break;

		case ERAR_MISSING_PASSWORD:
		LOGE("Unable to open %s, ERAR_MISSING_PASSWORD", filename);
		break;

		default:
		LOGE("Unable to open %s, unknown error: %d", filename, error);
	}
}

JNIEXPORT jobjectArray JNICALL Java_net_kervala_comicsreader_RarFile_nativeGetEntries(JNIEnv *env, jclass, jstring jFilename)
{
	jobjectArray ret = NULL;

	if (stringClass == NULL)
	{
		LOGE("String class is NULL");

		return ret;
	}

	const char *filename = env->GetStringUTFChars(jFilename, NULL);

	if (env->ExceptionCheck())
	{
		env->ExceptionDescribe();
		return ret;
	}

	RAROpenArchiveData data;
	memset(&data, 0, sizeof(RAROpenArchiveData));

	data.ArcName = filename;
	data.OpenMode = RAR_OM_LIST;

	HANDLE handle = RAROpenArchive(&data);

	if (handle && !data.OpenResult)
	{
		RARHeaderData header;
		memset(&header, 0, sizeof(RARHeaderData));

		Strings list;

		// read all entries
		while (RARReadHeader(handle, &header) == 0)
		{
//			LOGD("Found %s", header.FileName);

			// add file to list only if not a directory and not NULL
			if ((header.Flags & LHD_DIRECTORY) == 0) list.addString(header.FileName);

			// skip entry content
			int result = RARProcessFile(handle, RAR_SKIP, NULL, NULL);

			if (result)
			{
				LOGE("Unable to process %s, error: %d", header.FileName, result);
			}
		}

		RARCloseArchive(handle);

		int count = (int)list.size();

//		LOGD("Found %d pages", count);

		if (count > 0)
		{
			ret = (jobjectArray)env->NewObjectArray(count, stringClass, NULL);

			if (env->ExceptionCheck())
			{
				env->ExceptionDescribe();
			}
			else
			{
				Strings *tmp = &list;

				int i = 0;

				// don't put more strings than allocated
				while(tmp && i < count)
				{
					const char *str = tmp->getString();

					if (str)
					{
						// create a jstring from a UTF-8 string
						// TODO: fix Modified UTF-8 format
						jstring newStr = env->NewStringUTF(str);

						if (env->ExceptionCheck())
						{
							env->ExceptionDescribe();
							break;
						}
						else if (newStr)
						{
							// if string is NULL, don't increase list size
							env->SetObjectArrayElement(ret, i++, newStr);

							if (env->ExceptionCheck())
							{
								env->ExceptionDescribe();
								break;
							}

							env->DeleteLocalRef(newStr);
						}
						else
						{
							LOGE("NewStringUTF returned NULL for %s", str);
						}
					}
					else
					{
						LOGE("NULL filename returned for item %d", i);
					}

					tmp = tmp->getNext();
				}
			}
		}
	}
	else
	{
		displayError(data.OpenResult, filename);
	}

	env->ReleaseStringUTFChars(jFilename, filename);
	
	return ret;
}

class Buffer
{
public:
	Buffer(JNIEnv *env, size_t size)
	{
		m_position = 0;
		m_env = env;

		if (size < 1)
		{
			m_buffer = NULL;
			m_size = 0;

			LOGE("Unable to allocate less than 1 byte");
			return;
		}

		// allocates a new Java buffer
#ifdef USE_DIRECTBYTEBUFFER
		m_buffer = new uint8_t[size];
#else
		m_buffer = env->NewByteArray(size);

		if (env->ExceptionCheck())
		{
			m_size = 0;
			m_buffer = NULL;
			m_env->ExceptionDescribe();
			return;
		}

		if (m_buffer == NULL)
		{
			LOGE("Unable to allocate %d bytes in Java", (int)size);
		}
#endif

		m_size = size;
	}

	~Buffer()
	{
	}

	void appendBytes(unsigned char *addr, size_t size)
	{
		// to avoid overflows
		if (size + m_position > m_size) size = m_size - m_position;

		if (size > 0)
		{
#ifdef USE_DIRECTBYTEBUFFER
			memcpy(m_buffer + m_position, addr, size);
#else
			// copy C++ buffer data to Java buffer
			m_env->SetByteArrayRegion(m_buffer, m_position, size, (jbyte *)addr);

			if (m_env->ExceptionCheck())
			{
				m_env->ExceptionDescribe();
				return;
			}
#endif

			m_position += size;
		}
	}

	void release()
	{
		if (m_buffer)
		{
			m_env->DeleteLocalRef(m_buffer);

			m_buffer = NULL;
		}
	}

#ifndef USE_DIRECTBYTEBUFFER
	jbyteArray getBuffer() const { return m_buffer; }
#endif

	size_t getSize() const { return m_size; }

#ifdef USE_DIRECTBYTEBUFFER
	jobject getByteBuffer()
	{
		return m_env->NewDirectByteBuffer(m_buffer, m_size);
	}
#endif

private:
#ifdef USE_DIRECTBYTEBUFFER
	uint8_t *m_buffer;
#else
	jbyteArray m_buffer;
#endif

	size_t m_size;
	size_t m_position;
	JNIEnv *m_env;
};

int CALLBACK callbackData(UINT msg, LPARAM UserData, LPARAM P1, LPARAM P2)
{
	if (msg == UCM_PROCESSDATA)
	{
		Buffer *buffer = (Buffer*)UserData;

		if (buffer) buffer->appendBytes((unsigned char*)P1, (size_t)P2);
	}

	return 1;
}

JNIEXPORT jbyteArray JNICALL Java_net_kervala_comicsreader_RarFile_nativeGetData(JNIEnv *env, jclass, jstring jFilename, jstring jEntry)
{
	jbyteArray ret = NULL;

	const char *filename = env->GetStringUTFChars(jFilename, NULL);

	if (env->ExceptionCheck())
	{
		env->ExceptionDescribe();
		return ret;
	}

	if (filename == NULL)
	{
		LOGE("Unable to get filename C string");
		return ret;
	}

	const char *entry = env->GetStringUTFChars(jEntry, NULL);

	if (env->ExceptionCheck())
	{
		LOGE("Unable to get entry C string: %p", entry);

		// release previous string because it succeeded
		env->ReleaseStringUTFChars(jFilename, filename);

		env->ExceptionDescribe();

		return ret;
	}

	if (entry == NULL)
	{
		// release previous string because it succeeded
		env->ReleaseStringUTFChars(jFilename, filename);

		LOGE("Unable to get entry C string");
		return ret;
	}

	RAROpenArchiveData data;
	memset(&data, 0, sizeof(RAROpenArchiveData));

	data.ArcName = filename;
	data.OpenMode = RAR_OM_EXTRACT;

	HANDLE handle = RAROpenArchive(&data);

	if (handle && !data.OpenResult)
	{
		RARHeaderData header;
		memset(&header, 0, sizeof(RARHeaderData));

		// process each entry
		while (RARReadHeader(handle, &header) == 0)
		{
			// check if we must process this entry
			if (strcmp(header.FileName, entry) == 0)
			{
				if (header.UnpSize > 0)
				{
					// set buffer related variables
					Buffer buffer(env, header.UnpSize);
					
					if (buffer.getBuffer() == NULL)
					{
						LOGE("Error while allocating %d bytes", (int)header.UnpSize);
					}
					else
					{
						// set buffer callback
						RARSetCallback(handle, callbackData, (LPARAM)&buffer);

						// don't use RAR_EXTRACT because files will be extracted in current directory
						int result = RARProcessFile(handle, RAR_TEST, NULL, NULL);

						if (result)
						{
							buffer.release();

							LOGE("Unable to process %s, error: %d", header.FileName, result);
						}
						else
						{
							ret = buffer.getBuffer();
						}
					}
				}

				break;
			}
			else
			{
				// skip this entry
				int result = RARProcessFile(handle, RAR_SKIP, NULL, NULL);

				if (result)
				{
					LOGE("Unable to skip %s, error: %d", header.FileName, result);
				}
			}
		}

		RARCloseArchive(handle);
	}
	else
	{
		displayError(data.OpenResult, filename);
	}

	// release UTF-8 strings
	env->ReleaseStringUTFChars(jEntry, entry);
	env->ReleaseStringUTFChars(jFilename, filename);

	return ret;
}

JNIEXPORT jstring JNICALL Java_net_kervala_comicsreader_RarFile_nativeGetVersion(JNIEnv *env, jclass)
{
	char version[32];

	// build verbose version string
	sprintf(version, "%d.%d.%d (%04d-%02d-%02d)", RARVER_MAJOR, RARVER_MINOR, RARVER_BETA, RARVER_YEAR, RARVER_MONTH, RARVER_DAY);

	return env->NewStringUTF(version);
}

JNIEXPORT void JNICALL Java_net_kervala_comicsreader_RarFile_nativeTests(JNIEnv *env, jclass)
{
#if defined(_DEBUG) || defined(DEBUG)
	wchar_t buffer[50];
	wchar_t str1[] = L"test1";
	wchar_t str2[] = L"test2";

	LOGI("wcslen returned %d", (int)wcslen(str1));
	LOGI("wcscmp returned %d", (int)wcscmp(str1, str2));
	LOGI("wcsncmp returned %d", (int)wcsncmp(str1, str2, 5));
	LOGI("wcschr returned %p", (void*)wcschr(str1, L'1'));

	wcscpy(buffer, str1);
	wcscpy(buffer, str2);

	LOGI("wcscpy returned %ls %d", buffer, (int)wcslen(buffer));

	wcsncpy(buffer, str1, 5);
	wcsncpy(buffer, str2, 5);

	LOGI("wcsncpy returned %ls %d", buffer, (int)wcslen(buffer));

	wcscat(buffer, str2);

	LOGI("wcscat returned %ls %d", buffer, (int)wcslen(buffer));

	Strings list;

	list.addString(NULL);
	list.addString("12345");
	list.addString("12345\012345");
	list.addString("");
	list.addString(NULL);

	LOGI("Strings size() returned %d and should be 3", (int)list.size());
#endif
}

JNIEXPORT void JNICALL Java_net_kervala_comicsreader_RarFile_nativeInit(JNIEnv *env, jclass)
{
}

JNIEXPORT void JNICALL Java_net_kervala_comicsreader_RarFile_nativeDestroy(JNIEnv *env, jclass)
{
}
