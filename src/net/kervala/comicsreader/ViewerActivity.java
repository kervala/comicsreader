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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.kervala.comicsreader.AlbumThread.AlbumPageCallback;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

public class ViewerActivity extends Activity implements OnTouchListener, FullScrollView.OnSizeChangedListener, AlbumPageCallback {
	static final int PREVIOUS_PAGE = -1;
	static final int CURRENT_PAGE = 0;
	static final int NEXT_PAGE = 1;

	static final int DIALOG_NONE = 0;
	static final int DIALOG_PAGES = 1;
	static final int DIALOG_TEXT = 2;
	static final int DIALOG_ABOUT = 3;
	static final int DIALOG_ERROR = 5;
	static final int DIALOG_ALBUM = 7;
	
	static final int REQUEST_PREFERENCES = 0;
	static final int REQUEST_BOOKMARK = 2;
	
	static final int RESULT_FILE = RESULT_FIRST_USER;
	static final int RESULT_URL = RESULT_FIRST_USER+1;
	
	protected ErrorDialog mErrorDialog;
	protected String mError;
	protected String mText;
	protected String mTitle;
	
	private FullScrollView mScrollView;
	private FullImageView mImageView;
	
	private AlbumThread mAlbumThread;

	private int mMinPixelsBeforeSwitch = 0;
	private int mFirstTouchPosX;
	private int mFirstTouchPosY;
	private int mPrevTouchPosX;
	private boolean mProcessTouch = false;
	private Scroller mScroller;
	private Overlay mOverlay;
	private boolean mActionBarVisible = true;
	private boolean mHideActionBar = false;
	private Object mActionBar;
	private Method mActionBarShow;
	private Method mActionBarHide;
	private Method mActionBarSetTitle;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ComicsParameters.init(this);
		
		initActionBar();

		setContentView(R.layout.viewer);

		mScroller = new Scroller(this, new DecelerateInterpolator(1.0f));
		mOverlay = new Overlay(this);
		
		mScrollView = (FullScrollView) findViewById(R.id.scrollview);
		mImageView = (FullImageView) findViewById(R.id.imageview);

		mScrollView.setOnTouchListener(this);
		registerForContextMenu(mScrollView);

		mAlbumThread = new AlbumThread();
		mAlbumThread.setAlbumPageCallback(this);

		mAlbumThread.loadPreferences(true);

		Intent intent = getIntent();

		if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			// open last page
			mAlbumThread.open(intent.getData());
		}
	}
	
	public boolean initActionBar() {
		try {
			// call getActionBar to get a pointer on ActionBar
			Method getActionBar = getClass().getMethod("getActionBar");
			mActionBar = getActionBar.invoke(this);

			if (mActionBar == null) return false;

			// get ActionBar methods we need to use
			Class<?> actionBar = mActionBar.getClass();
			mActionBarShow = actionBar.getMethod("show");
			mActionBarHide = actionBar.getMethod("hide");
			mActionBarSetTitle = actionBar.getMethod("setTitle", CharSequence.class);

			// set a black background (default is transparent in overlay mode)
			Method setBackgroundDrawable = actionBar.getMethod("setBackgroundDrawable", Drawable.class);
			setBackgroundDrawable.invoke(mActionBar, new ColorDrawable(Resources.getSystem().getColor(android.R.color.background_dark)));

			return true;
		} catch (NoSuchMethodException e) {
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}

		mActionBar = null;
		mActionBarVisible = false;

		return false;
	}

	public void setActionBarVisible(boolean visible) {
		if (mActionBarVisible == visible || mActionBar == null) return;

		mActionBarVisible = visible;

		try {
			if (visible) {
				mOverlay.hide();
				mActionBarShow.invoke(mActionBar);
			} else {
				mActionBarHide.invoke(mActionBar);
			}
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
	}

	public boolean getActionBarVisible() {
		return mActionBarVisible;		
	}

	public void setActionBarTitle(String title) {
		if (mActionBar == null || title == null) return;

		try {
			mActionBarSetTitle.invoke(mActionBar, title);
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		} 
	}

	public void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
		if (newWidth == oldWidth && newHeight == oldHeight) return;

		int width = ComicsParameters.sScreenWidth;
		int height = ComicsParameters.sScreenHeight;

		ComicsParameters.sScreenWidth = newWidth;
		ComicsParameters.sScreenHeight = newHeight;

		if (mAlbumThread != null && mAlbumThread.isValid() && (width != newWidth || height != newHeight)) {
			// force refreshing current page even if already loaded

			if (AlbumParameters.zoom == Album.ZOOM_FIT_SCREEN ||
				(AlbumParameters.zoom == Album.ZOOM_FIT_WIDTH && mAlbumThread.getPageWidth() != newWidth) ||
				(AlbumParameters.zoom == Album.ZOOM_FIT_HEIGHT && mAlbumThread.getPageHeight() != newHeight)) {
				mAlbumThread.updateCurrentPage(true);
			}

			// we suppose user succeeded to show navigation bar (with power button ?)
			if (!mActionBarVisible && mImageView.getFullScreen() && newHeight <= height && newWidth <= width) {
				mAlbumThread.updateWindow();
			}
		}

		mMinPixelsBeforeSwitch = newWidth >> 3;
	}

	@Override
	protected void onResume() {
		super.onResume();

		mScrollView.setSizeChangedListener(this);
		
		if (mAlbumThread != null && mAlbumThread.isValid() && mImageView != null) {
			// refresh current page only if not already loaded
			mAlbumThread.updateCurrentPage(false);
		}

		mImageView.setFullScreen(AlbumParameters.fullScreen);
	}
	
	@Override
	protected void onPause() {
		super.onPause();

		mScrollView.setSizeChangedListener(null);

		// release all cached images in FullImageView
		mImageView.reset();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		mOverlay.remove();

		if (mAlbumThread != null) {
			mAlbumThread.exit();
			mAlbumThread = null;
		}
		
		// reset current open album
		ComicsParameters.sCurrentOpenAlbum = null;

		ComicsParameters.release();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
			case REQUEST_BOOKMARK:
				if (resultCode == BrowserActivity.RESULT_URL) {
					openIntentFolder(data);
				}
			break;

			case REQUEST_PREFERENCES:
				mAlbumThread.loadPreferences(false);
			break;
		}
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id) {
		// manage common dialogs
		switch(id) {
			case DIALOG_ERROR:
			mErrorDialog = new ErrorDialog(this);
			return mErrorDialog;
			
			case DIALOG_TEXT:
			return new TextDialog(this);
		}
		
		Dialog dialog = super.onCreateDialog(id);

		if (dialog == null && mAlbumThread.isValid() && id == DIALOG_PAGES) {
			dialog = new PagesDialog(this);
		}

		return dialog;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPrepareDialog (int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);

		// manage viewer specific dialogs
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

		case DIALOG_PAGES:
			PagesDialog pagesDialog = (PagesDialog) dialog;

			pagesDialog.setAlbum(mAlbumThread.album);
			pagesDialog.setPage(mAlbumThread.getCurrentPage());
			break;

		default:
			break;
		}
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean onSearchRequested() {
		showDialog(DIALOG_PAGES);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch(keyCode) {
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			scrollToNextPage();
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			scrollToPreviousPage();
			return true;
		case KeyEvent.KEYCODE_TAB:
		case KeyEvent.KEYCODE_SEARCH:
			showDialog(DIALOG_PAGES);
			return true;
		case KeyEvent.KEYCODE_BACK:
			if (mAlbumThread != null) {
				String mimeType = mAlbumThread.album != null ? mAlbumThread.album.getMimeType():null;
				Intent intent = getIntent();
				intent.setDataAndType(mAlbumThread.getAlbumUri(), mimeType);
				setResult(RESULT_FILE, intent);
			}
			finish();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	public boolean onTouch(View v, MotionEvent event) {
		int touchPosX = (int) (event.getX());
		int touchPosY = (int) (event.getY());

		switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
			return touchEnded(touchPosX, touchPosY);

			case MotionEvent.ACTION_DOWN:
			return touchBegan(touchPosX, touchPosY);

			case MotionEvent.ACTION_MOVE:
			return touchMoved(touchPosX, touchPosY);
		}

		return false;
	}

	private boolean touchBegan(int posX, int posY) {
		if (mScroller.isFinished()){
			mHideActionBar = true;

			mPrevTouchPosX = -1;
			mFirstTouchPosX = posX;
			mFirstTouchPosY = posY;
		} else {
			// width of borders to change page without fling
			int zoneWidth = ComicsParameters.sScreenWidth >> 4;

			if (posX < zoneWidth) {
				scrollToPreviousPage();
			} else if (posX > ComicsParameters.sScreenWidth - zoneWidth) {
				scrollToNextPage();
			} else {
				mScroller.forceFinished(true);
				mProcessTouch = true;
			}
		}
		
		// make sure FullScrollView only receive touch events if scrollable
		return !mScrollView.canScroll();
	}

	private boolean touchMoved(int posX, int posY) {
		int fullDeltaX = posX - mFirstTouchPosX;
		int fullDeltaY = posY - mFirstTouchPosY;
		int absFullDeltaX = Math.abs(fullDeltaX);
		int absFullDeltaY = Math.abs(fullDeltaY);

		boolean scroll = false;

		// horizontal fling
		if (absFullDeltaX > (absFullDeltaY << 2)) {
			int resistance = AlbumParameters.edgesResistance;
			
			if ((resistance < 1) || (absFullDeltaX > (mMinPixelsBeforeSwitch >> 2) * resistance)) {
				int x = mImageView.getOffset() + mScrollView.getScrollX();
				int w = mScrollView.getWidth();

				if (x == 0 && fullDeltaX > 0 && !mAlbumThread.isFirstPage()) {
					// left border, enable scroll to previous page
					mAlbumThread.updatePreviousPage(true);
					scroll = true;
				} else if (x + w >= Math.max(ComicsParameters.sScreenWidth, mAlbumThread.getPageWidth()) && fullDeltaX < 0 && !mAlbumThread.isLastPage()) {
					// right border, enable scroll to next page
					mAlbumThread.updateNextPage(true);
					scroll = true;
				}
			}
		}

		if (!mProcessTouch) {
			// check if we should scroll horizontally
			if (scroll) {
				mScrollView.clearAnimation();

				mProcessTouch = true;

				return true;
			} else {
				int deltaMin = ComicsParameters.sScreenHeight >> 2;

				// scroll up when on the top, show the action bar
				if (mScrollView.getScrollY() == 0 && fullDeltaY > 0) {
					if (fullDeltaY > deltaMin) {
						setActionBarVisible(true);
						
						// in immersive mode, we can't display ActionBar anymore, so we need to leave it temporarily
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
							mImageView.setFullScreen(false);
						}
					}
					mHideActionBar = false;
				} else {
					mHideActionBar = true;
				}
			}
		} else {
			if (mPrevTouchPosX == -1) {
				mPrevTouchPosX = posX + mImageView.getOffset();
			}

			// negation since the screen moves in a direction opposite to that of the touch
			int deltaX = -(posX - mPrevTouchPosX);
			
			mImageView.setOffset(deltaX);
			
			return true;
		}

		return false;
	}

	private boolean touchEnded(int posX, int posY) {
		if (mHideActionBar && getActionBarVisible()) {
			setActionBarVisible(false);
			mAlbumThread.updateWindow();
			mHideActionBar = false;
		}

		if (!mProcessTouch)
		{
			// width of borders to change page without fling
			int zoneWidth = ComicsParameters.sScreenWidth >> 4;

			if (posX < zoneWidth) {
				scrollToPreviousPage();
				return true;
			} else if (posX > ComicsParameters.sScreenWidth - zoneWidth) {
				scrollToNextPage();
				return true;
			}

			// process clicks, not flings
			return false;
		}

		int deltaX = posX - mFirstTouchPosX;

		if (deltaX > mMinPixelsBeforeSwitch) {
			scrollToPreviousPage();
		} else if (deltaX < -mMinPixelsBeforeSwitch) {
			scrollToNextPage();
		} else {
			scrollToCurrentPage();
		}

		mPrevTouchPosX = -1;
		mProcessTouch = false;

		return true;
	}

	public void scrollToPreviousPage() {
		if (!mAlbumThread.isFirstPage()) {
			final int offset = mImageView.getOffset();
			final int dx = -(mAlbumThread.getPageWidth() + offset);

			mScroller.startScroll(offset, mScrollView.getScrollY(), dx, -mScrollView.getScrollY(), Math.abs(dx) << 7 >> AlbumParameters.pageTransitionSpeed);
			mAlbumThread.updatePageScrolling(PREVIOUS_PAGE);
		}
	}

	public void scrollToCurrentPage() {
		final int offset = mImageView.getOffset();
		final int dx = -offset;

		mScroller.startScroll(offset, mScrollView.getScrollY(), dx, 0, Math.abs(dx) << 7 >> AlbumParameters.pageTransitionSpeed);
		mAlbumThread.updatePageScrolling(CURRENT_PAGE);
	}
	
	public void scrollToNextPage() {
		if (!mAlbumThread.isLastPage()) {
			final int offset = mImageView.getOffset() + mScrollView.getScrollX();
			final int dx = mAlbumThread.getPageWidth() - offset;

			mScroller.startScroll(offset, mScrollView.getScrollY(), dx, -mScrollView.getScrollY(), Math.abs(dx) << 7 >> AlbumParameters.pageTransitionSpeed);
			mAlbumThread.updatePageScrolling(NEXT_PAGE);
		}
	}
	
	public void changePage(int page) {
		mAlbumThread.changePage(page);
	}
	
	public void showPageNumber(int page, int pages, int duration) {
		++page;

		if (!getActionBarVisible()) mOverlay.show(String.valueOf(page) + "/" + String.valueOf(pages), duration);

		setActionBarTitle(String.format("%s - %d/%d", mAlbumThread.album.title, page, pages));
	}

	boolean openIntentFolder(Intent i) {
		// check if current Intent was started from BrowserActivity
		int requestCode = getIntent().getExtras().getInt("requestCode");

		if (requestCode != BrowserActivity.REQUEST_VIEWER) {
			// start BrowserActivity if not already started
			startActivity(new Intent(this, BrowserActivity.class));
		} else {
			setResult(RESULT_URL, i);
			finish();
		}
		
		return true;
	}
	
	public boolean openLastFolder() {
		final Intent intent = getIntent();

		Bundle bundle = intent.getExtras();
		int requestCode = bundle != null ? bundle.getInt("requestCode"):0;

		if (requestCode != BrowserActivity.REQUEST_VIEWER) {
			startActivity(new Intent(this, BrowserActivity.class));
		} else {
			Album album = mAlbumThread.album; 

			// avoid some unexpected crash
			if (album != null) {
				intent.setDataAndType(mAlbumThread.getAlbumUri(), album.getMimeType());
			}

			setResult(RESULT_FILE, intent);
			finish();
		}
		
		return true;
	}
	
	public int getPageWidth() {
		return mImageView.getBitmapWidth();
	}

	public int getPageHeight() {
		return mImageView.getBitmapHeight();
	}

	public void onUpdateNextPage(Bitmap bitmap) {
		mImageView.setNextBitmap(bitmap);
	}

	public void onUpdatePreviousPage(Bitmap bitmap) {
		mImageView.setPreviousBitmap(bitmap);
	}
	
	public void onUpdateCurrentPage(Bitmap bitmap) {
		mImageView.setOffset(0);

		// automatically rotate the screen to best fit the image
		if (bitmap != null && AlbumParameters.autoRotate)
		{
			if (bitmap.getWidth() > bitmap.getHeight()) {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			}
			else {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}
		}

		mImageView.setCurrentBitmap(bitmap);
	}

	public void onPageChanged(int current, int previous) {
		mScrollView.scrollTo(0, 0);

		mAlbumThread.saveCurrentAlbum();

		if (AlbumParameters.overlayDuration > -1) {
			showPageNumber(current, mAlbumThread.album.numPages, AlbumParameters.overlayDuration);
		}

		int next = current;

		// load and cache the next bitmap to display
		if (current >= previous) {
			mAlbumThread.updateNextPage(true);
			mAlbumThread.updatePreviousPage(false);
			++next;
		} else if (current < previous){
			mAlbumThread.updatePreviousPage(true);
			mAlbumThread.updateNextPage(false);
			--next;
		}

		// load/flush buffers
		mAlbumThread.updateBuffers(current, next, previous);
	}

	public boolean onReset() {
		mImageView.reset();

		return true;
	}

	public void onError(int error) {
		displayError(getString(error));

		System.gc();
	}

	public void onWindowChanged(boolean highQuality, boolean fullScreen) {
		final Window window = getWindow();

		if (highQuality) {
			window.setFormat(PixelFormat.RGBA_8888);
		} else {
			window.setFormat(PixelFormat.RGB_565);
		}

		if (fullScreen) {
			window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else {
			window.setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// don't turn off screen while reading
		window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// try to set full screen for tablets using CyanogenMod 9.x and + or Android 4.4+
		mImageView.setFullScreen(fullScreen);

		// hide ActionBar
		setActionBarVisible(false);
	}

	public void onPageScrolled(int direction) {
		if (mScroller.computeScrollOffset()) {
			mImageView.setOffset(mScroller.getCurrX());

			// if changing page, set position to left
			if (direction != CURRENT_PAGE) {
				mScrollView.scrollTo(0, mScroller.getCurrY());
			}

			if (!mScroller.isFinished()) {
				mAlbumThread.updatePageScrolling(direction);
			} else {
				if (direction == NEXT_PAGE) {
					mAlbumThread.nextPage();
				} else if (direction == PREVIOUS_PAGE) {
					mAlbumThread.previousPage();
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

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
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
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	public void displayError(String error) {
		mError = error;
		showDialog(DIALOG_ERROR);

		Log.e(ComicsParameters.APP_TAG, mError);
	}

	public Context getContext() {
		return this;
	}

	public void onOpenBegin() {
	}

	public void onOpenEnd() {
	}
}
