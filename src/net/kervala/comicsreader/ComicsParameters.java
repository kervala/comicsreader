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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

public class ComicsParameters {
	static final String APP_TAG = "ComicsReader";
	static final int BUFFER_SIZE = 65536;
	static final int MAX_IMAGES_IN_MEMORY = 3;
	static final int MIN_SCALED_SIZE = 256;
	static final int THUMBNAIL_HEIGHT = 96;
	static final int TIME_OUT = 60000;

	static BitmapDrawable sPlaceholderDrawable;
	static BitmapDrawable sFolderChildDrawable;
	static BitmapDrawable sFolderParentDrawable;

	static File sRootDirectory;
	static File sExternalDirectory;
	static File sCacheDirectory;
	static File sCoversDirectory;
	static File sPagesDirectory;

	static int sScreenDensity = 0;
	static int sBitmapDensity = 0;
	static boolean sLarge = false;
	static int sThumbnailRescaledHeight;

	static String sPackageName;
	static String sPackageVersion;
	static int sPackageVersionCode;
	static String sCurrentOpenAlbum;

	static int sScreenWidth = 0;
	static int sScreenHeight = 0;

	static private int sReferences = 0;

	public static void init(Context context) {
		if (sReferences < 1) {
			initPackageInfo(context);
			initDirectories();
			initDensity(context);
			loadBitmaps(context);
		}

		++sReferences;
	}
	
	public static void release() {
		--sReferences;

		if (sReferences < 1) releaseBitmaps();
	}
	
	public static void initPackageInfo(Context context) {
		// Try to load the a package matching the name of our own package
		try {
			PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
			sPackageVersion = pInfo.versionName;
			sPackageName = pInfo.packageName;
			sPackageVersionCode = pInfo.versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public static void initDensity(Context context) {
		final WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

		final DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);
				
		int size = context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;

		sScreenDensity = metrics.densityDpi;
		sLarge = size >= Configuration.SCREENLAYOUT_SIZE_LARGE;

		sThumbnailRescaledHeight = sLarge ? THUMBNAIL_HEIGHT:THUMBNAIL_HEIGHT * sScreenDensity / 240;
		sBitmapDensity = sLarge ? sScreenDensity:240;
	}

	public static void loadBitmaps(Context context) {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inDensity = sBitmapDensity;
		options.inTargetDensity = sScreenDensity;
		options.inDither = false;

		if (sPlaceholderDrawable == null) {
			options.inPreferredConfig = Bitmap.Config.RGB_565;
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.placeholder, options);
			sPlaceholderDrawable = new BitmapDrawable(bitmap);
			sPlaceholderDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			sPlaceholderDrawable.setTargetDensity(bitmap.getDensity());
		}

		if (sFolderChildDrawable == null) {
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_folder, options);
			sFolderChildDrawable = new BitmapDrawable(bitmap);
			sFolderChildDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			sFolderChildDrawable.setTargetDensity(bitmap.getDensity());
		}

		if (sFolderParentDrawable == null) {
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_folder_open, options);
			sFolderParentDrawable = new BitmapDrawable(bitmap);
			sFolderParentDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			sFolderParentDrawable.setTargetDensity(bitmap.getDensity());
		}
	}
	
	public static void releaseBitmaps() {
		if (sPlaceholderDrawable != null) {
			sPlaceholderDrawable.getBitmap().recycle();
			sPlaceholderDrawable = null;
		}

		if (sFolderChildDrawable != null) {
			sFolderChildDrawable.getBitmap().recycle();
			sFolderChildDrawable = null;
		}

		if (sFolderParentDrawable != null) {
			sFolderParentDrawable.getBitmap().recycle();
			sFolderParentDrawable = null;
		}
	}
	
	static void initDirectories() {
		sExternalDirectory = Environment.getExternalStorageDirectory();
		sRootDirectory = new File("/");

		sCacheDirectory = new File(sExternalDirectory, "/Android/data/" + sPackageName + "/cache");

		// create root cache directory
		if (!sCacheDirectory.exists()) sCacheDirectory.mkdirs();

		sCoversDirectory = new File(sCacheDirectory, "covers");

		// create covers directory
		if (!sCoversDirectory.exists()) sCoversDirectory.mkdirs();

		sPagesDirectory = new File(sCacheDirectory, "pages");

		// create pages directory
		if (!sPagesDirectory.exists()) sPagesDirectory.mkdirs();
	}
	
	public static int getDownloadedSize() {
		final File [] files = sCacheDirectory.listFiles();
		
		int size = 0;
		
		for(File file: files) {
			size += file.length();
		}
		
		return size;
	}
	
	public static void clearDownloadedAlbumsCache() {
		final File [] files = sCacheDirectory.listFiles();
		if (files != null) {
			for(File file: files) {
				// don't delete current open album
				if (!file.getAbsolutePath().equals(ComicsParameters.sCurrentOpenAlbum)) {
					file.delete();
				}
			}
		}
	}

	public static void clearThumbnailsCache() {
		clearAllCoversCache();
		clearAllPagesCache();
	}

	public static void clearAllCoversCache() {
		final File[] files = ComicsParameters.sCoversDirectory.listFiles();
		if (files != null) {
			for (File f : files) {
				f.delete();
			}
		}
	}

	public static void clearAllPagesCache() {
		// delete all albums pages thumbnails
		final File[] dirs = ComicsParameters.sPagesDirectory.listFiles();
		if (dirs != null) {
			for (File dir : dirs) {
				// delete all pages thumbnails
				File[] files = dir.listFiles();

				for (File file : files) {
					file.delete();
				}

				// delete directory
				dir.delete();
			}
		}
	}

	static Bitmap resizeThumbnail(Bitmap bitmap) {
		if (bitmap == null) return null;

		if (bitmap.getHeight() == ComicsParameters.sThumbnailRescaledHeight) return bitmap;

		final Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * ComicsParameters.sThumbnailRescaledHeight / bitmap.getHeight(), ComicsParameters.sThumbnailRescaledHeight, true);
		bitmap.recycle();
		return newBitmap;
	}

	static Bitmap cropThumbnail(Bitmap bitmap) {
		if (bitmap == null) return null;

		if (bitmap.getWidth() <= ComicsParameters.THUMBNAIL_HEIGHT && bitmap.getHeight() <= ComicsParameters.THUMBNAIL_HEIGHT) return bitmap;

		final Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, Math.min(ComicsParameters.THUMBNAIL_HEIGHT, bitmap.getWidth()), Math.min(ComicsParameters.THUMBNAIL_HEIGHT, bitmap.getHeight()));
		bitmap.recycle();

		return croppedBitmap;
	}

	static Bitmap createThumbnail(String filename) {
		Album album = Album.createInstance(filename);
		
		Bitmap bitmap = null;
		
		if (album.open(filename, false)) {
			// get a thumbnail for first page
			bitmap = cropThumbnail(resizeThumbnail(album.getPage(album.getCurrentPage(), -1, ComicsParameters.THUMBNAIL_HEIGHT, true)));
		
			album.close();
		}
		
		if (bitmap == null) return null;

		// if thumbnail can't be saved, continue
		try {
			File f = new File(ComicsParameters.sCoversDirectory, md5(filename) + ".png");
			OutputStream os = new FileOutputStream(f);
			bitmap.compress(Bitmap.CompressFormat.PNG, 70, os);
			os.close();
		} catch (FileNotFoundException e) {
			Log.e(APP_TAG, filename + " not found");
		} catch (IOException e) {
			Log.e(APP_TAG, e.getMessage());
		} catch (Error e) {
			Log.e(APP_TAG, "Error: " + e.getMessage());
		} catch (Exception e) {
			Log.e(APP_TAG, "Exception: " + e.getMessage());
		}
		
		return bitmap;
	}
	
	public static String md5(String str) {
		try {
			// Create MD5 Hash
			final MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(str.getBytes());
			final byte messageDigest[] = digest.digest();

			// Create Hex String
			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < messageDigest.length; i++) {
				String h = Integer.toHexString(0xFF & messageDigest[i]);
				while (h.length() < 2) h = "0" + h;
				hexString.append(h);
			}

			return hexString.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return "";
	}
	
	static Bitmap loadThumbnail(File f) {
		Bitmap bitmap = null;
		
		if (f.exists() && f.length() > 0) {
			// load cached file
			try {
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inScaled = true;
				options.inJustDecodeBounds = true;
				options.inDensity = ComicsParameters.sBitmapDensity;
				options.inTargetDensity = ComicsParameters.sScreenDensity;
				options.inScreenDensity = ComicsParameters.sScreenDensity;
				options.inPreferredConfig = Bitmap.Config.RGB_565;

				// get image size
				final BufferedInputStream is = new BufferedInputStream(new FileInputStream(f), ComicsParameters.BUFFER_SIZE);
				BitmapFactory.decodeStream(is, null, options);

				// don't load image if exceed maximum size or can't be decoded
				if (options.outHeight <= ComicsParameters.THUMBNAIL_HEIGHT && options.outHeight > 0 && options.outWidth <= ComicsParameters.THUMBNAIL_HEIGHT && options.outWidth > 0) {
					options.inJustDecodeBounds = false;

					is.reset();
					bitmap = resizeThumbnail(BitmapFactory.decodeStream(is, null, options));
					is.close();
				} else {
					is.close();
					f.delete();
				}
			} catch (FileNotFoundException e) {
				Log.e(APP_TAG, f + " not found");
			} catch (Error e) {
				Log.e(APP_TAG, "Error: " + e.getMessage());
			} catch (Exception e) {
				Log.e(APP_TAG, "Exception: " + e.getMessage());
			}
		}

		return bitmap;
	}
	
	static Bitmap getThumbnailFromCache(String filename) {
		String file;
		
		if (filename.startsWith("http://")) {
			int pos = filename.lastIndexOf('/');
			
			if (pos == -1) {
				file = filename;
			} else {
				file = filename.substring(pos + 1);
			}
		} else {
			file = md5(filename) + ".png";			
		}

		return loadThumbnail(new File(ComicsParameters.sCoversDirectory, file));
	}

	static boolean downloadThumbnailFromUrl(String url) {
		boolean res = false;
		String filename;

		int pos = url.lastIndexOf('/');
		
		if (pos == -1) {
			filename = url;
		} else {
			filename = url.substring(pos + 1);
		}
		
		File f = new File(ComicsParameters.sCoversDirectory, filename);

		HttpURLConnection urlConnection = null;
		
		try {
			URL u = new URL(url);

			// create the new connection
			ComicsAuthenticator.sInstance.reset();
			
			urlConnection = (HttpURLConnection)u.openConnection();
			urlConnection.setConnectTimeout(TIME_OUT);
			urlConnection.setReadTimeout(TIME_OUT);
			
			// download the file
			final InputStream input = new BufferedInputStream(urlConnection.getInputStream(), ComicsParameters.BUFFER_SIZE);
			final OutputStream output = new FileOutputStream(f);

			int count = 0;
			byte data[] = new byte[ComicsParameters.BUFFER_SIZE];
				
			while ((count = input.read(data)) != -1) {
				output.write(data, 0, count);
			}

			output.flush();
			output.close();
			input.close();

			res = true;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (ProtocolException e) {
			e.printStackTrace();
		} catch (SocketTimeoutException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// no space left on device
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		
		return res;
	}
}
