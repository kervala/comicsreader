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

import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import android.net.Uri;
import android.util.Log;

public class AlbumsIndexHandler extends DefaultHandler {
	private ArrayList<ThumbnailItem> mItems;
	private String mTitle;
	private int mSize = 0;
	private String mFilename;
	private String mThumbnail;
	private String mUrl;
	private StringBuilder mBuilder = new StringBuilder();
	
	public AlbumsIndexHandler() {
		mItems = new ArrayList<ThumbnailItem>();
	}
	
	public ArrayList<ThumbnailItem> getItems() {
		return mItems;
	}
	
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		// reset previous values
		if ("album".equals(localName) || "folder".equals(localName)) {
			mTitle = null;
			mFilename = null;
			mUrl = null;
			mThumbnail = null;
			mSize = 0;
		}
		mBuilder.setLength(0);
	}
	
	private boolean isAlbumValid() {
		if (mFilename == null || "".equals(mFilename)) return false;
		if (mSize <= 0) return false;
		if (mUrl == null || !"http".equals(Uri.parse(mUrl).getScheme())) return false;
		if (mThumbnail == null || !"http".equals(Uri.parse(mThumbnail).getScheme())) return false;

		return true;
	}

	private boolean isFolderValid() {
		if (mUrl == null || !"http".equals(Uri.parse(mUrl).getScheme())) return false;

		return true;
	}
	
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		String str = mBuilder.toString().trim();
		if ("title".equals(localName)) {
			mTitle = str;
		} else if ("filename".equals(localName)) {
			if (!str.matches(".*[/\\<>|\":*?].*")) {
				mFilename = str;
			} else {
				Log.w(ComicsParameters.APP_TAG, "Invalid filename for " + str);
				mFilename = null;
			}
		} else if ("size".equals(localName)) {
			try {
				mSize = Integer.parseInt(str);
			} catch(NumberFormatException e) {
				Log.w(ComicsParameters.APP_TAG, "Invalid size format for " + str);
				mSize = 0;
			}
		} else if ("thumbnail".equals(localName)) {
			mThumbnail = str;
		} else if ("url".equals(localName)) {
			mUrl = str;
		} else if ("album".equals(localName)) {
			// add album to list
			if (isAlbumValid()) {
				BrowserItem item = new BrowserItem(mTitle, BrowserItem.TYPE_FILE, true);
				item.setAlbumUrl(mUrl);
				item.setThumbnailUrl(mThumbnail);
				item.setFilename(mFilename);
				item.setSize(mSize);
				mItems.add(item);
			}
		} else if ("folder".equals(localName)) {
			// add folder to list
			if (isFolderValid()) {
				BrowserItem item = new BrowserItem(mTitle, "..".equals(mTitle) ? BrowserItem.TYPE_DIRECTORY_PARENT:BrowserItem.TYPE_DIRECTORY_CHILD, true);
				item.setAlbumUrl(mUrl);
				mItems.add(item);
			}
		}
	}

	@Override
	public void characters(char ch[], int start, int length) {
		mBuilder.append(ch, start, length);
	}
}
