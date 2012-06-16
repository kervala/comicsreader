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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class CommonActivity extends Activity implements OnCancelListener, OnDismissListener, Callback {
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
	
	static final int RESULT_FILE = RESULT_FIRST_USER;
	static final int RESULT_URL = RESULT_FIRST_USER+1;
	
	static final int ACTION_NONE = 0;
	static final int ACTION_CONFIRM_YES = 1;
	static final int ACTION_CONFIRM_NO = 2;
	static final int ACTION_UPDATE_BITMAP = 3;
	static final int ACTION_UPDATE_PAGE_NUMBER = 4;
	static final int ACTION_BOOKMARK = 5;
	static final int ACTION_SCROLL_PAGE = 6;
	static final int ACTION_UPDATE_WINDOW = 7;
	static final int ACTION_UPDATE_ALBUM = 8;
	static final int ACTION_DISPLAY_ERROR = 9;
	static final int ACTION_UPDATE_ITEM = 10;
	static final int ACTION_ASK_LOGIN = 11;
	static final int ACTION_LOGIN = 12;
	static final int ACTION_CANCEL_LOGIN = 13;
	
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mHandler = new Handler(this);

		ComicsParameters.init(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		ComicsParameters.release();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean res = super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return res;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}
	
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
		}
		return false;
	}

	public boolean openLastFolder() {
		return false;
	}

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

	public void onCancel(DialogInterface dialog) {
	}

	public void onDismiss(DialogInterface dialog) {
	}
	
	@Override
	public void onLowMemory() {
	}
	
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

	public boolean handleMessage(Message msg) {
		return false;
	}
}
