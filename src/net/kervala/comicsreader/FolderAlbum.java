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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class FolderAlbum extends Album {
	private File mFolder;

	static final String folderMimeType = "";
	static final String jpegMimeType = "image/jpeg";
	static final String pngMimeType = "image/png";
	static final String folderExtension = "";

	public String getExtension() {
		return folderExtension;
	}

	public String getMimeType() {
		return folderMimeType;
	}

	public static class ImageFilter implements FilenameFilter {
		public boolean accept(File dir, String filename) {
			File f = new File(dir, filename);

			return isValidImage(f.getAbsolutePath());
		}
	}

	public static class OtherFilter implements FilenameFilter {
		public boolean accept(File dir, String filename) {
			File f = new File(dir, filename);

			// build a full absolute path
			filename = f.getAbsolutePath();  

			return CbzAlbum.isValid(filename) || CbrAlbum.isValid(filename) || CbtAlbum.isValid(filename) || f.isDirectory();
		}
	}
	
	public static boolean isValid(String filename) {
		// if filename is an image, that's ok
		if (isValidImage(filename)) return true;
		
		// if filename is a directory, check if it contains any images
		File dir = new File(filename);
		
		if (dir.isDirectory()) {
			String [] filesOk = dir.list(new ImageFilter());
			
			// if at least one file is an image, that's ok
			if (filesOk != null && filesOk.length > 0) return true;
		}
		
		return false;
	}
	
	public static boolean askConfirm(String filename) {
		// if filename is a directory, check if it contains any images
		File dir = new File(filename);

		if (dir.isDirectory()) {
			String [] filesBad = dir.list(new OtherFilter());

			// if there are other files, ask before opening it
			if (filesBad != null && filesBad.length > 0) return true;
		}
		
		return false;
	}

	static String getTitle(String filename) {
		String title = null;
		
		// generate a title from filename
		int first = filename.lastIndexOf("/");

		if (first > 0) {
			title = filename.substring(first+1, filename.length());
		} else {
			title = filename;
		}

		return title;
	}
	
	public static String getMimeType(String filename) {
		if (isValidJpegImage(filename)) {
			return jpegMimeType;
		}

		if (isValidPngImage(filename)) {
			return pngMimeType;
		}
		
		return folderMimeType;
	}
	
	boolean loadFiles() {
		mFolder = new File(mFilename);
		
		if (!mFolder.isDirectory()) {
			mFolder = mFolder.getParentFile();

			// generate a title from filename
			int first = mFilename.lastIndexOf("/");

			if (first > 0) {
				mTitle = mFilename.substring(first+1, mFilename.length());
			} else {
				mTitle = mFilename;
			}
			
			mCurrentPageFilename = mTitle;
			mFilename = mFolder.getAbsolutePath();
		}

		mFiles = Arrays.asList(mFolder.list(new ImageFilter()));

		if (mFiles.isEmpty()) return false;

		// generate a title from filename
		int first = mFilename.lastIndexOf("/");

		if (first > 0) {
			mTitle = mFilename.substring(first+1, mFilename.length());
		} else {
			mTitle = mFilename;
		}

		return true;
	}

	public void close() {
		super.close();

		mFolder = null;
	}
	
	protected byte [] getBytes(int page) throws IOException {
		// get a buffer on a page
		File file = new File(mFolder, mFiles.get(page));

		if (!file.exists()) return null;

		InputStream fis = new FileInputStream(file);
		InputStream bis = new BufferedInputStream(fis, ComicsParameters.BUFFER_SIZE);

		return Album.inputStreamToBytes(bis, (int)file.length());
	}
}
