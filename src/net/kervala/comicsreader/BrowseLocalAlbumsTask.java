/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2015 Cedric OCHS
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.net.Uri;
import android.os.AsyncTask;

public class BrowseLocalAlbumsTask extends AsyncTask<Void, Integer, String> {
	private File mDirectory;
	private WeakReference<BrowserActivity> mActivity;
	private ArrayList<ThumbnailItem> mItems;
	
	public BrowseLocalAlbumsTask(BrowserActivity activity, File directory) {
		mActivity = new WeakReference<BrowserActivity>(activity);
		mDirectory = directory;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPreExecute() {
		mActivity.get().showDialog(BrowserActivity.DIALOG_WAIT);
	}

	@Override
	protected String doInBackground(Void... params) {
		mItems = new ArrayList<ThumbnailItem>();
		
		List<ThumbnailItem> files = new ArrayList<ThumbnailItem>();

		String error = null;

		File[] dirs = mDirectory.listFiles();
		
		if (dirs != null) {
			try {
				for(File dir: dirs) {
					String path = dir.getAbsolutePath();
					if (Album.isFilenameValid(path)) {
						BrowserItem item = new BrowserItem(Album.getTitle(path), BrowserItem.TYPE_FILE, false);
						item.setPath(path);
						item.setSize((int)dir.length());
						files.add(item);
					} else {
						if(dir.isDirectory()) {
							BrowserItem item = new BrowserItem(dir.getName(), BrowserItem.TYPE_DIRECTORY_CHILD, false);
							item.setPath(dir.getAbsolutePath());
							mItems.add(item);
						}
					}
				}
			} catch(Exception e) {
				error = e.toString();
				e.printStackTrace();
			}

			Collections.sort(mItems);
			Collections.sort(files);
			
			mItems.addAll(files);
		}

		if(!mDirectory.getAbsolutePath().equalsIgnoreCase(ComicsParameters.sRootDirectory.getAbsolutePath())) {
			BrowserItem item = new BrowserItem("..", BrowserItem.TYPE_DIRECTORY_PARENT, false);
			item.setPath(mDirectory.getParent());
			mItems.add(0, item);
		}
		
		return error;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPostExecute(String error) {
		if (mActivity.get() == null) return;
		
		mActivity.get().removeDialog(BrowserActivity.DIALOG_WAIT);

		if (error != null) {
			mActivity.get().displayError(error);
		} else {
			mActivity.get().displayItems(Uri.fromFile(mDirectory).toString(), mItems);
		}
	}
}
