/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2016 Cedric OCHS
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class AlbumPage {
	public Bitmap bitmap;
	public Bitmap thumbnail;
	public byte [] buffer;
	public Size bitmapSize;
	public Size cachedBitmapSize;
	public Size thumbnailSize;

	protected int mPage;
	protected String mFilename;
	protected String mCacheFilename;
	protected File mBufferCacheFile;

	static boolean sAbortLoading = false;
	
	// class to manage in and out sizes of a page
	public class Size {
		int srcWidth;
		int srcHeight;
		int dstWidth = -1;
		int dstHeight = -1;
		int dstScale = 0;
		boolean fitToScreen = false;
	}
	
	public AlbumPage(int page, String filename) {
		mPage = page;
		mFilename = filename;
	}

	public void reset() {
		// remove cached buffer if present
		deleteCache();

		// recycle bitmap
		if (bitmap != null) {
			bitmap.recycle();
			bitmap = null;
		}

		// recycle thumbnail
		if (thumbnail != null) {
			thumbnail.recycle();
			thumbnail = null;
		}
		
		// free buffer
		buffer = null;

		// uninitialize other variables
		bitmapSize = null;
		cachedBitmapSize = null;
		thumbnailSize = null;

		mFilename = null;
		mCacheFilename = null;
		mBufferCacheFile = null;
	}

	/**
	 * Load a page from stream
	 * 
	 * @param scale Scale to apply on page size
	 * @return Bitmap representing this page
	 */
	public Bitmap getPageRaw(int scale) {
		if (buffer == null) return null;

		if (sAbortLoading) {
			sAbortLoading = false;
			return null;
		}

		final BitmapFactory.Options options = new BitmapFactory.Options();

		// decode with inSampleSize
		options.inSampleSize = scale;
		options.inScaled = false;
//		options.inPurgeable = true; // if necessary purge pixels into disk

		if (AlbumParameters.highQuality) {
			options.inDither = false;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		} else {
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		}

		Bitmap b = null;

		try {
			// get bitmap from buffer
			b = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, options);

			if (b == null) {
				Log.e(ComicsParameters.APP_TAG, "BitmapFactory.decodeByteArray returned null for " + mFilename + " size = " + String.valueOf(buffer.length));

				saveBufferToCache();

				Log.d(ComicsParameters.APP_TAG, "File " + mFilename + " saved in temporary directory under namme " + mCacheFilename + " to check it");
			}
		} catch (OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "OutOfMemory while decoding bitmap " + mFilename + ": " + e.toString());
			return null;
		}

/*
		// TODO: check for Android version, 2.2 should need that
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
*/
		return b;
	}

	public void updateBitmapDstSize(int width, int height) {
		if (bitmapSize == null) return;
	
		// use some parameters from Album
		int scale = AlbumParameters.scale;
		boolean fitToScreen = AlbumParameters.fitToScreen;
		
		int scaleX = 1;
		int scaleY = 1;
		
		// update horizontal scale
		if (width < 1) {
			scaleX = -width;
			width = -1;
		}

		// update vertical scale
		if (height < 1) {
			scaleY = -height;
			height = -1;
		}
		
		// computes original image size
		final int imageWidth = bitmapSize.srcWidth / scaleX;
		final int imageHeight = bitmapSize.srcHeight / scaleY;

		if (width == -1 && height == -1) {
			width = imageWidth;
			height = imageHeight;
		} else if (width == -1) {
			if (scale < 1) {
				// find the correct scale value, it should be a power of 2
				scale = ComicsHelpers.findNearestPowerOfTwoScale(imageHeight, height);
			}

			if (!fitToScreen && imageHeight < height) {
				width = imageWidth;
				height = imageHeight;
			} else {
				width = height * imageWidth / imageHeight;
			}
		} else if (height == -1) {
			if (scale < 1) {
				// find the correct scale value, it should be the power of 2
				scale = ComicsHelpers.findNearestPowerOfTwoScale(imageWidth, width);
			}

			if (!fitToScreen && imageWidth < width) {
				width = imageWidth;
				height = imageHeight;
			} else {
				height = width * imageHeight / imageWidth;
			}
		} else {
			if (scale < 1) {
				scale = 1;

				// find the correct scale value, it should be a power of 2
				int tmpWidth = imageWidth, tmpHeight = imageHeight;

				while((tmpWidth / 2 >= Math.max(width, ComicsParameters.MIN_SCALED_SIZE)) && (tmpHeight / 2 >= Math.max(height, ComicsParameters.MIN_SCALED_SIZE))) {
					tmpWidth /= 2;
					tmpHeight /= 2;
					scale *= 2;
				}
			}

			// keep aspect ratio
			int newHeight = width * imageHeight / imageWidth;
				
			if (newHeight > height) {
				width = height * imageWidth / imageHeight;
			} else {
				height = newHeight;
			}
		}

		bitmapSize.dstWidth = width;
		bitmapSize.dstHeight = height;
		bitmapSize.dstScale = scale;
		bitmapSize.fitToScreen = fitToScreen;
	}

	public void updateThumbnailDstSize() {
		if (thumbnailSize.dstWidth > 0) return;

		thumbnailSize.dstHeight = ComicsParameters.THUMBNAIL_HEIGHT;

		// find the correct scale value, it should be a power of 2
		thumbnailSize.dstScale = ComicsHelpers.findNearestPowerOfTwoScale(thumbnailSize.srcHeight, thumbnailSize.dstHeight);
		thumbnailSize.dstWidth = thumbnailSize.dstHeight * thumbnailSize.srcWidth / thumbnailSize.srcHeight;
	}

	public boolean updateBitmap(int width, int height) {
		if (!updateSrcSize()) return false;

		boolean res = false;
		
		synchronized(bitmapSize) {
			// compute new size based on aspect ratio and scales
			updateBitmapDstSize(width, height);

			final Bitmap bitmapRaw = getPageRaw(bitmapSize.dstScale);

			if (bitmapRaw == null) return false;

			if (bitmapSize.srcWidth == bitmapSize.dstWidth * bitmapSize.dstScale && bitmapSize.srcHeight == bitmapSize.dstHeight * bitmapSize.dstScale) {
				bitmap = bitmapRaw;
				cachedBitmapSize = bitmapSize;

				return true;
			}

			try {
				// good quality resize
				bitmap = Bitmap.createScaledBitmap(bitmapRaw, bitmapSize.dstWidth, bitmapSize.dstHeight, true);
				cachedBitmapSize = bitmapSize;

				res = true;
			} catch(OutOfMemoryError e) {
				Log.e(ComicsParameters.APP_TAG, "Out of memory while creating scaled bitmap");
				e.printStackTrace();
			} catch (Exception e) {
				Log.e(ComicsParameters.APP_TAG, "Exception: " + e);
				e.printStackTrace();
			}

			if (bitmap != bitmapRaw) bitmapRaw.recycle();
		}

		return res;
	}

	public boolean updateThumbnail() {
		if (thumbnail != null) return true;
		
		if (!updateSrcSize()) return false;
		
		// compute new size based on aspect ratio and scales
		updateThumbnailDstSize();

		final Bitmap bitmapRaw = getPageRaw(thumbnailSize.dstScale);

		if (bitmapRaw == null) return false;

		if (thumbnailSize.srcWidth == thumbnailSize.dstWidth * thumbnailSize.dstScale && thumbnailSize.srcHeight == thumbnailSize.dstHeight * thumbnailSize.dstScale) {
			thumbnail = bitmapRaw;
			
			return true;
		}

		boolean res = false;

		try {
			// good quality resize
			thumbnail = Bitmap.createScaledBitmap(bitmapRaw, thumbnailSize.dstWidth, thumbnailSize.dstHeight, true);
		
			res = true;
		} catch(OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "Out of memory while creating scaled bitmap");
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception: " + e);
			e.printStackTrace();
		}

		if (bitmapRaw != thumbnail) bitmapRaw.recycle();

		return res;
	}

	public boolean updateSrcSize() {
		// size already loaded
		if (bitmapSize != null) return true;
		
		if (sAbortLoading) {
			sAbortLoading = false;
			return false;
		}

		final BitmapFactory.Options options = new BitmapFactory.Options();

		options.inJustDecodeBounds = true;
		options.inScaled = false;
		
		try {
			// get image size
			BitmapFactory.decodeByteArray(buffer, 0, buffer.length, options);
		} catch(Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Exception while reading size of page " + String.valueOf(mPage) + ": " + e.toString());
			return false;
		}

		bitmapSize = new Size();
		bitmapSize.srcWidth = options.outWidth;
		bitmapSize.srcHeight = options.outHeight;
		
		thumbnailSize = new Size();
		thumbnailSize.srcWidth = options.outWidth;
		thumbnailSize.srcHeight = options.outHeight;

		return true;
	}
	
	public void resetSize() {
		if (bitmapSize == null) return;
		
		synchronized(bitmapSize) {
			bitmapSize.dstWidth = -1;
			bitmapSize.dstHeight = -1;
			bitmapSize.dstScale = 0;
			bitmapSize.fitToScreen = false;
		}
	}
	
	public void updateCacheFilename() {
		if (mCacheFilename == null && mFilename != null) {
			mCacheFilename = ComicsHelpers.md5(mFilename);

			mBufferCacheFile = new File(ComicsParameters.sCacheCurrentAlbumDirectory, mCacheFilename);
		}
	}

	
	public boolean loadBufferFromCache() {
		if (buffer != null) return true;

		updateCacheFilename();
		
		if (!mBufferCacheFile.exists() || mBufferCacheFile.length() < 10) return false;

		try {
			buffer = new byte[(int)mBufferCacheFile.length()];
		} catch (OutOfMemoryError e) {
			Log.e(ComicsParameters.APP_TAG, "OutOfMemoryError while allocating a buffer of " + String.valueOf(mBufferCacheFile.length()) + " bytes for page " + String.valueOf(mPage));
			return false;
		}

		return ComicsHelpers.loadFileToBuffer(mBufferCacheFile, buffer);
	}
	
	public boolean saveBufferToCache() {
		if (buffer == null) return false;
		
		updateCacheFilename();

		boolean res = true;

		if (!mBufferCacheFile.exists()) {
			res = ComicsHelpers.saveBufferToFile(buffer, mBufferCacheFile);
		}

		if (res) buffer = null;

		return res;
	}

	public boolean deleteCache() {
		updateCacheFilename();

		boolean res = true;

		if (mBufferCacheFile.exists()) {
			res = mBufferCacheFile.delete();
		}

		return res;
	}

	public int getMemoryUsed() {
		int size = 0;

		if (buffer != null) size += buffer.length;
		if (bitmap != null) size += bitmap.getRowBytes() * bitmap.getHeight();
		if (thumbnail != null) size += thumbnail.getRowBytes() * thumbnail.getHeight();
		
		return size;
	}
}
