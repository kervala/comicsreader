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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Log;

public class Album {

	public String title;
	public String filename;
	public int numPages = 0;
	public int currentPageNumber = 0;
	public int maxImagesInMemory = 0;

	protected List<String> mFiles = new ArrayList<String>();
	protected AlbumPage mPages[];
	protected String mCurrentPageFilename;
	protected File mCachePagesDir;
	protected File mCacheThumbnailsDir;
	
	protected int mFirstBufferPageNumber = 65536;
	protected int mLastBufferPageNumber = 0;
	protected boolean mAlwaysFull = false;
	
	final static String undefinedExtension = "";
	final static String undefinedMimeType = "";

	static final int ALBUM_TYPE_NONE = 0;
	static final int ALBUM_TYPE_CBZ = 1;
	static final int ALBUM_TYPE_CBR = 2;
	static final int ALBUM_TYPE_CBT = 3;
	static final int ALBUM_TYPE_FOLDER = 4;

	static final int ZOOM_NONE = 0;
	static final int ZOOM_FIT_WIDTH = 1;
	static final int ZOOM_FIT_HEIGHT = 2;
	static final int ZOOM_FIT_SCREEN = 3;
	static final int ZOOM_100 = 4;
	static final int ZOOM_50 = 5;
	static final int ZOOM_25 = 6;

	public int maxBitmapsInMemory = 3;
	public int maxBuffersInMemory = 6;

	static Album createInstance(String filename) {
		if (CbzAlbum.isValid(filename)) {
			return new CbzAlbum();
		}

		if (RarFile.isLoaded() && CbrAlbum.isValid(filename)) {
			return new CbrAlbum();
		}

		if (CbtAlbum.isValid(filename)) {
			return new CbtAlbum();
		}

		if (FolderAlbum.isValid(filename)) {
			return new FolderAlbum();
		}

		return new Album();
	}

	static Uri getUriFromFilename(String filename) {
		return Uri.parse(filename.replace("#", "%23"));
	}

	static String getFilenameFromUri(Uri uri) {
		return uri.getPath();
	}

	static String getPageFromUri(Uri uri) {
		return uri.getFragment();
	}

	static int getType(String uriString) {
		Uri uri = Album.getUriFromFilename(uriString);

		String filename = Album.getFilenameFromUri(uri);

		// file is a cbz
		if (CbzAlbum.isValid(filename)) {
			return ALBUM_TYPE_CBZ;
		}

		// file is a cbr
		if (RarFile.isLoaded() && CbrAlbum.isValid(filename)) {
			return ALBUM_TYPE_CBR;
		}

		// file is a cbt
		if (CbtAlbum.isValid(filename)) {
			return ALBUM_TYPE_CBT;
		}
		
		// file is an image or a folder containing images
		if (FolderAlbum.isValid(filename)) {
			return ALBUM_TYPE_FOLDER;
		}

		return ALBUM_TYPE_NONE;
	}

	static String mimeType(String uriString) {
		final Uri uri = Album.getUriFromFilename(uriString);
		final String filename = Album.getFilenameFromUri(uri);

		switch(getType(uriString))
		{
			case ALBUM_TYPE_CBZ: return CbzAlbum.getMimeType(filename);
			case ALBUM_TYPE_CBR: return CbrAlbum.getMimeType(filename);
			case ALBUM_TYPE_CBT: return CbtAlbum.getMimeType(filename);
			case ALBUM_TYPE_FOLDER: return FolderAlbum.getMimeType(filename);
			default: break;
		}

		return undefinedMimeType;
	}

	static boolean isUrlValid(String uriString) {
		if (uriString == null) return false;

		Uri uri = Uri.parse(uriString);
		
		String filename = Album.getFilenameFromUri(uri);

		if (filename == null) return false;

		if (!(new File(filename).exists())) return false;

		// file is a cbz
		if (CbzAlbum.isValid(filename)) return true;

		// file is a cbr
		if (RarFile.isLoaded() && CbrAlbum.isValid(filename)) return true;

		// file is a cbt
		if (CbtAlbum.isValid(filename)) return true;

		// file is an image or a folder containing images
		if (FolderAlbum.isValid(filename)) return true;

		return false;
	}
	
	static boolean isFilenameValid(String uriString) {
		if (uriString == null) return false;

		Uri uri = Album.getUriFromFilename(uriString);
		
		String filename = Album.getFilenameFromUri(uri);

		if (filename == null) return false;

		if (!(new File(filename).exists())) return false;

		// file is a cbz
		if (CbzAlbum.isValid(filename)) return true;

		// file is a cbr
		if (RarFile.isLoaded() && CbrAlbum.isValid(filename)) return true;

		// file is a cbt
		if (CbtAlbum.isValid(filename)) return true;

		// file is an image or a folder containing images
		if (FolderAlbum.isValid(filename)) return true;

		return false;
	}

	public static boolean askConfirm(String filename) {
		// file is a cbz
		if (CbzAlbum.askConfirm(filename)) return true;

		// file is a cbr
		if (RarFile.isLoaded() && CbrAlbum.askConfirm(filename)) return true;

		// file is a cbt
		if (CbtAlbum.askConfirm(filename)) return true;

		// file is an image or a folder containing images
		if (FolderAlbum.askConfirm(filename)) return true;
		
		return false;
	}
	
	static String getTitle(String filename) {
		switch(getType(filename))
		{
			case ALBUM_TYPE_CBZ: return CbzAlbum.getTitle(filename);
			case ALBUM_TYPE_CBR: return CbrAlbum.getTitle(filename);
			case ALBUM_TYPE_CBT: return CbtAlbum.getTitle(filename);
			case ALBUM_TYPE_FOLDER: return FolderAlbum.getTitle(filename);
			default: break;
		}

		return null;
	}
	
	static String getExtension(String filename) {
		int pos = filename.lastIndexOf(".");

		if (pos == -1) return "";
		
		return filename.toLowerCase(Locale.US).substring(pos+1);
	}

	public static boolean isValidJpegImage(String filename) {
		String ext = Album.getExtension(filename);

		return "jpg".equals(ext) || "jpeg".equals(ext) || "jpe".equals(ext);
	}

	public static boolean isValidPngImage(String filename) {
		return "png".equals(Album.getExtension(filename));
	}

	public static boolean isValidGifImage(String filename) {
		return "gif".equals(Album.getExtension(filename));
	}
	
	public static boolean isValidImage(String filename) {
		return isValidJpegImage(filename) || isValidPngImage(filename) || isValidGifImage(filename);
	}
	
	public Album() {
	}

	public String getExtension() {
		return undefinedExtension;
	}

	public String getMimeType() {
		return undefinedMimeType;
	}
	
	boolean loadFiles() {
		return false;
	}
	
	public boolean open(String file, boolean full) {
		if (file == null) return false;

		if (mAlwaysFull) full = true;

		filename = file;

		if (!loadFiles()) return false;

		numPages = full ? mFiles.size():Math.min(1, mFiles.size());

		Collections.sort(mFiles, new NaturalOrderComparator());

		if (mCurrentPageFilename != null) {
			currentPageNumber = mFiles.indexOf(mCurrentPageFilename);
			
			if (currentPageNumber == -1) currentPageNumber = 0;
		}

		mPages = new AlbumPage[numPages];

		for(int i = 0; i < numPages; ++i) {
			AlbumPage album = new AlbumPage(i, mFiles.get(i));

			mPages[i] = album;
		}
		
		if (full) {
			mCachePagesDir = new File(ComicsParameters.sPagesDirectory, ComicsHelpers.md5(filename));
			mCachePagesDir.mkdirs();

			checkMaxImagesInMemory();
		}

		return true;
	}
	
	public class Size implements Comparable<Size> {
		final int width;
		final int height;
		final int pixels;
		
		public Size(int w, int h) {
			width = w;
			height = h;
			pixels = w*h;
		}

		public int compareTo(Size s) {
			return pixels == s.pixels ? 0:pixels > s.pixels ? -1:1;
		}
	}
	
	public void checkMaxImagesInMemory() {
		maxImagesInMemory = 3; 

/*
		System.gc();
		
		ArrayList<Size> sizes = new ArrayList<Size>();
		
		// search for 3 biggest files
		for(int page = 0; page < mNumPages; ++page) {
			final BitmapFactory.Options options = getPageOptions(page);

			if (options != null) {
				sizes.add(new Size(options.outWidth, options.outHeight));
			}
		}

		Collections.sort(sizes);

		List<Bitmap> bitmaps = new ArrayList<Bitmap>();
		
		int scale = mScale;
		if (mScale < 1) {
			scale = 1;
//			int max = Math.max(ComicsParameters.sScreenWidth, ComicsParameters.sScreenHeight);
		}

		// check how many pages we can keep in memory
		for(int page = 0; page < Math.min(ComicsParameters.MAX_IMAGES_IN_MEMORY, sizes.size()); ++page) {
			final Size size = sizes.get(page);
			try {
				bitmaps.add(Bitmap.createBitmap(size.width / scale, size.height / scale, mHighQuality ? Bitmap.Config.ARGB_8888:Bitmap.Config.RGB_565));
			} catch(Error e) {
				break;
			} catch(Exception e) {
				break;
			}
		}

		mMaxImagesInMemory = bitmaps.size(); 

		// free memory used by temporary images
		for(Bitmap bitmap: bitmaps) {
			bitmap.recycle();
		}
		
		bitmaps.clear();
		
		System.gc();
*/
	}
	
	public Bitmap createPageThumbnail(int page) {
		File f = new File(mCachePagesDir, String.valueOf(page) + ".png");
		if (f.exists() && f.length() > 0) return null;

		// get a thumbnail for specified page
		if (!updateThumbnail(page)) return null;

		Bitmap bitmap = mPages[page].thumbnail;
		if (bitmap == null) return null;

		// if thumbnail can't be saved, continue
		try {
			OutputStream os = new FileOutputStream(f);
			bitmap.compress(Bitmap.CompressFormat.PNG, 70, os);
			os.close();
		} catch (FileNotFoundException e) {
			Log.e(ComicsParameters.APP_TAG, "Unable to create " + f);
		} catch (IOException e) {
			Log.e(ComicsParameters.APP_TAG, e.getMessage());
		} catch (Error e) {
			Log.e(ComicsParameters.APP_TAG, "Error: " + e.getMessage());
		} catch (Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception: " + e.getMessage());
		}
		
		return ComicsHelpers.resizeThumbnail(bitmap);
	}

	public Bitmap getPageThumbnailFromCache(int page) {
		return ComicsHelpers.loadThumbnail(new File(mCachePagesDir, String.valueOf(page) + ".png"));
	}

	public void clearThumbnailsCache() {
		// delete all pages thumbnails
		File[] files = mCacheThumbnailsDir.listFiles();
		for (File f : files) {
			f.delete();
		}

		// delete directory
		mCacheThumbnailsDir.delete();
	}

	public void clearPagesCache() {
		// delete all pages
		File[] files = mCachePagesDir.listFiles();
		for (File f : files) {
			f.delete();
		}

		// delete directory
		mCachePagesDir.delete();
	}

	public void close() {
		if (mPages != null) {
			for(AlbumPage page: mPages) {
				page.reset();
			}

			mPages = null;
		}

		filename = null; 
	}

	protected byte [] getBytes(int page) {
		Log.e("ComicsReader", "Album.getBytes shouldn't be called directly");
		
		return null;
	}
	
	public boolean updateDoublePage(int page, int width, int height) {
		if (page < 0 || page >= numPages) return false;

		updateBuffer(page);
		updateBuffer(page+1);
		
		mPages[page].updateSrcSize();
		mPages[page+1].updateSrcSize();
		
		// compute new sizes based on aspect ratio and scales
		mPages[page].updateBitmapDstSize(width, height);
		mPages[page+1].updateBitmapDstSize(width, height);
		
		// read sizes of 2 pages
		final AlbumPage.Size size1 = mPages[page].bitmapSize;
		final AlbumPage.Size size2 = mPages[page+1].bitmapSize;

		if (size1 == null || size2 == null) return false;
		
		Bitmap bitmap = null;
		Bitmap tmp = null;
		
		try {
			// create a new bitmap with the size of the 2 bitmaps
			bitmap = Bitmap.createBitmap(size1.dstWidth + size2.dstWidth, Math.max(size1.dstHeight, size2.dstHeight), AlbumParameters.highQuality ? Bitmap.Config.ARGB_8888:Bitmap.Config.RGB_565);

			Canvas canvas = new Canvas(bitmap);

			// get first page
			Bitmap tmpRaw = mPages[page].getPageRaw(size1.dstScale);

			if (tmpRaw == null) {
				bitmap.recycle();
				return false;
			}

			// good quality resize
			tmp = Bitmap.createScaledBitmap(tmpRaw, size1.dstWidth, size1.dstHeight, true);
			tmpRaw.recycle();

			canvas.drawBitmap(tmp, 0, 0, null);
			tmp.recycle();

			// get second page
			tmpRaw = mPages[page+1].getPageRaw(size2.dstScale);

			if (tmpRaw == null) {
				bitmap.recycle();
				tmp.recycle();
				return false;
			}

			// good quality resize
			tmp = Bitmap.createScaledBitmap(tmpRaw, size2.dstWidth, size2.dstHeight, true);
			tmpRaw.recycle();

			canvas.drawBitmap(tmp, size1.dstWidth, 0, null);
			tmp.recycle();

			mPages[page].bitmap = bitmap;

			return true;
		} catch(OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "Out of memory while assembling a double page");
		} catch (Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception: " + e.getMessage());
			e.printStackTrace();
		}

		if (bitmap != null) bitmap.recycle();
		if (tmp != null) tmp.recycle();

		return false;
	}
	
	public boolean updatePage(int page) {
		Log.d("ComicsReader", "updatePage " + String.valueOf(page));
		
		if (ComicsParameters.sScreenWidth < 1 || ComicsParameters.sScreenHeight < 1) return false;

		// already updated
		if (mPages[page].bitmap != null) return false;

		int screenWidth = ComicsParameters.sScreenWidth;
		int screenHeight = ComicsParameters.sScreenHeight;
		if (AlbumParameters.autoRotate && mPages[page].updateSrcSize()) {
			// prepare image for automatic rotation
			if (mPages[page].bitmapSize.srcWidth > mPages[page].bitmapSize.srcHeight &&
					ComicsParameters.sScreenHeight > ComicsParameters.sScreenWidth) {
				screenWidth = ComicsParameters.sScreenHeight;
				screenHeight = ComicsParameters.sScreenWidth;
			} else if (mPages[page].bitmapSize.srcHeight > mPages[page].bitmapSize.srcWidth &&
					ComicsParameters.sScreenWidth > ComicsParameters.sScreenHeight) {
				screenWidth = ComicsParameters.sScreenHeight;
				screenHeight = ComicsParameters.sScreenWidth;
			}
		}

		boolean divideByTwo = false;
		int width = -1;
		int height = -1;

		switch (AlbumParameters.zoom) {
		case ZOOM_FIT_WIDTH: {
			width = screenWidth;
			if (AlbumParameters.doublePage) {
				divideByTwo = true;
			}
			break;
		}
		case ZOOM_FIT_HEIGHT: {
			height = screenHeight;
			break;
		}
		case ZOOM_FIT_SCREEN: {
			width = screenWidth;
			if (AlbumParameters.doublePage) {
				divideByTwo = true;
			}
			height = screenHeight;
			break;
		}
		case ZOOM_50: {
			width = -2;
			height = -2;
			break;
		}
		case ZOOM_25: {
			width = -4;
			height = -4;
			break;
		}
		}

		if (AlbumParameters.doublePage && page > 0) {
			return updateDoublePage(page, divideByTwo ? width/2:width, height);
		} else {
			updateBuffer(page);

			if (!mPages[page].updateBitmap(width, height)) return false;
		}

//		debugMemory();

		return true;
	}

	protected void updateBuffers(int current, int next, int previous) {
		// TODO: make different algorithms depending on free memory

		// recycle all unused pages
		for(int i = mFirstBufferPageNumber; i <= mLastBufferPageNumber; ++i) {
			if (mPages[i].bitmap != null && i != current && (maxBitmapsInMemory < 2 || i != next) && (maxBitmapsInMemory < 3 || i != previous)) {
				mPages[i].bitmap.recycle();
				mPages[i].bitmap = null;
				
				Log.d("ComicsReader", "Recycle page " + String.valueOf(i));
			}
		}

		// save unused buffers to disk to free memory
		int start;
		int end;

		if (next < current) {
			start = current + 2;
			end = mLastBufferPageNumber;

			// update last buffer page
			mLastBufferPageNumber = Math.min(current + 1, numPages - 1);
		} else {
			start = mFirstBufferPageNumber;
			end = current - 2;

			// update first buffer page
			mFirstBufferPageNumber = Math.max(current - 1, 0);
		}

		// fix bounding wrong values
		if (start >= numPages) start = numPages - 1;
		if (end < 0) end = 0;

		for(int i = start; i <= end; ++i) {
			mPages[i].saveBufferToCache();
		}
		
		// load new buffers to speed up loading
		if (next < current) {
			start = current - (maxBuffersInMemory - 2);
			end = current + 1;
		} else {
			start = current - 1;
			end = current + (maxBuffersInMemory - 2);
		}
		
		// fix bounding wrong values
		if (start < 0) start = 0;
		if (end >= numPages) end = numPages - 1;

		for(int i = start; i <= end; ++i) {
			if (!AlbumPage.sAbortLoading) updateBuffer(i);
		}
		
		AlbumPage.sAbortLoading = false;
	}

	protected void updateBuffer(int page) {
		if (!mPages[page].loadBufferFromCache()) {
			mPages[page].buffer = getBytes(page);
			
			Log.d("ComicsReader", "Loaded buffer for page " + String.valueOf(page));
		} else {
			Log.d("ComicsReader", "Buffer already in memory for page " + String.valueOf(page));
		}

		if (page < mFirstBufferPageNumber) mFirstBufferPageNumber = page;
		if (page > mLastBufferPageNumber) mLastBufferPageNumber = page;
	}

	public boolean updateThumbnail(int page) {
		updateBuffer(page);
		
		return mPages[page].updateThumbnail();
	}
	
	public boolean hasPageBitmap(int page) {
		return mPages[page].bitmap != null;
	}
	
	public Bitmap getPageThumbnail(int page) {
		return mPages[page].thumbnail;
	}

	public Bitmap getPageBitmap(int page) {
		return mPages[page].bitmap;
	}

	public int getPageWidth(int page) {
		return mPages[page].bitmapSize.dstWidth;
	}

	public int getPageHeight(int page) {
		return mPages[page].bitmapSize.dstHeight;
	}
	
	public void updatePagesSizes() {
		Log.d("ComicsReader", "updatePagesSizes");
		
		for(int i = 0; i < numPages; ++i) {
			if (mPages[i].bitmap != null) {
				mPages[i].bitmap.recycle();
				mPages[i].bitmap = null;
			}
			
			mPages[i].resetSize();
		}
	}
	
	public int getMemoryUsed() {
		int size = 0;

		for(int i = mFirstBufferPageNumber; i <= mLastBufferPageNumber; ++i) {
			size += mPages[i].getMemoryUsed();
		}
		
		return size;
	}
	
	public void debugMemory() {
		Runtime runtime = Runtime.getRuntime();
		int used = (int)runtime.totalMemory();
		int max = (int)runtime.maxMemory();
		
		Log.d("ComicsReader", "From page " + String.valueOf(mFirstBufferPageNumber) + " to " + String.valueOf(mLastBufferPageNumber) + " using " + String.valueOf(getMemoryUsed()) + " bytes (" + String.valueOf(used) + " on " + String.valueOf(max));
		
		for(int i = 0; i < numPages; ++i) {
			String buffer = mPages[i].buffer == null ? "0":String.valueOf(mPages[i].buffer.length);
			String bitmap = mPages[i].bitmap == null ? "0":String.valueOf(mPages[i].getMemoryUsed());
			Log.d("ComicsReader", "Page " + String.valueOf(i) + ": buffer " + buffer + ", bitmap " + bitmap);
		}
	}
}
