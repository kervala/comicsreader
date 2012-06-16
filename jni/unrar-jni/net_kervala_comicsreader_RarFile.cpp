/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011 Cedric OCHS
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

#include "rar.hpp"
#include "version.hpp"
#include "dll.hpp"

#define  LOG_TAG    "libunrar-jni"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) return -1;

	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *, void *)
{
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

	const char *filename = env->GetStringUTFChars(jFilename, NULL);

	RAROpenArchiveData data;
	memset(&data, 0, sizeof(RAROpenArchiveData));

	data.ArcName = (char*)filename;
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
			// add file to list only if not a directory
			if ((header.Flags & LHD_DIRECTORY) != LHD_DIRECTORY) list.addString(header.FileName);

			// skip entry content
			int result = RARProcessFile(handle, RAR_SKIP, NULL, NULL);

			if (result)
			{
				LOGE("Unable to process %s, error: %d", header.FileName, result);
			}
		}

		RARCloseArchive(handle);

		size_t count = list.size();

		if (count > 0)
		{
			ret = (jobjectArray)env->NewObjectArray(count, env->FindClass("java/lang/String"), NULL);

			Strings *tmp = &list;
		
			int i = 0;
		
			while(tmp)
			{
				env->SetObjectArrayElement(ret, i++, env->NewStringUTF(tmp->getString()));

				tmp = tmp->getNext();
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

	Buffer(size_t size)
	{
		m_position = 0;
		m_size = size;
		m_address = new unsigned char[m_size];
	}

	~Buffer()
	{
		if (m_address) delete [] m_address;
	}

	void appendBytes(unsigned char *addr, size_t size)
	{
		memcpy(m_address + m_position, addr, size);
		m_position += size;
	}

	unsigned char* getAddress() const { return m_address; }
	size_t getSize() const { return m_size; }

	private:

	unsigned char *m_address;
	size_t m_size;
	size_t m_position;
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
	const char *entry = env->GetStringUTFChars(jEntry, NULL);

	RAROpenArchiveData data;
	memset(&data, 0, sizeof(RAROpenArchiveData));

	data.ArcName = (char*)filename;
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
				// set buffer related variables
				Buffer buffer(header.UnpSize);

				RARSetCallback(handle, callbackData, (LPARAM)&buffer);

				// don't use RAR_EXTRACT because files will be extracted in current directory
				int result = RARProcessFile(handle, RAR_TEST, NULL, NULL);

				if (result)
				{
					LOGE("Unable to process %s, error: %d", header.FileName, result);
				}
				else
				{
					// allocates a new Java buffer
					ret = env->NewByteArray(buffer.getSize());

					// copy C++ buffer data to Java buffer
					env->SetByteArrayRegion(ret, 0, buffer.getSize(), (jbyte *)buffer.getAddress());

					// exit if file was correctly extracted
					break;
				}
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
