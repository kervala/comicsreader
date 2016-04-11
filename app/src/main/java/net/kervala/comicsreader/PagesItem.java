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

import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;

public class PagesItem extends ThumbnailItem {
	private final WeakReference<Album> mAlbum;

	public PagesItem(Context context, int index, Album album) {
		this.index = index;
		mAlbum = new WeakReference<Album>(album);
		mText = String.valueOf(index+1);
		mThumbPosition = THUMB_POSITION_BOTTOM;
	}

	@Override
	protected boolean loadBitmap() {
		// try to load thumbnail from cache
		mThumb = mAlbum.get().getPageThumbnailFromCache(index);

		if (mThumb == null) {
			// create a thumbnail for specified page
			mThumb = mAlbum.get().createPageThumbnail(index);
		}
		
		return mThumb != null;
	}

	@Override
	protected BitmapDrawable getDefaultDrawable() {
		return ComicsParameters.sPlaceholderDrawable;
	}
}
