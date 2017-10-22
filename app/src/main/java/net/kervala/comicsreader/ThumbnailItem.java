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

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.TextView;

class ThumbnailItem implements Comparable<ThumbnailItem> {
	// thumbnail status
	static final int STATUS_NONE = 0;
	static final int STATUS_UPDATED = 1;

	// display thumbnail on the top or bottom
	static final int THUMB_POSITION_TOP = 0;
	static final int THUMB_POSITION_BOTTOM = 1;

	public int index = -1;
	
	Bitmap mThumb;
	private int mThumbSize = 0;
	String mText;
	int mStatus = STATUS_NONE;
	int mThumbPosition;

	static int sUsedMemory = 0;
	private static int sMaxThumbSize = 0;
	static final int MAX_USED_MEMORY = 10000000;

	synchronized void recycle() {
		if (mThumb != null) {
			sUsedMemory -= mThumbSize;
			mThumb.recycle();
			mStatus = STATUS_NONE;
			mThumb = null;
			mThumbSize = 0;
		}
	}

	protected boolean loadBitmap() {
		return false;
	}

	protected BitmapDrawable getDefaultDrawable() {
		return null;
	}

	synchronized Bitmap getThumb() {
		return mThumb;
	}
	
	synchronized BitmapDrawable getDrawable() {
		if (mThumb == null || mStatus < STATUS_UPDATED) return getDefaultDrawable();

		final BitmapDrawable drawable = new BitmapDrawable(null, mThumb);
		drawable.setBounds(0, 0, mThumb.getWidth(), mThumb.getHeight());
		drawable.setTargetDensity(mThumb.getDensity());
		
		return drawable;
	}

	String getText() {
		return mText;
	}
	
	synchronized boolean update() {
		// don't update if already done
		if (mStatus >= STATUS_UPDATED) return true;

		if (!loadBitmap()) return false;

		mStatus = STATUS_UPDATED;

		mThumbSize = mThumb.getRowBytes() * mThumb.getHeight();
		sUsedMemory += mThumbSize;

		if (mThumbSize > sMaxThumbSize) sMaxThumbSize = mThumbSize;

		return true;
	}

	int getStatus() {
		return mStatus;
	}

	boolean updateView(TextView view) {
		if (view == null) {
			return false;
		}
	
		final BitmapDrawable drawable = getDrawable();

		if (mThumbPosition == THUMB_POSITION_BOTTOM) {
			view.setCompoundDrawables(null, null, null, drawable);
		} else {
			view.setCompoundDrawables(null, drawable, null, null);
		}
		
		if (mText != null) {
			view.setText(mText);
		}

		return true;
	}

	public int compareTo(ThumbnailItem item) {
		if (mText != null) {
			return NaturalOrderComparator.compareStrings(mText, item.mText);
		} else {
			throw new IllegalArgumentException();
		}
	}
}
