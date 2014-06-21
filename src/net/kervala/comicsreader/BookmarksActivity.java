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

import java.io.File;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class BookmarksActivity extends ListActivity implements Callback, OnClickListener {
	private Cursor mCursor;
	private Handler mHandler;
	private BookmarkDialog mBookmarkDialog;
	private BookmarksHelper mHelper;
	private int mPosition = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mHandler = new Handler(this);

		setContentView(R.layout.bookmarks);
		
		registerForContextMenu(getListView());
		
		mHelper = new BookmarksHelper(this);

		final Button addButton = (Button)findViewById(R.id.bookmarks_add);
		addButton.setOnClickListener(this);

		fillData();
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		mHelper.closeDatabase();
		
		if (mCursor != null) {
			stopManagingCursor(mCursor);
			mCursor.close();
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		try {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			if (info.position > 0) {
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.bookmarks_menu_context, menu);
			}
		} catch (ClassCastException e) {
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		try {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
			mPosition = info.position;
		} catch (ClassCastException e) {
			mPosition = -1;
			return false;
		}

		switch (item.getItemId()) {
			case R.id.bookmarks_menu_context_load:
			return loadBookmark();

			case R.id.bookmarks_menu_context_edit:
			return displayBookmark();

			case R.id.bookmarks_menu_context_remove:
			return displayConfirm();
		}

		return super.onContextItemSelected(item);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case BrowserActivity.DIALOG_BOOKMARK:
			{
				mBookmarkDialog = new BookmarkDialog(BookmarksActivity.this, mHandler);
				return mBookmarkDialog;
			}
			case BrowserActivity.DIALOG_CONFIRM:
			{
				return new ConfirmDialog(this, mHandler, getString(R.string.confirm_delete_bookmark));
			}
		}
		
		return super.onCreateDialog(id);
	}
	
	private void fillData() {
		new GetBookmarkTask().execute();
	}
	
	@SuppressWarnings("deprecation")
	private boolean displayBookmark() {
		showDialog(BrowserActivity.DIALOG_BOOKMARK);
		
		if (mPosition == -1) {
			mBookmarkDialog.setId(-1);
			mBookmarkDialog.setTitle("");
			mBookmarkDialog.setUrl("");
			return true;
		} else if (mCursor.moveToPosition(mPosition)) {
			final String scheme = "http://";
			String url = mCursor.getString(2);

			if (url.startsWith(scheme)) {
				// fix remote path
				url = url.substring(scheme.length());
			}

			mBookmarkDialog.setId(mCursor.getInt(0));
			mBookmarkDialog.setTitle(mCursor.getString(1));
			mBookmarkDialog.setUrl(url);
			return true;
		}
		
		return false;
	}
	
	@SuppressWarnings("deprecation")
	private boolean displayConfirm() {
		showDialog(BrowserActivity.DIALOG_CONFIRM);
		return true;
	}
	
	private boolean loadBookmark() {
		if (mCursor.moveToPosition(mPosition)) {
			String url = mCursor.getString(2);
			
			Uri uri = null;
			
			if (url.startsWith("/")) {
				uri = Uri.fromFile(new File(url));
			} else {
				uri = Uri.parse(url);
			}
			
			final Intent intent = getIntent();
			intent.setDataAndType(uri, null);
			setResult(ViewerActivity.RESULT_URL, intent);
			finish();

			return true;
		}
		
		return false;
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mPosition = position;
		loadBookmark();
	}

	public boolean handleMessage(Message msg) {
		Bundle bundle = msg.getData();
		
		switch(msg.what) {
			case BrowserActivity.ACTION_BOOKMARK:
			{
				new SetBookmarkTask(bundle.getInt("id"), bundle.getString("title"), bundle.getString("url")).execute();
				
				return true;
			}
			
			case BrowserActivity.ACTION_CONFIRM_YES:
			{
				if (mCursor.moveToPosition(mPosition)) {
					new DeleteBookmarkTask().execute(mCursor.getInt(0));
					
					return true;
				}
			}

			case BrowserActivity.ACTION_CONFIRM_NO:
			{
				return true;
			}
		}
		
		return false;
	}

	public void onClick(View v) {
		mPosition = -1;
		displayBookmark();
	}
	
	private class GetBookmarkTask extends AsyncTask<Void, Void, Cursor> {
		@Override
		protected Cursor doInBackground(Void... params) {
			return mHelper.getBookmarkCursor();
		}

		@SuppressWarnings("deprecation")
		@Override
		protected void onPostExecute(Cursor cursor) {
			if (mCursor != null) {
				stopManagingCursor(mCursor);
				mCursor.close();
			}

			final String [] fields = new String[] { "name", "url" };

			ListAdapter adapter = new SimpleCursorAdapter(BookmarksActivity.this, R.layout.bookmarks_item, cursor, fields, new int[] { R.id.bookmarks_item_name, R.id.bookmarks_item_url });
			setListAdapter(adapter);

			mCursor = cursor;

			startManagingCursor(mCursor);
		}
	}

	private class SetBookmarkTask extends AsyncTask<String, Integer, Boolean> {
		private int mId;
		private String mTitle;
		private String mUrl;
		
		public SetBookmarkTask(int id, String title, String url) {
			mId = id;
			mTitle = title;
			mUrl = url;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			return mHelper.setBookmark(mId, mTitle, mUrl);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				fillData();
			}
		}
	}

	private class DeleteBookmarkTask extends AsyncTask<Integer, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(Integer... params) {
			return mHelper.deleteBookmark(params[0]);
		}


		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				fillData();
			}
		}
	}
}
