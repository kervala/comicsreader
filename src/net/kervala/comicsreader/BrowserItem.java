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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.Log;

public class BrowserItem extends ThumbnailItem {
	private String mFilename;
	private String mPath;
	private String mAlbumUrl;
	private int mSize = 0;
	private int mType;
	private String mThumbnailUrl;
	private boolean mRemote;

	// item type
	static final int TYPE_NONE = 0;
	static final int TYPE_FILE = 1;
	static final int TYPE_DIRECTORY_CHILD = 2;
	static final int TYPE_DIRECTORY_PARENT = 3;

	public BrowserItem(String name, int type, boolean remote) {
		mText = name;
		mType = type;
		mRemote = remote;
		mThumbPosition = THUMB_POSITION_TOP;

		if (type != TYPE_FILE) mStatus = STATUS_UPDATED;
	}

	public boolean getRemote() {
		return mRemote;
	}

	public int getType() {
		return mType;
	}
	
	public void setPath(String path) {
		mPath = path;
	}

	public String getPath() {
		return mPath;
	}
	
	public void setFilename(String filename) {
		mFilename = filename;
	}

	public void setSize(int size) {
		mSize = size;
	}
	
	public int getSize() {
		return mSize;
	}

	public void setAlbumUrl(String albumUrl) {
		mAlbumUrl = albumUrl.replace("#", "%23");
	}
	
	public String getAlbumUrl() {
		return mAlbumUrl;
	}
	
	public void setThumbnailUrl(String thumbnailUrl) {
		mThumbnailUrl = thumbnailUrl.replace("#", "%23");
	}
	
	public String getMimeType() {
		return Album.mimeType(mPath);
	}
	
	public File getFile() {
		if (mFilename == null) return null;

		return new File(ComicsParameters.sCacheDirectory, mFilename);
	}
	
	public Uri getLocalUri() {
		if (mPath == null) return null;

		return Uri.fromFile(new File(mPath));
	}
	
	boolean createThumbnail(String filename) {
		Album album = Album.createInstance(filename);
		
		if (album.open(filename, false)) {
			// get a thumbnail for first page
			if (album.updateThumbnail(album.currentPageNumber)) {
				mThumb = ComicsHelpers.cropThumbnail(ComicsHelpers.resizeThumbnail(album.getPageThumbnail(album.currentPageNumber)));
			}

			album.close();
		}

		if (mThumb != null) {
			// if thumbnail can't be saved, continue
			try {
				File f = new File(ComicsParameters.sCoversDirectory, ComicsHelpers.md5(filename) + ".png");
				OutputStream os = new FileOutputStream(f);
				mThumb.compress(Bitmap.CompressFormat.PNG, 70, os);
				os.close();
			} catch (FileNotFoundException e) {
				Log.e(ComicsParameters.APP_TAG, filename + " not found");
			} catch (IOException e) {
				Log.e(ComicsParameters.APP_TAG, e.getMessage());
			} catch (Error e) {
				Log.e(ComicsParameters.APP_TAG, "Error: " + e.getMessage());
			} catch (Exception e) {
				Log.e(ComicsParameters.APP_TAG, "Exception: " + e.getMessage());
			}
		}

		return mThumb != null;
	}
	
	@Override
	protected boolean loadBitmap() {
		if (mType == TYPE_FILE) {
			if (!mRemote) {
				// try to load thumbnail from cache
				mThumb = ComicsHelpers.getThumbnailFromCache(mPath);
				
				if (mThumb == null) {
					// if file is not in cache, create it
					return createThumbnail(mPath);
				}
			} else {
				mThumb = ComicsHelpers.getThumbnailFromCache(mThumbnailUrl);

				if (mThumb == null) {
					if (ComicsHelpers.downloadThumbnailFromUrl(mThumbnailUrl)) {
						mThumb = ComicsHelpers.getThumbnailFromCache(mThumbnailUrl);
					}

					if (mThumb == null) return false;
				}
			}
		}
		
		return true;
	}

	@Override
	protected BitmapDrawable getDefaultDrawable() {
		switch(mType) {
			case TYPE_FILE:
			return ComicsParameters.sPlaceholderDrawable;

			case TYPE_DIRECTORY_CHILD:
			return ComicsParameters.sFolderChildDrawable;

			case TYPE_DIRECTORY_PARENT:
			return ComicsParameters.sFolderParentDrawable;
		}
		
		return null;
	}
}
