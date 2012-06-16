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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.util.Log;

public class Album {

	protected String mTitle;
	protected String mFilename;
	protected List<String> mFiles;
	protected int mNumPages = 0;
	protected int mCurrentPageNumber = 0;
	protected String mCurrentPageFilename;
	protected boolean mHighQuality = false;
	protected boolean mFitToScreen = true;
	protected int mScale = 1;
	protected int mMaxImagesInMemory = 0;
	protected File mCachePagesDir;

	final static String undefinedExtension = "";
	final static String undefinedMimeType = "";

	static final int ALBUM_TYPE_NONE = 0;
	static final int ALBUM_TYPE_CBZ = 1;
	static final int ALBUM_TYPE_CBR = 2;
	static final int ALBUM_TYPE_FOLDER = 3;

	static Album createInstance(String filename) {
		if (CbzAlbum.isValid(filename)) {
			return new CbzAlbum();
		}

		if (CbrAlbum.isValid(filename)) {
			return new CbrAlbum();
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
		if (CbrAlbum.isValid(filename)) {
			return ALBUM_TYPE_CBR;
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
		if (CbrAlbum.isValid(filename)) return true;

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
		if (CbrAlbum.isValid(filename)) return true;

		// file is an image or a folder containing images
		if (FolderAlbum.isValid(filename)) return true;

		return false;
	}

	public static boolean askConfirm(String filename) {
		// file is a cbz
		if (CbzAlbum.askConfirm(filename)) return true;

		// file is a cbr
		if (CbrAlbum.askConfirm(filename)) return true;

		// file is an image or a folder containing images
		if (FolderAlbum.askConfirm(filename)) return true;
		
		return false;
	}
	
	static String getTitle(String filename) {
		switch(getType(filename))
		{
			case ALBUM_TYPE_CBZ: return CbzAlbum.getTitle(filename);
			case ALBUM_TYPE_CBR: return CbrAlbum.getTitle(filename);
			case ALBUM_TYPE_FOLDER: return FolderAlbum.getTitle(filename);
			default: break;
		}

		return null;
	}
	
	static String getExtension(String filename) {
		int pos = filename.lastIndexOf(".");

		if (pos == -1) return "";
		
		return filename.toLowerCase().substring(pos+1);
	}

	public static boolean isValidJpegImage(String filename) {
		String ext = Album.getExtension(filename);
			
		return "jpg".equals(ext) || "jpeg".equals(ext) || "jpe".equals(ext);
	}

	public static boolean isValidPngImage(String filename) {
		return "png".equals(Album.getExtension(filename));
	}

	public static boolean isValidImage(String filename) {
		return isValidJpegImage(filename) || isValidPngImage(filename);
	}
	
	public Album() {
	}

	public String getExtension() {
		return undefinedExtension;
	}

	public String getMimeType() {
		return undefinedMimeType;
	}
	
	public String getFilename() {
		return mFilename;
	}

	public String getTitle() {
		return mTitle;
	}
	
	public int getNumPages() {
		return mNumPages;
	}

	public int getCurrentPage() {
		return mCurrentPageNumber;
	}
	
	public int getMaxImagesInMemory() {
		return mMaxImagesInMemory;
	}
	
	boolean loadFiles() {
		return false;
	}
	
	public boolean open(String filename, boolean full) {
		if (filename == null) return false;

		mFilename = filename;
		mFiles = new ArrayList<String>();
		
		if (!loadFiles()) return false;

		mNumPages = mFiles.size();

		Collections.sort(mFiles);

		if (mCurrentPageFilename != null) {
			mCurrentPageNumber = mFiles.indexOf(mCurrentPageFilename);
			
			if (mCurrentPageNumber == -1) mCurrentPageNumber = 0;
		}
		
		if (full) {
			mCachePagesDir = new File(ComicsParameters.sPagesDirectory, ComicsParameters.md5(filename));
			mCachePagesDir.mkdirs();

			checkMaxImagesInMemory();
			
//			createPagesThumbnails();
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
		System.gc();
		
		ArrayList<Size> sizes = new ArrayList<Size>();
		
		// search for 3 biggest files
		for(int page = 0; page < mNumPages; ++page) {
			final BitmapFactory.Options options = getPageOptions(page);

			sizes.add(new Size(options.outWidth, options.outHeight));
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
	}
	
	public boolean createPagesThumbnails() {
		for(int page = 0; page < mNumPages; ++page) {
			Bitmap bitmap = createPageThumbnail(page);

			if (bitmap == null) continue;

			bitmap.recycle();
		}
		
		return true;
	}
	
	public Bitmap createPageThumbnail(int page) {
		File f = new File(mCachePagesDir, String.valueOf(page) + ".png");
		if (f.exists() && f.length() > 0) return null;

		
		
		// get a thumbnail for specified page
		Bitmap bitmap = getPage(page, -1, ComicsParameters.THUMBNAIL_HEIGHT, true);
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
		
		return ComicsParameters.resizeThumbnail(bitmap);
	}

	public Bitmap getPageThumbnailFromCache(int page) {
		return ComicsParameters.loadThumbnail(new File(mCachePagesDir, String.valueOf(page) + ".png"));
	}

	public void clearPagesCache() {
		// delete all pages thumbnails
		File[] files = mCachePagesDir.listFiles();
		for (File f : files) {
			f.delete();
		}
		
		// delete directory
		mCachePagesDir.delete();
	}
	
	public void close() {
		mFiles = null;
		mFilename = null; 
	}

	public void setHighQuality(boolean highQuality) {
		mHighQuality = highQuality;
	}

	public void setFitToScreen(boolean fitToScreen) {
		mFitToScreen = fitToScreen;
	}
	
	public void setScale(int scale) {
		mScale = scale;
	}
	
	private BitmapFactory.Options getPageOptions(int page) {
		if (page >= mNumPages || page < 0) return null;

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		options.inScaled = false;
		
		try {
			// open stream
			InputStream is = getInputStream(page);

			// get image size
			BitmapFactory.decodeStream(is, null, options);
			
			// close stream
			is.close();
		} catch(IOException e) {
			Log.e(ComicsParameters.APP_TAG, "IOException while reading size of page " + String.valueOf(page) + ": " + e.toString());
			options = null;
		} catch(Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception while reading size of page " + String.valueOf(page) + ": " + e.toString());
			options = null;
		}
		
		return options;
	}
	
	protected InputStream getInputStream(int page) throws IOException {
		return null;
	}
	
	/**
	 * Load a page from stream
	 * 
	 * @param page Page to process
	 * @param scale Scale to apply on page size
	 * @return Bitmap representing this page
	 */
	private Bitmap getPageRaw(int page, int scale) {
		if (page >= mNumPages || page < 0) return null;

		final BitmapFactory.Options options = new BitmapFactory.Options();

		// decode with inSampleSize
		options.inSampleSize = scale;
		options.inScaled = false;
//		options.inPurgeable = true; // if necessary purge pixels into disk

		if (mHighQuality) {
			options.inDither = false;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		} else {
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		}
		
		Bitmap bitmap = null;
		InputStream is = null;

		try {
			is = getInputStream(page);
			if (is == null) return null;
			bitmap = BitmapFactory.decodeStream(is, null, options);
		} catch(IllegalStateException e) {
			Log.e(ComicsParameters.APP_TAG, "Exception while reading file " + mFilename + ": " + e.toString());
			return null;
		} catch (IOException e) {
			Log.e(ComicsParameters.APP_TAG, "Exception while decoding bitmap " + mFiles.get(page) + ": " + e.toString());
			return null;
		} catch (OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "OutOfMemory while decoding bitmap " + mFiles.get(page) + ": " + e.toString());
			return null;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		if (mHighQuality && bitmap != null) {
			final int width = bitmap.getWidth();
			final int height = bitmap.getHeight();

			Bitmap newBitmap = null;

			try {
				// create a new bitmap with the right format
				newBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
			} catch (OutOfMemoryError e) {
				Log.e(ComicsParameters.APP_TAG, "OutOfMemory while decoding bitmap " + mFiles.get(page) + ": " + e.toString());
				return null;
			}

			newBitmap.eraseColor(0);

			// create a canvas to draw the old bitmap on new one
			final Canvas canvas = new Canvas(newBitmap);
			Rect r = new Rect();
			r.set(0, 0, width, height);
			canvas.drawBitmap(bitmap, null, r, null);

			// original bitmap is not needed anymore
			bitmap.recycle();

			return newBitmap;
		}
		
		return bitmap;
	}
	
	// class to manage in and out sizes of a page
	private class PageSize {
		int srcWidth;
		int srcHeight;
		int dstWidth;
		int dstHeight;
		int dstScale;
		boolean fitToScreen;
	}
	
	private void computePageSize(PageSize size) {
		int scaleX = 1;
		int scaleY = 1;
		
		// update horizontal scale
		if (size.dstWidth < 1) {
			scaleX = -size.dstWidth;
			size.dstWidth = -1;
		}

		// update vertical scale
		if (size.dstHeight < 1) {
			scaleY = -size.dstHeight;
			size.dstHeight = -1;
		}
		
		// computes original image size
		final int imageWidth = size.srcWidth / scaleX;
		final int imageHeight = size.srcHeight / scaleY;

		if (size.dstWidth == -1 && size.dstHeight == -1) {
			size.dstWidth = imageWidth;
			size.dstHeight = imageHeight;
		} else if (size.dstWidth == -1) {
			if (size.dstScale < 1) {
				size.dstScale = 1;

				// find the correct scale value, it should be a power of 2
				int tmpHeight = imageHeight;

				while(tmpHeight / 2 >= Math.max(size.dstHeight, ComicsParameters.MIN_SCALED_SIZE)) {
					tmpHeight /= 2;
					size.dstScale *= 2;
				}
			}

			if (!size.fitToScreen && imageHeight < size.dstHeight) {
				size.dstWidth = imageWidth;
				size.dstHeight = imageHeight;
			} else {
				size.dstWidth = size.dstHeight * imageWidth / imageHeight;
			}
		} else if (size.dstHeight == -1) {
			if (size.dstScale < 1) {
				size.dstScale = 1;

				// find the correct scale value, it should be the power of 2
				int tmpWidth = imageWidth;

				while(tmpWidth / 2 >= Math.max(size.dstWidth, ComicsParameters.MIN_SCALED_SIZE)) {
					tmpWidth /= 2;
					size.dstScale *= 2;
				}
			}

			if (!size.fitToScreen && imageWidth < size.dstWidth) {
				size.dstWidth = imageWidth;
				size.dstHeight = imageHeight;
			} else {
				size.dstHeight = size.dstWidth * imageHeight / imageWidth;
			}
		} else {
			if (size.dstScale < 1) {
				size.dstScale = 1;

				// find the correct scale value, it should be a power of 2
				int tmpWidth = imageWidth, tmpHeight = imageHeight;

				while((tmpWidth / 2 >= Math.max(size.dstWidth, ComicsParameters.MIN_SCALED_SIZE)) && (tmpHeight / 2 >= Math.max(size.dstHeight, ComicsParameters.MIN_SCALED_SIZE))) {
					tmpWidth /= 2;
					tmpHeight /= 2;
					size.dstScale *= 2;
				}
			}

			// keep aspect ratio
			int newHeight = size.dstWidth * imageHeight / imageWidth;
				
			if (newHeight > size.dstHeight) {
				size.dstWidth = size.dstHeight * imageWidth / imageHeight;
			} else {
				size.dstHeight = newHeight;
			}
		}
	}

	public Bitmap getPage(int page, int width, int height, boolean thumbnail) {
		final BitmapFactory.Options options = getPageOptions(page);
		
		if (options == null) {
			return null;
		}
		
		final PageSize size = new PageSize();
		size.srcWidth = options.outWidth;
		size.srcHeight = options.outHeight;
		size.dstWidth = width;
		size.dstHeight = height;
		size.dstScale = thumbnail ? 0:mScale;
		size.fitToScreen = thumbnail ? true:mFitToScreen;

		// compute new size based on aspect ratio and scales
		computePageSize(size);

		final Bitmap bitmapRaw = getPageRaw(page, size.dstScale);

		if (bitmapRaw == null) return null;

		Bitmap bitmap = null;
		
		try {
			// good quality resize
			bitmap = Bitmap.createScaledBitmap(bitmapRaw, size.dstWidth, size.dstHeight, true);
			bitmapRaw.recycle();
		} catch(OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "Out of memory while creating scaled bitmap");
			bitmap = null;

			bitmapRaw.recycle();

			return null;
		} catch (Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception: " + e);
			e.printStackTrace();
			bitmap = null;
		}

		return bitmap;
	}
	
	public Bitmap getDoublePage(int page, int width, int height) {
		// read sizes of 2 pages
		final BitmapFactory.Options options1 = getPageOptions(page);
		final BitmapFactory.Options options2 = getPageOptions(page+1);

		if (options1 == null || options2 == null) {
			return null;
		}

		PageSize size1 = new PageSize();
		size1.srcWidth = options1.outWidth;
		size1.srcHeight = options1.outHeight;
		size1.dstWidth = width;
		size1.dstHeight = height;
		size1.dstScale = mScale;
		size1.fitToScreen = mFitToScreen;

		PageSize size2 = new PageSize();
		size2.srcWidth = options2.outWidth;
		size2.srcHeight = options2.outHeight;
		size2.dstWidth = width;
		size2.dstHeight = height;
		size2.dstScale = mScale;
		size2.fitToScreen = mFitToScreen;

		// compute new sizes based on aspect ratio and scales
		computePageSize(size1);
		computePageSize(size2);

		// create a new bitmap with the size of the 2 bitmaps
		Bitmap bitmap = Bitmap.createBitmap(size1.dstWidth + size2.dstWidth, Math.max(size1.dstHeight, size2.dstHeight), mHighQuality ? Bitmap.Config.ARGB_8888:Bitmap.Config.RGB_565);

		try {
			Canvas canvas = new Canvas(bitmap);

			// get first page
			Bitmap tmpRaw = getPageRaw(page, size1.dstScale);

			if (tmpRaw == null) return null;

			// good quality resize
			Bitmap tmp = Bitmap.createScaledBitmap(tmpRaw, size1.dstWidth, size1.dstHeight, true);
			tmpRaw.recycle();

			canvas.drawBitmap(tmp, 0, 0, null);
			tmp.recycle();

			// get second page
			tmpRaw = getPageRaw(page+1, size2.dstScale);

			// good quality resize
			tmp = Bitmap.createScaledBitmap(tmpRaw, size2.dstWidth, size2.dstHeight, true);
			tmpRaw.recycle();

			canvas.drawBitmap(tmp, size1.dstWidth, 0, null);
			tmp.recycle();
		} catch(OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "Out of memory while assembling a double page");

			return null;
		} catch (Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception: " + e.getMessage());
			e.printStackTrace();
		}
		
		return bitmap;
	}
}
