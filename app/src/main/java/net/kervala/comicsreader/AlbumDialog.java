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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlbumDialog extends Dialog implements android.view.View.OnClickListener {
	private BrowserItem mItem;
	private LinearLayout mLayout;
	
	public AlbumDialog(Context context) {
		super(context);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.dialog_album);

		setCanceledOnTouchOutside(true);
		
		mLayout = (LinearLayout)findViewById(R.id.dialog_album_layout);
		mLayout.setOnClickListener(this);
	}

	public void setFilename(BrowserItem item) {
		mItem = item;

		init();
	}

	public void init() {
		final TextView title = (TextView)findViewById(R.id.dialog_album_title);
		final TextView sizeView = (TextView)findViewById(R.id.dialog_album_size);
		final ImageView image = (ImageView)findViewById(R.id.dialog_album_thumbnail);

		int size = mItem.getSize();
		String unit = getContext().getString(R.string.bytes);
		
		if (size > 1000) {
			size /= 1000;
			unit = getContext().getString(R.string.kbytes);
			if (mItem.getSize() > 1000) {
				size /= 1000;
				unit = getContext().getString(R.string.mbytes);
			}
		}
		
		title.setText(mItem.getText());
		sizeView.setText(String.format(getContext().getString(R.string.dialog_album_size), size, unit));
		
		if (mItem.getStatus() == BrowserItem.STATUS_NONE) {
			mItem.update();
		}

		image.setImageDrawable(mItem.getDrawable());
	}

	public void onClick(View v) {
		if (v == mLayout) {
			cancel();
		}
	}
}
