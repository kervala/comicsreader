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
import java.util.ArrayList;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.Gallery;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

@SuppressWarnings("deprecation")
public class PagesDialog extends Dialog implements OnItemClickListener, Callback {
	private WeakReference<Album> mAlbum;
	private int mPage = 0;
	private ThumbnailAdapter mAdapter;
	private final WeakReference<ViewerActivity> mActivity;
	private final Handler mHandler;
	private Gallery mGallery;

	public PagesDialog(ViewerActivity activity) {
		super(activity);
		
		mActivity = new WeakReference<ViewerActivity>(activity);
		mHandler = new Handler(this);
	}

	public void setAlbum(Album album) {
		mAlbum = new WeakReference<Album>(album);
	}

	public void setPage(int page) {
		mPage = page;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window window = getWindow();

		window.requestFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.pages);

		mGallery = (Gallery)findViewById(R.id.gallery);
		
		window.setLayout(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);

		setCanceledOnTouchOutside(true);
	}
	
	@Override
	protected void onStart() {
		super.onStart();

		if (mAlbum == null) return;

		final TextView t = (TextView) findViewById(R.id.title);
		t.setText(mAlbum.get().title);

		// create pages list
		final ArrayList<ThumbnailItem> items = new ArrayList<ThumbnailItem>();

		for(int i = 0, len = mAlbum.get().numPages; i < len; ++i) {
			items.add(new PagesItem(getContext(), i, mAlbum.get()));
		}

		mAdapter = new ThumbnailAdapter(getContext(), mHandler, items, R.layout.pages_item);

		mGallery.setAdapter(mAdapter);
		mGallery.setOnItemClickListener(PagesDialog.this);
		
		if (mPage != -1) {
			// be sure to select last album after all items are added
			mGallery.post(new Runnable() {
				public void run() {
					mGallery.setSelection(mPage);
				}
			});
		}
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (mAdapter != null) mAdapter.reset();
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		dismiss();
		
		mActivity.get().changePage(position);
	}

	public boolean handleMessage(Message msg) {
		if (msg.what == BrowserActivity.ACTION_UPDATE_ITEM) {
			final Bundle bundle = msg.getData();
			int index = bundle.getInt("index");

			// return the PagesItem at specific position
			final PagesItem item = (PagesItem)mGallery.getItemAtPosition(index);

			// return at most 1 view because all other have been removed when hidden
			final TextView view = (TextView)mGallery.findViewWithTag(item);

			if (item != null) item.updateView(view);

			return true;
		}

		return true;
	}
}
