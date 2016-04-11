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

import java.io.File;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

public class AlbumThread extends HandlerThread {
	public Album album;

	private int mCurrentPage = -1;
	private int mPreviousPage = -1;
	private int mWidth = 0;
	private int mHeight = 0;
	private boolean mLoadingPage = false;
	private Handler mMainHandler;
	private Handler mLoaderHandler;
	private WeakReference<AlbumPageCallback> mCallback;

	static final int TIME_CHECK_INTERVAL = 16; // milliseconds
	static final int DELAY_WINDOW_CHANGED = 1000; // ms

	static final int LOADER_OPEN = 1;
	static final int LOADER_UPDATE_PAGE = 2;
	static final int LOADER_UPDATE_BUFFERS = 3;
	static final int LOADER_LOAD_PREFERENCES = 4;
	static final int LOADER_SAVE_CURRENT_ALBUM = 5;

	static final int VIEWER_CHANGE_PAGE = 10;
	static final int VIEWER_UPDATE_PAGE = 11;
	static final int VIEWER_WINDOW_CHANGED = 12;
	static final int VIEWER_SCROLL_PAGE = 13;
	static final int VIEWER_OPEN_BEGIN = 14;
	static final int VIEWER_OPEN_END = 15;
	static final int VIEWER_ERROR = 16;

	public interface AlbumPageCallback {
		public int getPageWidth();
		public int getPageHeight();

		public void onUpdateNextPage(Bitmap bitmap);
		public void onUpdatePreviousPage(Bitmap bitmap);
		public void onUpdateCurrentPage(Bitmap bitmap);

		public void onPageChanged(int current, int next);

		public boolean onReset();
		public void onError(int error);
		public void onWindowChanged(boolean highQuality, boolean fullScreen);
		public void onPageScrolled(int direction);

		public void onOpenBegin();
		public void onOpenEnd();

		public Context getContext();
	}

	public AlbumThread() {
		super("ComicsReader", Process.THREAD_PRIORITY_MORE_FAVORABLE /* Process.THREAD_PRIORITY_BACKGROUND */);

		mMainHandler = new Handler(Looper.getMainLooper(), mMainCallback);
		
		start();
	}

	public void setAlbumPageCallback(AlbumPageCallback callback) {
		mCallback = new WeakReference<AlbumPageCallback>(callback);
	}

	@Override
	protected void onLooperPrepared() {
		super.onLooperPrepared();
		synchronized (this) {
			mLoaderHandler = new Handler(getLooper(), mLoaderCallback);
			notifyAll();
		}
	}

	public boolean exit() {
		if (mLoaderHandler != null) {
			mLoaderHandler.removeMessages(LOADER_OPEN);
			mLoaderHandler.removeMessages(LOADER_UPDATE_PAGE);
			mLoaderHandler.removeMessages(LOADER_LOAD_PREFERENCES);
			mLoaderHandler = null;
		}

		return true;
	}

	private boolean isLoaderReady() {
		if (!isAlive()) return false;

		synchronized (this) {
			while (isAlive() && mLoaderHandler == null) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}
		
		return true;
	}

	public Uri getAlbumUri() {
		if (album == null) return null;

		if (album.filename == null) return null;

		return Uri.parse(Uri.fromFile(new File(album.filename)).toString() + "#" + String.valueOf(mCurrentPage));
	}

	public boolean isValid() {
		return album != null && album.numPages > 0;
	}

	public int getPageWidth() {
		return Math.max(mWidth, ComicsParameters.sScreenWidth);
	}

	public int getPageHeight() {
		return Math.max(mHeight, ComicsParameters.sScreenHeight);
	}

	public int getCurrentPage() {
		return mCurrentPage;
	}

	public int getNextPage() {
		if (AlbumParameters.rightToLeft) {
			if (mCurrentPage == 1) return 0;

			int previousPage = mCurrentPage - (AlbumParameters.doublePage ? 2:1);

			return previousPage < getLastPage() ? -1:previousPage;
		}
		
		if (mCurrentPage == 0 && getLastPage() > 0) return 1;

		int nextPage = mCurrentPage + (AlbumParameters.doublePage ? 2:1);

		return nextPage > getLastPage() ? -1:nextPage;
	}

	public int getPreviousPage() {
		if (AlbumParameters.rightToLeft) {
			if (mCurrentPage == 0 && getFirstPage() > 0) return 1;

			int nextPage = mCurrentPage + (AlbumParameters.doublePage ? 2:1);

			return nextPage > getFirstPage() ? -1:nextPage;
		}
		
		if (mCurrentPage == 1) return 0;

		int previousPage = mCurrentPage - (AlbumParameters.doublePage ? 2:1);

		return previousPage < getFirstPage() ? -1:previousPage;
	}

	public int getLastPage() {
		if (AlbumParameters.rightToLeft || album == null) return 0;

		int page = album.numPages - 1;
		
		if (AlbumParameters.doublePage && (page % 2) == 0) --page;
		
		return page;

	}

	public int getFirstPage() {
		if (!AlbumParameters.rightToLeft || album == null) return 0;

		int page = album.numPages - 1;
			
		if (AlbumParameters.doublePage && (page % 2) == 0) --page;
			
		return page;
	}

	public boolean isFirstPage() {
		return getCurrentPage() == getFirstPage();
	}
	
	public boolean isLastPage() {
		return getCurrentPage() == getLastPage();
	}

	public boolean isPageLoading() {
		return mLoadingPage;
	}

	/*
	 * Loader actions
	 */

	/**
	 * Open album
	 * @param uri URI for album
	 */
	public void open(Uri uri) {
		if (!isLoaderReady()) return;

		Message msg = mLoaderHandler.obtainMessage(LOADER_OPEN);
		msg.obj = uri;
		mLoaderHandler.sendMessage(msg);
	}

	/**
	 * go to specified page
	 * @param page the page
	 */
	public void changePage(int page) {
		if (!isLoaderReady()) return;

//		AlbumPage.sAbortLoading = true;
		
		Message msg = mMainHandler.obtainMessage(VIEWER_CHANGE_PAGE);
		msg.getData().putInt("page", page);
		mMainHandler.sendMessage(msg);
	}

	/*
	 * go to next page
	 */
	public void nextPage() {
		changePage(getNextPage());
	}

	/*
	 * go to previous page
	 */
	public void previousPage() {
		changePage(getPreviousPage());
	}

	/*
	 * go to last page
	 */
	public void lastPage() {
		changePage(getLastPage());
	}

	/*
	 * go to first page
	 */
	public void firstPage() {
		changePage(getFirstPage());
	}

	public void updatePreviousPage(boolean force) {
		if (album != null) {
			if (!isLoaderReady()) return;
			
			int page = getPreviousPage();

			if (page < 0 || page >= album.numPages) return;
			
			if (force) {
				mMainHandler.removeMessages(VIEWER_UPDATE_PAGE);
			}

			Message msg = mMainHandler.obtainMessage(VIEWER_UPDATE_PAGE);
			msg.getData().putInt("page", page);
			
			if (force) {
				mMainHandler.sendMessage(msg);
			} else {
				mMainHandler.sendMessageDelayed(msg, 500);
			}
		}
	}

	public void updateNextPage(boolean force) {
		if (album != null) {
			if (!isLoaderReady()) return;

			int page = getNextPage();

			if (page < 0 || page >= album.numPages) return;

			if (force) {
				mMainHandler.removeMessages(VIEWER_UPDATE_PAGE);
			}
			
			Message msg = mMainHandler.obtainMessage(VIEWER_UPDATE_PAGE);
			msg.getData().putInt("page", page);

			if (force) {
				mMainHandler.sendMessage(msg);
			} else {
				mMainHandler.sendMessageDelayed(msg, 500);
			}
		}
	}

	public void updateCurrentPage(boolean force) {
		if (!isLoaderReady()) return;
		
		if (mCurrentPage < 0 || mCurrentPage >= album.numPages) return;

		mMainHandler.removeMessages(VIEWER_UPDATE_PAGE);
		
		Message msg = mMainHandler.obtainMessage(VIEWER_UPDATE_PAGE);
		msg.getData().putBoolean("force", force);
		msg.getData().putInt("page", mCurrentPage);
		mMainHandler.sendMessage(msg);
	}
	
	public void updateBuffers(int current, int next, int previous) {
		if (!isLoaderReady()) return;
		
		Message msg = mLoaderHandler.obtainMessage(LOADER_UPDATE_BUFFERS);
		msg.getData().putInt("current", current);
		msg.getData().putInt("next", next);
		msg.getData().putInt("previous", previous);
		mLoaderHandler.sendMessage(msg);
	}

	public void loadPreferences(boolean force) {
		if (!isLoaderReady()) return;

		// cancel previously page loading
		mLoadingPage = false;

		Message msg = mLoaderHandler.obtainMessage(LOADER_LOAD_PREFERENCES);
		msg.getData().putBoolean("force", force);
		mLoaderHandler.sendMessage(msg);
	}

	public void updateWindow() {
		mMainHandler.sendEmptyMessageDelayed(VIEWER_WINDOW_CHANGED, DELAY_WINDOW_CHANGED);
	}

	public void saveCurrentAlbum() {
		if (!isLoaderReady()) return;

		mLoaderHandler.sendEmptyMessage(LOADER_SAVE_CURRENT_ALBUM);
	}
	
	/*
	 * Main actions
	 */

	public void updatePageScrolling(int direction) {
		mMainHandler.removeMessages(VIEWER_SCROLL_PAGE);

		final Message msg = mMainHandler.obtainMessage(VIEWER_SCROLL_PAGE);
		msg.getData().putInt("direction", direction);
		mMainHandler.sendMessageDelayed(msg, TIME_CHECK_INTERVAL);
	}

	private void displayError(int error) {
		Message msg = mMainHandler.obtainMessage(VIEWER_ERROR);
		msg.getData().putInt("error", error);
		mMainHandler.sendMessage(msg);
	}

	private Callback mLoaderCallback = new Callback() {
		public boolean handleMessage(Message msg) {
			if (mCallback.get() == null) return false;

			switch (msg.what) {
			case LOADER_OPEN: {
				int error = 0;
				Uri uri = (Uri)msg.obj;

				String filename = Album.getFilenameFromUri(uri);
				String strPage = Album.getPageFromUri(uri);

				int page = -1;

				if (strPage != null) {
					try {
						page = Integer.valueOf(strPage);
					} catch(NumberFormatException e) {
					}
				}

				if (Album.isFilenameValid(filename)) {
					mMainHandler.sendEmptyMessage(VIEWER_OPEN_BEGIN);

					// cancel previously page loading
					mLoadingPage = false;

					if (album != null) {
						synchronized (album) {
							album.close();
						}
					}
					
					// be sure the pages cache is empty
					ComicsParameters.clearCurrentAlbumDirectory();
					
					// create an album depending on file type
					album = Album.createInstance(filename);

					synchronized (album) {
						if (album.open(filename, true)) {
							ComicsParameters.sCurrentOpenAlbum = album.filename;
						} else {
							error = R.string.error_no_album_loaded;
						}
					}

					mMainHandler.sendEmptyMessage(VIEWER_OPEN_END);

					msg = mMainHandler.obtainMessage(VIEWER_CHANGE_PAGE);
					msg.getData().putInt("page", page);
					mMainHandler.sendMessage(msg);
				} else {
					error = R.string.error_no_album_loaded; // TODO: filename invalid
				}

				if (error != 0) {
					displayError(error);
				}
				break;
			}
			case LOADER_UPDATE_PAGE: {
				Bundle b = msg.getData();
				int page = b.getInt("page");

				if (album.updatePage(page)) {
					msg = mMainHandler.obtainMessage(VIEWER_UPDATE_PAGE);
					msg.getData().putInt("page", page);
					mMainHandler.sendMessage(msg);
				} else {
					// TODO: create an error message
				}
				break;
			}
			case LOADER_UPDATE_BUFFERS: {
				Bundle b = msg.getData();
				int current = b.getInt("current");
				int next = b.getInt("next");
				int previous = b.getInt("previous");
				
				album.updateBuffers(current, next, previous);
				
				break;
			}
			case LOADER_LOAD_PREFERENCES: {
				boolean force = msg.getData().getBoolean("force", false);

				if (AlbumParameters.getAlbumPreferences(mCallback.get().getContext()) || force) {
					ComicsParameters.initTablet();

					mMainHandler.sendEmptyMessageDelayed(VIEWER_WINDOW_CHANGED, DELAY_WINDOW_CHANGED);
				}
				
				break;
			}
			case LOADER_SAVE_CURRENT_ALBUM: {
				final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mCallback.get().getContext()).edit();
				editor.putString("last_file", getAlbumUri().toString());
//				Log.d("ComicsReader", "Save " + getAlbumUri().toString());
				editor.commit();
				break;
			}
			default:
				Log.e("ComicsReader", "Message " + String.valueOf(msg.what) + " ot handled in loader handler");
				break;
			}
			return true;
		}
	};

	private Callback mMainCallback = new Callback() {
		public boolean handleMessage(Message msg) {
			if (mLoaderHandler == null || album == null) {
				// Looper stopped, we consider all terminating tasks invalid
				return false;
			}

			switch (msg.what) {
			case VIEWER_CHANGE_PAGE: {
				Bundle b = msg.getData();
				int page = b.getInt("page");

				if (page == -1) {
					page = album.currentPageNumber;
				}

				// wait until album is loaded
				if (page >= album.numPages) {
					page = album.numPages - 1;
				} else if (page < 0) {
					page = 0;
				}

				if (AlbumParameters.doublePage) {
					if (page > 0) {
						page -= 1 - (page % 2);
					} else {
						page = 0;
					}
				}

				// we really change page
				if (page != mCurrentPage) {
					mPreviousPage = mCurrentPage;
					mCurrentPage = page;
					
					mCallback.get().onPageChanged(mCurrentPage, mPreviousPage);

					if (album.hasPageBitmap(page)) {
						mWidth = album.getPageWidth(page);
						mHeight = album.getPageHeight(page);
						
						final Bitmap bitmap = album.getPageBitmap(page);

						mCallback.get().onUpdateCurrentPage(bitmap);
					} else {
//						AlbumPage.sAbortLoading = true;

						// request page loading
						msg = mLoaderHandler.obtainMessage(LOADER_UPDATE_PAGE);
						msg.getData().putInt("page", page);
						mLoaderHandler.sendMessage(msg);
					}
				}

				return true;
			}
			case VIEWER_UPDATE_PAGE: {
				Bundle b = msg.getData();
				int page = b.getInt("page");
				int count = b.getInt("count", 0);
				boolean force = b.getBoolean("force", false);
				
				if (force) {
					album.updatePagesSizes();
				}
				
				final Bitmap bitmap = album.getPageBitmap(page);
				
				// display the right image if already in memory or a blank page
				if (page == getCurrentPage()) {
					mCallback.get().onUpdateCurrentPage(bitmap);
				} else if (page == getNextPage()) {
					mCallback.get().onUpdateNextPage(bitmap);
				} else if (page == getPreviousPage()) {
					mCallback.get().onUpdatePreviousPage(bitmap);
				} else {
				}
				
				// if image not yet in memory, load it
				if (bitmap == null) {
//					AlbumPage.sAbortLoading = true;

					// request page loading
					msg = mLoaderHandler.obtainMessage(LOADER_UPDATE_PAGE);
					msg.getData().putInt("page", page);
					msg.getData().putInt("count", ++count);
					mLoaderHandler.sendMessage(msg);
				}
				
				return true;
			}
			case VIEWER_WINDOW_CHANGED: {
				mCallback.get().onWindowChanged(AlbumParameters.highQuality, AlbumParameters.fullScreen);

				return true;
			}
			case VIEWER_SCROLL_PAGE: {
				Bundle bundle = msg.getData();
				int direction = bundle.getInt("direction");

				mCallback.get().onPageScrolled(direction);

				return true;
			}
			case VIEWER_OPEN_BEGIN: {
				mCallback.get().onOpenBegin();

				return true;
			}
			case VIEWER_OPEN_END: {
				mCallback.get().onOpenEnd();

				return true;
			}
			case VIEWER_ERROR: {
				Bundle bundle = msg.getData();
				int error = bundle.getInt("error");

				mCallback.get().onError(error);

				return true;
			}
			default:
				Log.e("ComicsReader", "Message " + String.valueOf(msg.what) + " ot handled in main handler");
				break;
			}
			
			return false;
		}
	};
}
