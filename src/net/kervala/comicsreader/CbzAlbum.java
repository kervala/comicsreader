/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2013 Cedric OCHS
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CbzAlbum extends Album {
	private ZipFile mZip;

	static final String cbzMimeType = "application/x-cbz";
	static final String cbzExtension = "cbz";

	public String getExtension() {
		return cbzExtension;
	}

	public String getMimeType() {
		return cbzMimeType;
	}
	
	public static boolean isValid(String filename) {
		File file = new File(filename);

		boolean valid = false;

		if (file.isFile() && file.canRead()) {
			try {
				FileInputStream ifs = new FileInputStream(file);

				byte [] buffer = new byte[2];

				if (ifs.read(buffer, 0, 2) == 2 && buffer[0] == 'P' && buffer[1] == 'K') {
					valid = true;
				}

				ifs.close();
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			}
		}

		return valid;
	}
	
	public static boolean askConfirm(String filename) {
		return false;
	}

	static String getTitle(String filename) {
		String title = filename;

		int posExt = title.lastIndexOf(".");
		if (posExt > -1) title = title.substring(0, posExt);

		int posPath = title.lastIndexOf("/");
		if (posPath > -1) title = title.substring(posPath + 1);

		return title;
	}
	
	public static String getMimeType(String filename) {
		return cbzMimeType;
	}
	
	boolean loadFiles() {
		try {
			// open ZIP file
			mZip = new ZipFile(filename);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		Enumeration<? extends ZipEntry> zippedFiles = mZip.entries();

		// add all pages
		while (zippedFiles.hasMoreElements()) {
			ZipEntry entry = zippedFiles.nextElement();
			if (!entry.isDirectory()) {
				String filename = entry.getName();
				if (Album.isValidImage(filename)) {
					mFiles.add(filename);
				}
			}
		}
		
		if (mFiles.isEmpty()) {
			close();
			return false;
		}

		// generate a title from filename
		int first = filename.lastIndexOf("/");
			
		if (first != -1) {
			title = filename.substring(first+1, filename.length());
		} else {
			title = filename;
		}

		int last = title.lastIndexOf(".");
			
		if (last != -1) {
			title = title.substring(0, last);
		}

		return true;
	}

	public void close() {
		super.close();

		try {
			if (mZip != null) {
				mZip.close();
				mZip = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected byte [] getBytes(int page) {
		byte [] buffer = null;

		ZipEntry entry = mZip.getEntry(mFiles.get(page));
		
		if (entry != null) {
			try {
				buffer = ComicsHelpers.inputStreamToBytes(mZip.getInputStream(entry), (int)entry.getSize());
			} catch (IOException e) {
				buffer = null;
				e.printStackTrace();
			}
		}

		return buffer;
	}
}
