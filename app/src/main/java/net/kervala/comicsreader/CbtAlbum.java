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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class CbtAlbum extends Album {
	private TarFile mTar;

	static final String cbtMimeType = "application/x-cbt";
	static final String cbtExtension = "cbt";
	
	public String getExtension() {
		return cbtExtension;
	}

	public String getMimeType() {
		return cbtMimeType;
	}

	public static boolean isValid(String filename) {
		File file = new File(filename);

		boolean valid = false;

		if (file.isFile() && file.canRead() && (file.length() % 512 == 0)) {
			try {
				FileInputStream ifs = new FileInputStream(file);

				if (ifs.skip(99) == 99) {
					byte [] buffer = new byte[1];
					
					if (ifs.read(buffer, 0, 1) == 1) {
						if (buffer[0] == 0) {
							valid = true;
						}
					}
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
		return cbtMimeType;
	}
	
	boolean loadFiles() {
		try {
			// open RAR file
			mTar = new TarFile(filename);
		} catch (IOException e) {
			return false;
		}

		List<String> files = mTar.entries();

		for(String filename: files) {
			if (isValidImage(filename)) {
				mFiles.add(filename);
			}
		}

		if (mFiles.isEmpty()) return false;
		
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

		if (mTar != null)
			mTar.close();
	}

	protected byte [] getBytes(int page) {
		// get a buffer on a page
		return mTar.getBytes(mFiles.get(page));
	}
}
