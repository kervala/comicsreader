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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.WindowManager;

public class ComicsParameters {
	static final String APP_TAG = "ComicsReader";
	static final int BUFFER_SIZE = 65536;
	static final int MAX_IMAGES_IN_MEMORY = 3;
	static final int MIN_SCALED_SIZE = 256;
	static final int THUMBNAIL_HEIGHT = 96;
	static final int TIME_OUT = 5000;
	static final int REQUEST_READ_EXTERNAL_PERMISSION = 1;

	static BitmapDrawable sPlaceholderDrawable;
	static BitmapDrawable sFolderChildDrawable;
	static BitmapDrawable sFolderParentDrawable;

	static File sRootDirectory;
	static File sExternalDirectory;
	static File sCacheDirectory;
	static File sCacheCurrentAlbumDirectory;
	static File sCoversDirectory;
	static File sPagesDirectory;

	static int sScreenDensity = 0;
	static int sBitmapDensity = 0;
	static boolean sLarge = false;
	static boolean sIsTablet = false;
	static boolean sHasMenuKey = false;
	static boolean sIsCyanogenMod = false;
	static String sDeviceType;
	static int sThumbnailRescaledHeight;

	static String sPackageName;
	static String sPackageVersion;
	static int sPackageVersionCode;
	static String sCurrentOpenAlbum;

	static int sScreenWidth = 0;
	static int sScreenHeight = 0;
	static boolean sFullScreenNoticeDisplayed = false;

	static private int sReferences = 0;

	public static void init(Context context) {
		if (sReferences < 1) {
			initPackageInfo(context);
			initDirectories(context);
			initDensity(context);
			loadBitmaps(context);
		}

		++sReferences;
	}

	static void release() {
		--sReferences;

		if (sReferences < 1) {
			releaseBitmaps();
		}
	}

	private static void initPackageInfo(Context context) {
		// Try to load the a package matching the name of our own package
		try {
			PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
			sPackageVersion = pInfo.versionName;
			sPackageName = pInfo.packageName;
			sPackageVersionCode = pInfo.versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		PackageManager pm = context.getPackageManager();

		try {
			Class<?> pmClass = pm.getClass();

			Method hasSystemFeature = pmClass.getMethod("hasSystemFeature", String.class);

			sIsCyanogenMod = (Boolean)hasSystemFeature.invoke(pm, "com.cyanogenmod.android");
		} catch (NoSuchMethodException e) {
			Log.e(ComicsParameters.APP_TAG, "Method hasSystemFeature doesn't exist");
		} catch (Exception e) {
			Log.e(ComicsParameters.APP_TAG, "Error when getting method hasSystemFeature");
		}
	}
	
	private static void initDensity(Context context) {
		final WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

		final DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);
				
		int size = context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;

		sScreenDensity = metrics.densityDpi;
		sLarge = size >= Configuration.SCREENLAYOUT_SIZE_LARGE;

		sThumbnailRescaledHeight = sLarge ? THUMBNAIL_HEIGHT:THUMBNAIL_HEIGHT * sScreenDensity / 240;
		sBitmapDensity = sLarge ? sScreenDensity:240;
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			sHasMenuKey = true;
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			sHasMenuKey = false;
		} else {
			ViewConfiguration config = ViewConfiguration.get(context);

			try {
				Class<?> configClass = config.getClass();

				Method hasPermanentMenuKey = configClass.getMethod("hasPermanentMenuKey");

				sHasMenuKey = (Boolean)hasPermanentMenuKey.invoke(config);
			} catch (NoSuchMethodException e) {
				Log.e(ComicsParameters.APP_TAG, "Method hasPermanentMenuKey doesn't exist");
			} catch (Exception e) {
				Log.e(ComicsParameters.APP_TAG, "Error when getting method hasPermanentMenuKey");
			}
		}
	}

	static void initTablet() {
		// already defined
		if (sDeviceType != null) return;

		sDeviceType = System.getProperty("ro.build.characteristics", "unknown");

		if ("unknown".equals(sDeviceType)) {
			File buildPropFile = new File("/system/build.prop");

			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(buildPropFile)));

				String line;

				while((line = in.readLine()) != null) {
					if (line.startsWith("ro.build.characteristics")) {
						String [] tokens = line.split("=");

						if (tokens.length > 1) {
							tokens = tokens[1].split(",");

							for (String token : tokens) {
								if ("tablet".equalsIgnoreCase(token)) {
									sDeviceType = "tablet";
									break;
								} else if ("phone".equalsIgnoreCase(token)) {
									sDeviceType = "phone";
									break;
								}
							}
						}

						break;
					}
				}

				in.close();
			} catch (IOException e) {
				Log.e(ComicsParameters.APP_TAG, "Error when reading /system/build.prop");
			}
		}

		sIsTablet = "tablet".equalsIgnoreCase(sDeviceType);
	}

	private static void loadBitmaps(Context context) {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inDensity = sBitmapDensity;
		options.inTargetDensity = sScreenDensity;
		options.inDither = false;

		if (sPlaceholderDrawable == null) {
			options.inPreferredConfig = Bitmap.Config.RGB_565;
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.placeholder, options);
			sPlaceholderDrawable = new BitmapDrawable(null, bitmap);
			sPlaceholderDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			sPlaceholderDrawable.setTargetDensity(bitmap.getDensity());
		}

		if (sFolderChildDrawable == null) {
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_folder, options);
			sFolderChildDrawable = new BitmapDrawable(null, bitmap);
			sFolderChildDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			sFolderChildDrawable.setTargetDensity(bitmap.getDensity());
		}

		if (sFolderParentDrawable == null) {
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_folder_open, options);
			sFolderParentDrawable = new BitmapDrawable(null, bitmap);
			sFolderParentDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
			sFolderParentDrawable.setTargetDensity(bitmap.getDensity());
		}
	}
	
	private static void releaseBitmaps() {
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



	private static boolean initDirectories(Context context) {
		Activity act = (Activity) context;

		sExternalDirectory = Environment.getExternalStorageDirectory();
		sRootDirectory = Environment.getRootDirectory();

		if (sExternalDirectory != null) {
			if (!sExternalDirectory.canRead()) {
				Log.e(ComicsParameters.APP_TAG, "Unable to read external storage");
			}

			if (!sExternalDirectory.canWrite()) {
				Log.e(ComicsParameters.APP_TAG, "Unable to write external storage");
			}
		}

		sCacheDirectory = act.getExternalCacheDir() != null ? act.getExternalCacheDir():new File(sExternalDirectory, "/Android/data/" + sPackageName + "/cache");

		// create root cache directory
		if (!sCacheDirectory.exists() && !sCacheDirectory.mkdirs()) {
			Log.e(APP_TAG, "Unable to create " + sCacheDirectory + " directory");
		}
		
		sCacheCurrentAlbumDirectory = new File(sCacheDirectory, "current");

		// create current album cache directory
		if (!sCacheCurrentAlbumDirectory.exists() && !sCacheCurrentAlbumDirectory.mkdirs()) {
			Log.e(APP_TAG, "Unable to create " + sCacheCurrentAlbumDirectory + " directory");
		}

		sCoversDirectory = new File(sCacheDirectory, "covers");

		// create covers directory
		if (!sCoversDirectory.exists() && !sCoversDirectory.mkdirs()) {
			Log.e(APP_TAG, "Unable to create " + sCoversDirectory + "directory");
		}

		sPagesDirectory = new File(sCacheDirectory, "pages");

		// create pages directory
		if (!sPagesDirectory.exists() && !sPagesDirectory.mkdirs()) {
			Log.e(APP_TAG, "Unable to create " + sPagesDirectory + "directory");
		}

		return true;
	}

	static int getDownloadedSize() {
		final File [] files = ComicsParameters.sCacheDirectory.listFiles();
		
		int size = 0;
		
		if (files != null) {
			for(File file: files) {
				size += file.length();
			}
		}
		
		return size;
	}
	
	static void clearDownloadedAlbumsCache() {
		final File [] files = ComicsParameters.sCacheDirectory.listFiles();
		if (files != null) {
			for(File file: files) {
				// don't delete current open album
				if (!file.getAbsolutePath().equals(ComicsParameters.sCurrentOpenAlbum)) {
					if (!file.delete()) {
						Log.e(APP_TAG, "Unable to delete " + file.getAbsolutePath());
					}
				}
			}
		}
	}

	static void clearThumbnailsCache() {
		clearAllCoversCache();
		clearAllPagesCache();
	}

	private static void clearAllCoversCache() {
		final File[] files = ComicsParameters.sCoversDirectory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (!file.delete()) {
					Log.e(APP_TAG, "Unable to delete " + file.getAbsolutePath());
				}
			}
		}
	}

	private static void clearAllPagesCache() {
		// delete all albums pages thumbnails
		final File[] dirs = ComicsParameters.sPagesDirectory.listFiles();
		if (dirs != null) {
			for (File dir : dirs) {
				// delete all pages thumbnails
				File[] files = dir.listFiles();

				if (files != null) {
					for (File file : files) {
						if (!file.delete()) {
							Log.e(APP_TAG, "Unable to delete " + file.getAbsolutePath());
						}
					}
				}

				// delete directory
				if (dir.delete()) {
					Log.e(APP_TAG, "Unable to delete " + dir.getAbsolutePath());
				}
			}
		}
	}

	static void clearCurrentAlbumDirectory() {
		// delete all cached pages
		File[] files = ComicsParameters.sCacheCurrentAlbumDirectory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (!file.delete()) {
					Log.e(APP_TAG, "Unable to delete " + file.getAbsolutePath());
				}
			}
		}
	}
}
