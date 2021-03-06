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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

class ComicsHelpers {
	
	static Bitmap resizeThumbnail(Bitmap bitmap) {
		if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) return null;

		final Bitmap.Config config = bitmap.getConfig();

		if (bitmap.getHeight() == ComicsParameters.sThumbnailRescaledHeight) return bitmap.copy(config != null ? config:Config.RGB_565, true);

		final Bitmap newBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * ComicsParameters.sThumbnailRescaledHeight / bitmap.getHeight(), ComicsParameters.sThumbnailRescaledHeight, true);
		if (bitmap != newBitmap) bitmap.recycle();

		return newBitmap;
	}

	static Bitmap cropThumbnail(Bitmap bitmap) {
		if (bitmap == null) return null;

		final Bitmap.Config config = bitmap.getConfig();

		if (bitmap.getWidth() <= ComicsParameters.THUMBNAIL_HEIGHT && bitmap.getHeight() <= ComicsParameters.THUMBNAIL_HEIGHT) return bitmap.copy(config != null ? config:Config.RGB_565, true);

		final Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, Math.min(ComicsParameters.THUMBNAIL_HEIGHT, bitmap.getWidth()), Math.min(ComicsParameters.THUMBNAIL_HEIGHT, bitmap.getHeight()));
		if (bitmap != croppedBitmap) bitmap.recycle();

		return croppedBitmap;
	}

	static String md5(String str) {
		try {
			// Create MD5 Hash
			final MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
			digest.update(str.getBytes());
			final byte messageDigest[] = digest.digest();

			// Create Hex String
			StringBuilder hexString = new StringBuilder();
			for (byte aMessageDigest : messageDigest) {
				String h = Integer.toHexString(0xFF & aMessageDigest);
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
				BufferedInputStream is = new BufferedInputStream(new FileInputStream(f), ComicsParameters.BUFFER_SIZE);
				BitmapFactory.decodeStream(is, null, options);

				// don't load image if exceed maximum size or can't be decoded
				if (options.outHeight <= ComicsParameters.THUMBNAIL_HEIGHT && options.outHeight > 0 && options.outWidth <= ComicsParameters.THUMBNAIL_HEIGHT && options.outWidth > 0) {
					options.inJustDecodeBounds = false;

					// there is a bug with Android 4.4+
					try {
						is.reset();
					} catch(IOException e) {
						// we have to reopen the file
						is.close();
						is = new BufferedInputStream(new FileInputStream(f), ComicsParameters.BUFFER_SIZE);
					}
					bitmap = resizeThumbnail(BitmapFactory.decodeStream(is, null, options));
					is.close();
				} else {
					is.close();
					if (!f.delete()) {
						Log.e(ComicsParameters.APP_TAG, "Unable to delete file: " + f.getAbsolutePath());
					}
				}
			} catch (FileNotFoundException e) {
				Log.e(ComicsParameters.APP_TAG, f + " not found");
			} catch (Error e) {
				Log.e(ComicsParameters.APP_TAG, "Error: " + e.getMessage());
			} catch (Exception e) {
				Log.e(ComicsParameters.APP_TAG, "Exception: " + e.getMessage());
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
			urlConnection.setConnectTimeout(ComicsParameters.TIME_OUT);
			urlConnection.setReadTimeout(ComicsParameters.TIME_OUT);
			
			// download the file
			final InputStream input = new BufferedInputStream(urlConnection.getInputStream(), ComicsParameters.BUFFER_SIZE);
			final OutputStream output = new FileOutputStream(f);

			int count;
			byte data[] = new byte[ComicsParameters.BUFFER_SIZE];
				
			while ((count = input.read(data)) != -1) {
				output.write(data, 0, count);
			}

			output.flush();
			output.close();
			input.close();

			res = true;
		} catch (FileNotFoundException e) {
			Log.e(ComicsParameters.APP_TAG, "File " + url + " not found");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}
		
		return res;
	}
	
	static boolean loadFileToBuffer(File file, byte [] buffer) {
		FileInputStream input = null;
		try {
			input = new FileInputStream(file);
			int res = input.read(buffer);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			if (input != null) {
				try {
					 input.close();
				} catch (IOException e) {
				}
			}
		}
		
		return true;
	}

	static boolean saveBufferToFile(byte [] buffer, File file) {
		boolean res = false;
		
		FileOutputStream input = null;
		try {
			input = new FileOutputStream(file);
			input.write(buffer);
			res = true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			if (input != null) input.close();
		} catch (IOException e) {
		}
		
		return res;
	}

	static byte [] inputStreamToBytes(InputStream input, int size) {
		byte [] buffer = null;

		if (input != null) {
			try {
				buffer = new byte[size];

				int offset = 0;
				int readSize = 0;
				
				while (size > 0 && (readSize = input.read(buffer, offset, size)) > 0)
				{
					offset += readSize;
					size -= readSize;
				}

				if (size != 0) {
					Log.e(ComicsParameters.APP_TAG, "Album buffer length differs");
				}

				input.close();
			} catch (OutOfMemoryError e) {
				Log.e(ComicsParameters.APP_TAG, "OutOfMemoryError while allocating a buffer of " + String.valueOf(size) + " bytes");
				return null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return buffer;
	}
	
	static int findNearestPowerOfTwoScale(int srcSize, int dstSize) {
		int scale = 1;

		while(srcSize / 2 >= Math.max(dstSize, ComicsParameters.MIN_SCALED_SIZE)) {
			srcSize /= 2;
			scale *= 2;
		}
		
		return scale;
	}

	static boolean hasReadExternalStoragePermission(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;

			// under Android 6+ request read external storage permissions
			if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				if (activity.shouldShowRequestPermissionRationale(permission)) {
					// TODO: display a message box
					activity.requestPermissions(new String[]{permission}, ComicsParameters.REQUEST_READ_EXTERNAL_PERMISSION);
				} else {
					activity.requestPermissions(new String[]{permission}, ComicsParameters.REQUEST_READ_EXTERNAL_PERMISSION);
				}

				return false;
			}
		}

		return true;
	}

	static boolean restartApplicationIfNeededReadExternalStoragePermission(int requestCode, String permissions[], int[] grantResults, Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (requestCode == ComicsParameters.REQUEST_READ_EXTERNAL_PERMISSION && permissions.length > 0 && permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					Log.i(ComicsParameters.APP_TAG, "Permission " + permissions[0] + " granted, we need to restart application to apply them");

					// restart application
					// PendingIntent pi = PendingIntent.getActivity(activity, 0, activity.getIntent(), PendingIntent.FLAG_CANCEL_CURRENT);
					// AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
					// am.set(AlarmManager.RTC, System.currentTimeMillis() + 500, pi);
					PackageManager packageManager = activity.getPackageManager();
					Intent intent = packageManager.getLaunchIntentForPackage(activity.getPackageName());
					ComponentName componentName = intent.getComponent();
					Intent mainIntent = Intent.makeRestartActivityTask(componentName);
					activity.startActivity(mainIntent);

					// Stop now
					System.exit(0);
				} else {
					Log.e(ComicsParameters.APP_TAG, "Permission REQUEST_WRITE_EXTERNAL_PERMISSION Denied");
				}
			}

			return true;
		}

		return false;
	}
}
