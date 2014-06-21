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
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

public class BrowseRemoteAlbumsTask extends AsyncTask<String, Integer, String> {
	private final String mUrl;
	private final WeakReference<BrowserActivity> mActivity;
	private ArrayList<ThumbnailItem> mItems;

	public BrowseRemoteAlbumsTask(BrowserActivity activity, String url) {
		mActivity = new WeakReference<BrowserActivity>(activity);
		mUrl = url;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onPreExecute() {
		mActivity.get().showDialog(BrowserActivity.DIALOG_WAIT);
	}
	
	private boolean parseJson(String json) {
		try {
			mItems = new ArrayList<ThumbnailItem>();
			
			JSONObject main = new JSONObject(json);
			
			JSONObject main2 = main.getJSONObject("albums");

			if (main2.has("folder")) {
				JSONArray folders = main2.getJSONArray("folder");

				for (int i = 0; i < folders.length(); i++) {
					final JSONObject folder = folders.getJSONObject(i);
					final String title = folder.getString("title");
					final String url = folder.getString("url");

					final BrowserItem item = new BrowserItem(title, "..".equals(title) ? BrowserItem.TYPE_DIRECTORY_PARENT:BrowserItem.TYPE_DIRECTORY_CHILD, true);
					item.setAlbumUrl(url);

					mItems.add(item);
				}
			}

			if (main2.has("album")) {
				JSONArray albums = main2.getJSONArray("album");

				for (int i = 0; i < albums.length(); i++) {
					final JSONObject album = albums.getJSONObject(i);
					final String title = album.getString("title");
					final String url = album.getString("url");
					final String thumbnail = album.getString("thumbnail");
					final String filename = album.getString("filename");
					int size = album.getInt("size");

					final BrowserItem item = new BrowserItem(title, BrowserItem.TYPE_FILE, true);
					item.setAlbumUrl(url);
					item.setThumbnailUrl(thumbnail);
					item.setFilename(filename);
					item.setSize(size);

					mItems.add(item);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private boolean parseXml(String xml) {
		try {
			final AlbumsIndexHandler handler = new AlbumsIndexHandler();
			
			Xml.parse(xml, handler);

			mItems = handler.getItems();
		} catch (SAXException e) {
//			error = e.toString();
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	@Override
	protected String doInBackground(String... params) {
		String error = null;

		URL url = null;
		
		try {
			// open a stream on URL
			url = new URL(mUrl);
		} catch (MalformedURLException e) {
			error = e.toString();
			e.printStackTrace();
		}

		boolean retry = false;
		HttpURLConnection urlConnection = null;
		int resCode = 0;
			
		do {
			// create the new connection
			ComicsAuthenticator.sInstance.reset();

			if (urlConnection != null) {
				urlConnection.disconnect();
			}

			try {
				urlConnection = (HttpURLConnection)url.openConnection();
				urlConnection.setConnectTimeout(ComicsParameters.TIME_OUT);
				urlConnection.setReadTimeout(ComicsParameters.TIME_OUT);
				resCode = urlConnection.getResponseCode();
			} catch(EOFException e) {
				// under Android 4
				resCode = -1;
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (resCode < 0) {
				retry = true;
			} else if (resCode == 401) {
				ComicsAuthenticator.sInstance.setResult(false);

				// user pressed cancel
				if (!ComicsAuthenticator.sInstance.isValidated()) {
					return null;
				}

				retry = true;
			} else {
				retry = false;
			}
		} while(retry);

		if (resCode != HttpURLConnection.HTTP_OK) {
			ComicsAuthenticator.sInstance.setResult(false);

			// TODO: HTTP error occurred 
			return null;
		}

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		InputStream is = null;
		
		try {
			// download the file
			is = new BufferedInputStream(urlConnection.getInputStream(), ComicsParameters.BUFFER_SIZE);
			
			int count = 0;
			byte data[] = new byte[ComicsParameters.BUFFER_SIZE];

			while ((count = is.read(data)) != -1) {
				bytes.write(data, 0, count);
			}

			ComicsAuthenticator.sInstance.setResult(true);
		} catch (IOException e) {
			error = e.toString();
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}

		if (bytes != null) {
			final String text = new String(bytes.toByteArray());
		
			if (text.contains("<albums>")) {
				parseXml(text);
			} else if (text.contains("\"albums\":")){
				parseJson(text);
			} else {
				Log.e("ComicsReader", "Error");
			}
		}

		return error;
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
	}
	  
	@SuppressWarnings("deprecation")
	@Override
	protected void onPostExecute(String error) {
		mActivity.get().removeDialog(BrowserActivity.DIALOG_WAIT);

		if (error != null) {
			mActivity.get().displayError(error);
			mActivity.get().setLastUrl(null);
		} else {
			if (mItems != null) {
				mActivity.get().displayItems(mUrl, mItems);
			}
		}
	}
}
