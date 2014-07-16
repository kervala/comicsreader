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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;

public class DownloadAlbumTask extends AsyncTask<String, Integer, String> {
	private WeakReference<BrowserActivity> mActivity;
	private BrowserItem mItem;
	private boolean mCancelled = false;
	
	public DownloadAlbumTask(BrowserActivity activity, BrowserItem item) {
		mActivity = new WeakReference<BrowserActivity>(activity);
		mItem = item;
	}
	
	public void cancel() {
		mCancelled = true;
	}
	
	@Override
	protected void onPreExecute() {
		mActivity.get().showProgressAlbum(mItem.getText(), mItem.getSize());
	}

	@Override
	protected String doInBackground(String... params) {
		String error = null;

		int count = 0;
		File f = null;

		HttpURLConnection urlConnection = null;
		
		try {
			URL url = new URL(mItem.getAlbumUrl());
			f = mItem.getFile();
			
			ComicsAuthenticator.sInstance.reset();
			
			urlConnection = (HttpURLConnection)url.openConnection();
			urlConnection.setConnectTimeout(ComicsParameters.TIME_OUT);
			urlConnection.setReadTimeout(ComicsParameters.TIME_OUT);
			
			int contentLength = urlConnection.getContentLength();

			// download the file
			InputStream input = new BufferedInputStream(urlConnection.getInputStream(), ComicsParameters.BUFFER_SIZE);
			OutputStream output = new FileOutputStream(f);

			byte data[] = new byte[ComicsParameters.BUFFER_SIZE];
			
			int offset = contentLength / 1000;
			int progress = 0;

			int total = 0;

			while ((count = input.read(data)) != -1 && !mCancelled) {
				total += count;
				progress += count;
				
				if (progress > offset) {
					publishProgress(total);
					progress = 0;
				}

				output.write(data, 0, count);
			}

			output.flush();
			output.close();
			input.close();
			
			ComicsAuthenticator.sInstance.setResult(true);
		} catch (Exception e) {
			mCancelled = true;
			error = e.toString();
			e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}

		if (mCancelled) {
			// delete partially downloaded file
			if (f.exists() && f.length() != mItem.getSize()) {
				f.delete();
			}
			
			mItem.setPath(null);
		} else {
			mItem.setPath(f.getAbsolutePath());
		}
		
		return error;
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		mActivity.get().updateProgress(progress[0]);
	}	
	
	@Override
	protected void onPostExecute(String error) {
		mActivity.get().dismissProgress();

		if (error != null) {
			mActivity.get().displayError(error);
		} else if (mItem.getPath() != null){
			mActivity.get().loadItem(mItem.getText());
		}
	}
}
