/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2012 Cedric OCHS
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
import java.io.InputStream;
import java.util.List;

public class CbrAlbum extends Album {
	private RarFile mRar;

	static final String cbrMimeType = "application/x-cbr";
	static final String cbrExtension = "cbr";
	
	public String getExtension() {
		return cbrExtension;
	}

	public String getMimeType() {
		return cbrMimeType;
	}
	
	public static boolean isValid(String filename) {
		File file = new File(filename);

		boolean valid = false;

		if (file.isFile() && file.canRead()) {
			try {
				FileInputStream ifs = new FileInputStream(file);

				byte [] buffer = new byte[4];
					
				if (ifs.read(buffer, 0, 4) == 4 && buffer[0] == 'R' && buffer[1] == 'a' && buffer[2] == 'r' && buffer[3] == '!') {
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
		return cbrMimeType;
	}
	
	boolean loadFiles() {
		try {
			// open RAR file
			mRar = new RarFile(mFilename);
		} catch (IOException e) {
			return false;
		}

		List<String> files = mRar.entries();

		for(String filename: files) {
			if (isValidImage(filename)) {
				mFiles.add(filename);
			}
		}
		
		// generate a title from filename
		int first = mFilename.lastIndexOf("/");
			
		if (first != -1) {
			mTitle = mFilename.substring(first+1, mFilename.length());
		} else {
			mTitle = mFilename;
		}

		int last = mTitle.lastIndexOf(".");
			
		if (last != -1) {
			mTitle = mTitle.substring(0, last);
		}
		
		return true;
	}

	public void close() {
		super.close();

		if (mRar != null)
			mRar.close();
	}
	
	protected InputStream getInputStream(int page) throws IOException {
		// get a stream on a page
		return mRar.getInputStream(mFiles.get(page));
	}
}
