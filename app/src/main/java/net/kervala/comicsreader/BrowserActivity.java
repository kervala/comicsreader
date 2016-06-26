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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.AdapterView.OnItemClickListener;

public class BrowserActivity extends Activity implements OnItemClickListener, OnItemLongClickListener, OnCancelListener, OnDismissListener, Callback {
	static final int DIALOG_NONE = 0;
	static final int DIALOG_PAGES = 1;
	static final int DIALOG_TEXT = 2;
	static final int DIALOG_ABOUT = 3;
	static final int DIALOG_WAIT = 4;
	static final int DIALOG_ERROR = 5;
	static final int DIALOG_DOWNLOAD = 6;
	static final int DIALOG_ALBUM = 7;
	static final int DIALOG_CONFIRM = 8;
	static final int DIALOG_BOOKMARK = 9;
	static final int DIALOG_LOGIN = 10;
	
	static final int REQUEST_PREFERENCES = 0;
	static final int REQUEST_VIEWER = 1;
	static final int REQUEST_BOOKMARK = 2;
	
	static final int ACTION_NONE = 0;
	static final int ACTION_CONFIRM_YES = 1;
	static final int ACTION_CONFIRM_NO = 2;
	static final int ACTION_BOOKMARK = 5;
	static final int ACTION_UPDATE_ITEM = 10;
	static final int ACTION_ASK_LOGIN = 11;
	static final int ACTION_LOGIN = 12;
	static final int ACTION_CANCEL_LOGIN = 13;

	private ThumbnailAdapter mAdapter;
	private BrowserItem mSelectedItem;
	private String mLastUrl;
	private String mLastTitle;
	private String mLastFile;
	private int mLastVersion;
	private boolean mFastScroll;
	private ComicsAuthenticator mAuthenticator;
	
	protected ProgressDialog mProgressDialog;
	protected AlbumDialog mAlbumDialog;
	protected ErrorDialog mErrorDialog;
	protected LoginDialog mLoginDialog;
	protected String mError;
	protected Handler mHandler;
	protected String mText;
	protected String mTitle;
	protected String mUsername;
	protected String mPassword;
	protected boolean mRememberPassword;
	
	private DownloadAlbumTask mDownloadAlbumTask;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mHandler = new Handler(this);

		ComicsParameters.init(this);
		
		setContentView(R.layout.browser);

		final GridView g = (GridView) findViewById(R.id.grid);
		g.setOnItemClickListener(this);
		g.setOnItemLongClickListener(this);

		mAuthenticator = new ComicsAuthenticator(mHandler);
		Authenticator.setDefault(mAuthenticator);
		
		new LoadPreferencesTask().execute();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		destroyDownloadAlbumTask();

		ComicsParameters.release();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		if (mAdapter != null) {
			mAdapter.refresh();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mAdapter != null) {
			mAdapter.reset();
		}

		new SavePreferencesTask().execute();
	}
	
	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		
		// force refreshing layout to be sure text is not truncated
		new RefreshTask(false).execute();
	}
	
	public void onItemClick(AdapterView<?> l, View v, int position, long id) {
		final BrowserItem item = (BrowserItem) mAdapter.getItem(position);

		if (item.getType() == BrowserItem.TYPE_DIRECTORY_CHILD || item.getType() == BrowserItem.TYPE_DIRECTORY_PARENT) {
			if (!item.getRemote()) {
				browseFolder(item.getPath());
			} else {
				browseFolder(item.getAlbumUrl());
			}
		} else {
			onFileClick(item);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean res = super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.menu_pages).setVisible(false);
		menu.findItem(R.id.menu_browse).setVisible(false);
		return res;
	}

	public void onCancel(DialogInterface dialog) {
		
		// user canceled authentication dialog
		if (dialog == mLoginDialog) {
			browseFolder(null);
		} else {
			if (mAdapter == null) {
				openLastFolder();
			} else {
				destroyDownloadAlbumTask();
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		String uriString = null;

		if (data != null) {
			final Uri uri = data.getData();
			
			if (uri != null) uriString = uri.toString();
		}
		
		switch(requestCode) {
			case REQUEST_VIEWER:
			if (resultCode == ViewerActivity.RESULT_URL) {
				// user selected a bookmark from ViewerActivity
				if (uriString != null && !uriString.equals(mLastUrl)) {
					mLastUrl = uriString;
						
					resetAdapter();
						
					mAdapter = null;
				}

				if (mAdapter == null) {
					openLastFolder();
				}
			} else if (resultCode == ViewerActivity.RESULT_FILE) {
				// user closed ViewerActivity
				if (uriString != null && !uriString.equals(mLastFile)) {
					mLastFile = uriString;
				}

				if (mAdapter == null) {
					openLastFolder();
				}
			} else if (resultCode == ViewerActivity.RESULT_QUIT) {
				// want to quit
				finish();
			}

			break;

			case REQUEST_BOOKMARK:
			if (resultCode == ViewerActivity.RESULT_URL) {
				if (uriString != null && !uriString.equals(mLastUrl)) {
					mLastUrl = uriString;
				}

				openLastFolder();
			}
			break;

			case REQUEST_PREFERENCES:
			new RefreshTask(false).execute();
			break;
		}
	}
	
	public void setLastUrl(String url) {
		mLastUrl = url;
	}

	public boolean openLastFolder() {
		return browseFolder(mLastUrl);
	}

	public boolean openLastFile() {
		startViewer(mLastFile);

		return true;
	}
	
	@SuppressWarnings("deprecation")
	private boolean onFileClick(BrowserItem item) {
		// file is remote
		if (item.getRemote() && item.getPath() == null) {
			final File file = item.getFile();
			
			if (file != null && file.exists() && file.length() == item.getSize()) {
				item.setPath(item.getFile().getAbsolutePath());
			} else {
				final String state = Environment.getExternalStorageState();

				if (!Environment.MEDIA_MOUNTED.equals(state)) {
					displayError(getString(R.string.error_unable_to_write));

					return false;
				}

				if (!isConnected()) {
					displayError(getString(R.string.error_no_connection_available));

					return false;
				}

				final StatFs stat = new StatFs(ComicsParameters.sCacheDirectory.getAbsolutePath());
				final long freeSpace = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();

				if (freeSpace < item.getSize()) {
					if (ComicsParameters.getDownloadedSize() + freeSpace < item.getSize()) {
						displayError(getString(R.string.error_no_room));
						
						return false;
					} else {
						// emptying download cache
						ComicsParameters.clearDownloadedAlbumsCache();
					}
				}

				mAdapter.stopThread();

				// download it and save it somewhere
				mDownloadAlbumTask = new DownloadAlbumTask(this, item);
				mDownloadAlbumTask.execute();

				return true;
			}
		}

		loadItem(item);
		
		return true;
	}

	public void loadItem(String title) {
		int pos = mAdapter.getItemPosition(title);
		
		if (pos > -1) {
			loadItem((BrowserItem)mAdapter.getItem(pos));
		}
	}
	
	@SuppressWarnings("deprecation")
	private void loadItem(BrowserItem item) {
		if (Album.askConfirm(item.getPath())) {
			mSelectedItem = item;

			// ask confirmation to open it
			showDialog(DIALOG_CONFIRM);
		} else {
			startViewer(item);
		}
	}
	
	/**
	 * Start ViewerActivity with a selected album
	 * 
	 * @param item selected BrowserItem
	 */
	private void startViewer(BrowserItem item) {
		mLastTitle = item.getText();

		String newFilename = item.getLocalUri().toString();
		String lastFilename = null;
		
		if (mLastFile != null) {
			lastFilename = Uri.fromFile(new File(Album.getFilenameFromUri(Uri.parse(mLastFile)))).toString();
		}

		if (newFilename != null && !newFilename.equals(lastFilename)) {
			mLastFile = newFilename;
		}

		startViewer(mLastFile);
	}

	/**
	 * Start ViewerActivity with the last album
	 * 
	 * @param uri URI to open
	 */
	private void startViewer(String uri) {
		final Intent intent = new Intent(this, ViewerActivity.class);
		intent.putExtra("requestCode", REQUEST_VIEWER);
		intent.setDataAndType(Uri.parse(uri), Album.mimeType(uri));
		intent.setAction(Intent.ACTION_VIEW);
		startActivityForResult(intent, REQUEST_VIEWER);
	}
	
	private String getDefaultDirectory() {
		String folder;
		String state = Environment.getExternalStorageState();

		// a SD card is mounted, use it
		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			folder = ComicsParameters.sExternalDirectory.getAbsolutePath();
		} else {
			folder = ComicsParameters.sRootDirectory.getAbsolutePath();
		}
		
		return folder;
	}
	
	public boolean isConnected() {
		try {
			final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			final NetworkInfo networkInfo = cm.getActiveNetworkInfo();

			return networkInfo != null && networkInfo.isConnected();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public boolean browseFolder(String url) {
		boolean usingDefaultDirectory = false;

		if (url == null) {
			usingDefaultDirectory = true;

			url = getDefaultDirectory();

			if (url == null) {
				displayError(getString(R.string.error_no_external_storage));
				return false;
			}
		}
		
		Uri uri = Uri.parse(url);

		String scheme = uri.getScheme();
		
		boolean ok = false;

		if ("http".equals(scheme)) {
			// check for an internet connection
			if (isConnected()) {
				new BrowseRemoteAlbumsTask(this, url).execute();

				ok = true;
			} else {
				displayError(getString(R.string.error_no_connection_available));

				// using default folder
				return browseFolder(null);
			}
		}

		if (!ok && (scheme == null || "file".equals(scheme) || "".equals(scheme))) {
			url = Album.getFilenameFromUri(uri);
			File f = new File(url);

			if (!f.exists() || !f.isDirectory()) {
				if (!usingDefaultDirectory) {
					// using default folder
					return browseFolder(null);
				} else {
					displayError(getString(R.string.error_no_external_storage));
					return false;
				}
			} 

			new BrowseLocalAlbumsTask(this, f).execute();

			ok = true;
		}

		return ok;
	}
	
	public void resetAdapter() {
		if (mAdapter != null) {
			// display an empty grid
			final GridView g = (GridView) findViewById(R.id.grid);
			g.setAdapter(null);

			// free all bitmaps used by adapter
			mAdapter.reset();
		}
	}
	
	public void displayItems(String url, ArrayList<ThumbnailItem> items) {
		mLastUrl = url;
		
		resetAdapter();
		
		mAdapter = new ThumbnailAdapter(this, mHandler, items, R.layout.browser_item);

		new RefreshTask(true).execute();
	}

	public void destroyDownloadAlbumTask() {
		if (mDownloadAlbumTask != null) {
			mDownloadAlbumTask.cancel();
			mDownloadAlbumTask = null;

			mAdapter.refresh();
		}
	}

	public boolean displayChangelog() {
		// Try to load the a package matching the name of our own package
		if (ComicsParameters.sPackageVersionCode > mLastVersion) {
			displayHtml(R.raw.changelog, R.string.changelog);

			mLastVersion = ComicsParameters.sPackageVersionCode;

			return true;
		}

		return false;
	}
	
	public void updateUserInfo() {
		final Uri oldUri = Uri.parse(mLastUrl != null ? mLastUrl:"");

		try {
			String userInfo = mUsername;
			if (mRememberPassword && mPassword != null) userInfo += ":" + mPassword;
			URI tmpUri = new URI(oldUri.getScheme(), userInfo, oldUri.getHost(), oldUri.getPort(), oldUri.getPath(), oldUri.getQuery(), oldUri.getFragment());
			mLastUrl = tmpUri.toString();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	public boolean handleMessage(Message msg) {
		switch(msg.what) {
			case ACTION_CONFIRM_YES:
			{
				startViewer(mSelectedItem);

				return true;
			}

			case ACTION_CONFIRM_NO:
			{
				browseFolder(mSelectedItem.getPath());

				return true;
			}

			case ACTION_UPDATE_ITEM:
			{
				final Bundle bundle = msg.getData();
				int index = bundle.getInt("index");

				final GridView g = (GridView)findViewById(R.id.grid);
				final BrowserItem item = (BrowserItem)g.getItemAtPosition(index);
				final TextView view = (TextView)g.findViewWithTag(item);

				if (item != null && view != null) item.updateView(view);
				
				// check if we need to recycle bitmaps
				mAdapter.optimize(g.getFirstVisiblePosition(), g.getLastVisiblePosition());

				return true;
			}
			case ACTION_ASK_LOGIN:
			{
				showDialog(DIALOG_LOGIN);
				return true;
			}
			case ACTION_LOGIN:
			{
				final Bundle bundle = msg.getData();
				mUsername = bundle.getString("username");
				mPassword = bundle.getString("password");
				mRememberPassword = bundle.getBoolean("remember_password");
				mAuthenticator.setLogin(mUsername);
				mAuthenticator.setPassword(mPassword);
				mAuthenticator.validate(true);

				updateUserInfo();

				new SavePreferencesTask().execute();

				return true;
			}
			case ACTION_CANCEL_LOGIN:
			{
				mAuthenticator.validate(false);
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("deprecation")
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		final BrowserItem item = (BrowserItem)mAdapter.getItem(position);

		if (item.getType() == BrowserItem.TYPE_FILE) {
			showDialog(DIALOG_ALBUM);
		
			mAlbumDialog.setFilename(item);
			
			return true;
		}
		
		return false;
	}
	
	private void updateTitle() {
		String lastUrl = mLastUrl;

		if (lastUrl != null) {
			// remove scheme from URL
			int pos = lastUrl.indexOf("://");
			if (pos > -1) {
				lastUrl = lastUrl.substring(pos+3);

				// remove login and password
				pos = lastUrl.indexOf("@");
				if (pos > -1) {
					lastUrl = lastUrl.substring(pos+1);
				}
			}
			
			final String root = ComicsParameters.sExternalDirectory.getAbsolutePath();
			// remove root path from path
			if (lastUrl.startsWith(root)) {
				lastUrl = lastUrl.substring(root.length());
			}
		}
		
		String title = getString(R.string.browser_title);

		if (lastUrl != null && lastUrl.length() > 0) title = lastUrl;
		
		setTitle(title);
	}
	
	private class LoadPreferencesTask extends AsyncTask<String, Integer, Integer> {
		@Override
		protected void onPreExecute() {
		}

		@Override
		protected Integer doInBackground(String... params) {
			// set default values for ViewerActivity preferences at first launch
			PreferenceManager.setDefaultValues(BrowserActivity.this, R.xml.preferences, false);

			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.this);

			mRememberPassword = prefs.getBoolean("remember_password", true);

			mLastUrl = prefs.getString("last_url", getDefaultDirectory());
			mLastTitle = prefs.getString("last_title", null);
			mLastFile = prefs.getString("last_file", null);
			mLastVersion = prefs.getInt("last_version", 0);

			if (mLastUrl == null) return 0;

			Uri uri = Uri.parse(mLastUrl);
			
			String userInfo = uri.getUserInfo();
			
			if (userInfo != null) {
				int pos = userInfo.indexOf(":");
				
				if (pos > -1) {
					mUsername = userInfo.substring(0, pos);
					mPassword = userInfo.substring(pos+1);
				} else {
					mUsername = userInfo;
				}
				
				mAuthenticator.setLogin(mUsername);
				mAuthenticator.setPassword(mPassword);
			}
			
			return Album.isUrlValid(mLastFile) ? 1:0;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (!displayChangelog()) {
				if (result == 1) {
					openLastFile();
				} else {
					openLastFolder();
				}
			}
		}
	}

	private class SavePreferencesTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			final SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.this).edit();

			prefs.putBoolean("remember_password", mRememberPassword);

			updateUserInfo();

			prefs.putString("last_url", mLastUrl);
			prefs.putString("last_title", mLastTitle);
			prefs.putString("last_file", mLastFile);
			prefs.putInt("last_version", mLastVersion);
			prefs.commit();
			return null;
		}
	}
	
	private class RefreshTask extends AsyncTask<Void, Integer, Boolean> {
		private boolean mSelect;
		
		public RefreshTask(boolean select) {
			mSelect = select;
		}
		
		@Override
		protected void onPreExecute() {
			updateTitle();
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			if (mAdapter == null) return false;

			// to be sure we can download/generate thumbnails in background thread
			mAdapter.init();

			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.this);
			mFastScroll = prefs.getBoolean("preference_fast_scroll", false);

			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				final GridView g = (GridView) findViewById(R.id.grid);
				g.setFastScrollEnabled(mFastScroll);

				if (g.getAdapter() != mAdapter) {
					g.setAdapter(mAdapter);
				}

				if (mSelect) {
					final int pos = mAdapter.getItemPosition(mLastTitle);

					if (pos != -1) {
						// be sure to select last album after all items are added
						g.post(new Runnable() {
							public void run() {
								g.setSelection(pos);
							}
						});
					}
				}
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean res = super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return res;
	}

	@SuppressWarnings("deprecation")
	public void displayHtml(int resText, int resTitle) {
		InputStream raw = getResources().openRawResource(resText);
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		int i;
		try {
			i = raw.read();
			while (i != -1)	{
				stream.write(i);
				i = raw.read();
			}
			raw.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		mText = stream.toString();
		mTitle = getString(resTitle);

		showDialog(DIALOG_TEXT);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_browse:
			openLastFolder();
			return true;
		case R.id.menu_bookmarks:
			startActivityForResult(new Intent(this, BookmarksActivity.class), REQUEST_BOOKMARK);
			return true;
		case R.id.menu_pages:
			showDialog(DIALOG_PAGES);
			return true;
		case R.id.menu_settings:
			startActivityForResult(new Intent(this, ComicsPreferenceActivity.class), REQUEST_PREFERENCES);
			return true;
		case R.id.menu_quit:
			finish();
			return true;
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id) {
		// manage common dialogs
		switch(id) {
			case DIALOG_WAIT:
			{
				ProgressDialog progressDialog = new ProgressDialog(this);
				progressDialog.setMessage(getString(R.string.dialog_progress_browser_message));
				progressDialog.setIndeterminate(true);
				progressDialog.setCancelable(false);

				return progressDialog;
			}
			
			case DIALOG_ERROR:
			mErrorDialog = new ErrorDialog(this);
			return mErrorDialog;
			
			case DIALOG_DOWNLOAD:
			return mProgressDialog;

			case DIALOG_ALBUM:
			mAlbumDialog = new AlbumDialog(this);
			return mAlbumDialog;

			case DIALOG_CONFIRM:
			return new ConfirmDialog(this, mHandler, getString(R.string.confirm_open_folder));

			case DIALOG_TEXT:
			return new TextDialog(this);
			
			case DIALOG_LOGIN:
			mLoginDialog = new LoginDialog(this, mHandler); 
			return mLoginDialog;
		}
		
		return super.onCreateDialog(id);
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onPrepareDialog (int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);

		// manage common dialogs
		switch(id) {
			case DIALOG_TEXT:
			TextDialog textDialog = (TextDialog)dialog;
			textDialog.setTitle(mTitle);
			textDialog.setText(mText);
			break;

			case DIALOG_ERROR:
			ErrorDialog errorDialog = (ErrorDialog)dialog;
			errorDialog.setError(mError);
			break;

			case DIALOG_LOGIN:
			LoginDialog loginDialog = (LoginDialog)dialog;
			loginDialog.setUsername(mUsername);
			loginDialog.setPassword(mPassword);
			loginDialog.setRememberPassword(mRememberPassword);
			break;
		}

		dialog.setOnCancelListener(this);
		dialog.setOnDismissListener(this);
	}

	@SuppressWarnings("deprecation")
	public void displayError(String error) {
		mError = error;
		showDialog(DIALOG_ERROR);

		Log.e(ComicsParameters.APP_TAG, mError);
	}

	private void showProgress(String title, int total) {
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
		}

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIndeterminate(false);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setMessage(String.format(getString(R.string.album_downloading), title));
		mProgressDialog.setMax(total);
		mProgressDialog.setOnCancelListener(this);
		mProgressDialog.setOnDismissListener(this);
	}
	
	public void showProgressAlbum(String title, int total) {
		showProgress(title, total);
		mProgressDialog.setCancelable(true);
		mProgressDialog.setMessage(String.format(getString(R.string.album_downloading), title));
		mProgressDialog.show();
	}

	public void showProgressIndex(String title, int total) {
		showProgress(title, total);
		mProgressDialog.setCancelable(false);
		mProgressDialog.setMessage(String.format(getString(R.string.album_downloading), title));
		mProgressDialog.show();
	}
	
	public void updateProgress(int current) {
		if (mProgressDialog != null) {
			mProgressDialog.setProgress(current);
		}
	}
	
	public void dismissProgress() {
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
		}
	}

	public void onDismiss(DialogInterface dialog) {
	}
}
