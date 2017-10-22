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

package net.kervala.comicsreader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.util.Log;

public class RarFile {
	private String mName;
	private List<String> mEntries = new ArrayList<String>();
	private static String mVersion;
	private static boolean sLoaded = false;

	// implemented by libunrar-jni.so
	private static native String[] nativeGetEntries(String filename);
	private static native byte[] nativeGetData(String filename, String entry);
	private static native String nativeGetVersion();
	private static native void nativeTests();
	private static native void nativeInit();
	private static native void nativeDestroy();

	static boolean isLoaded() {
		return sLoaded;
	}

	public static void destroy() {
		nativeDestroy();
	}

	public static String getVersion() {
		if (mVersion == null && sLoaded) {
			mVersion = nativeGetVersion();
			nativeTests();
		}
		return mVersion;
	}
	
	public RarFile(File file) throws IOException {
		if (file == null || !open(file.getAbsolutePath())) {
			throw new IOException();
		}
	}

	public RarFile(String filename) throws IOException {
		open(filename);
	}

	private boolean open(String filename) throws IOException {
		if (filename == null) {
			throw new IOException();
		}

		mName = filename;

		return true;
	}

	public void close() {
		mEntries = null;
		mName = null;
	}

	public List<String> entries() {
		if (sLoaded && mEntries.isEmpty()) {
			String [] entries = nativeGetEntries(mName);

			// load all entries if not already processed
			if (entries != null) {
				mEntries = Arrays.asList(entries);
			}
		}
		return mEntries;
	}

	byte [] getBytes(String entry) {
		if (!sLoaded) return null;

		try {
			return nativeGetData(mName, entry);
		} catch(OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "Out of memory while getting file " + entry + " from " + mName);
		}

		return null;
	}

	public String getName() {
		return mName;
	}

	public int size() {
		return mEntries.size();
	}

	// load our native library
	static {
		try {
			System.loadLibrary("unrar-jni");
			sLoaded = true;
		} catch (UnsatisfiedLinkError e) {
			Log.e(ComicsParameters.APP_TAG, "Unrar library can't be loaded, RAR support is disabled: " + e);
		}
	}
}
