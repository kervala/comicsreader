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

import net.kervala.comicsreader.AlbumThread.AlbumPageCallback;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

public class ViewerActivity extends CommonActivity implements OnTouchListener, FullScrollView.OnSizeChangedListener, AlbumPageCallback {
	static final int PREVIOUS_PAGE = -1;
	static final int CURRENT_PAGE = 0;
	static final int NEXT_PAGE = 1;

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
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.viewer);

		mScroller = new Scroller(this, new DecelerateInterpolator(1.0f));
		mOverlay = new Overlay(this, mHandler);
		
		mScrollView = (FullScrollView) findViewById(R.id.scrollview);
		mImageView = (FullImageView) findViewById(R.id.imageview);

		mScrollView.setOnTouchListener(this);
		registerForContextMenu(mScrollView);

		mAlbumThread = new AlbumThread();
		mAlbumThread.setAlbumPageCallback(this);
		
		mAlbumThread.loadPreferences();

		Intent intent = getIntent();

		if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			// open last page
			mAlbumThread.open(intent.getData());
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
			mAlbumThread.updateCurrentPage(true);
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
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
			case REQUEST_BOOKMARK:
				if (resultCode == CommonActivity.RESULT_URL) {
					openIntentFolder(data);
				}
			break;

			case REQUEST_PREFERENCES:
				mAlbumThread.loadPreferences();
			break;
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = super.onCreateDialog(id);

		if (dialog == null && mAlbumThread.isValid() && id == DIALOG_PAGES) {
			dialog = new PagesDialog(this);
		}

		return dialog;
	}

	@Override
	protected void onPrepareDialog (int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);

		// manage viewer specific dialogs
		switch(id) {
		case DIALOG_PAGES:
			PagesDialog pagesDialog = (PagesDialog) dialog;

			pagesDialog.setAlbum(mAlbumThread.getAlbum());
			pagesDialog.setPage(mAlbumThread.getCurrentPage());
			break;

		default:
			break;
		}
	}
	
	@Override
	public boolean onSearchRequested() {
		showDialog(DIALOG_PAGES);
		return true;
	}

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
				String mimeType = mAlbumThread.getAlbum() != null ? mAlbumThread.getAlbum().getMimeType():null;
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
			mPrevTouchPosX = -1;
			mFirstTouchPosX = posX;
			mFirstTouchPosY = posY;
		} else {
			mScroller.forceFinished(true);
			mProcessTouch = true;
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
			int resistance = mAlbumThread.getEdgeResistance();
			
			if ((resistance < 1) || (absFullDeltaX > (mMinPixelsBeforeSwitch >> 2) * resistance)) {
				int x = mImageView.getOffset() + mScrollView.getScrollX();
				int w = mScrollView.getWidth();

				if (x == 0 && fullDeltaX > 0 && !mAlbumThread.isFirstPage()) {
					// left border, enable scroll to previous page
					mAlbumThread.updatePreviousPage();
					scroll = true;
				} else if (x + w >= Math.max(ComicsParameters.sScreenWidth, mAlbumThread.getPageWidth()) && fullDeltaX < 0 &&  !mAlbumThread.isLastPage()) {
					// right border, enable scroll to next page
					mAlbumThread.updateNextPage();
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
		if (!mProcessTouch) return false;

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
			final int dx = -(Math.max(ComicsParameters.sScreenWidth, mAlbumThread.getPageWidth()) + offset);

			mScroller.startScroll(offset, mScrollView.getScrollY(), dx, -mScrollView.getScrollY(), Math.abs(dx) << 7 >> mAlbumThread.getPageTransitionSpeed());
			mAlbumThread.updatePageScrolling(PREVIOUS_PAGE);
		}
	}

	public void scrollToCurrentPage() {
		final int offset = mImageView.getOffset();
		final int dx = -offset;
			
		mScroller.startScroll(offset, mScrollView.getScrollY(), dx, 0, Math.abs(dx) << 7 >> mAlbumThread.getPageTransitionSpeed());
		mAlbumThread.updatePageScrolling(CURRENT_PAGE);
	}
	
	public void scrollToNextPage() {
		if (!mAlbumThread.isLastPage()) {
			final int offset = mImageView.getOffset() + mScrollView.getScrollX();
			final int dx = Math.max(ComicsParameters.sScreenWidth, mAlbumThread.getPageWidth()) - offset;

			mScroller.startScroll(offset, mScrollView.getScrollY(), dx, -mScrollView.getScrollY(), Math.abs(dx) << 7 >> mAlbumThread.getPageTransitionSpeed());
			mAlbumThread.updatePageScrolling(NEXT_PAGE);
		}
	}
	
	public void changePage(int page) {
		mAlbumThread.changePage(page);
	}
	
	public void showPageNumber(int page, int pages, int duration) {
		mOverlay.show(String.valueOf(page + 1) + "/" + String.valueOf(pages), duration);
	}

	boolean openIntentFolder(Intent i) {
		// check if current Intent was started from BrowserActivity
		int requestCode = getIntent().getExtras().getInt("requestCode");

		if (requestCode != REQUEST_VIEWER) {
			// start BrowserActivity if not already started
			startActivity(new Intent(this, BrowserActivity.class));
		} else {
			setResult(RESULT_URL, i);
			finish();
		}
		
		return true;
	}
	
	@Override
	public boolean openLastFolder() {
		final Intent intent = getIntent();
		int requestCode = intent.getExtras().getInt("requestCode");

		if (requestCode != REQUEST_VIEWER) {
			startActivity(new Intent(this, BrowserActivity.class));
		} else {
			intent.setDataAndType(mAlbumThread.getAlbumUri(), mAlbumThread.getAlbum().getMimeType());
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

	public boolean onSwapNextPage() {
		return mImageView.swapNext();
	}

	public boolean onSwapPreviousPage() {
		return mImageView.swapPrevious();
	}

	public boolean onPrepareNextPage(int newPage, int oldPage) {
		if (newPage == oldPage && mImageView.getNextBitmap() != null) return false;

		mImageView.recycleNextBitmap();

		return true;
	}

	public boolean onPreparePreviousPage(int newPage, int oldPage) {
		if (newPage == oldPage && mImageView.getPreviousBitmap() != null) return false;

		mImageView.recyclePreviousBitmap();

		return true;
	}

	public boolean onPrepareCurrentPage(int newPage, int oldPage) {
		if (newPage == oldPage && mImageView.getCurrentBitmap() != null) return false;

		mImageView.recyclePreviousBitmap();

		return true;
	}
	
	public boolean onUpdateNextPage(Bitmap bitmap) {
		mImageView.setNextBitmap(bitmap);
		return true;
	}

	public boolean onUpdatePreviousPage(Bitmap bitmap) {
		mImageView.setPreviousBitmap(bitmap);
		return true;
	}
	
	public boolean onUpdateCurrentPage(Bitmap bitmap) {
		mImageView.setCurrentBitmap(bitmap);
		return true;
	}

	public void onDisplayPageNumber(int page, int pages, int duration) {
		showPageNumber(page, pages, duration);
	}

	public void onPageChanged() {
		mScrollView.scrollTo(0, 0);

		// preload next page
		mAlbumThread.updateNextPage();

		// preload previous page
		mAlbumThread.updatePreviousPage();
		
		mAlbumThread.saveCurrentAlbum();
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
	}

	public void onPageScrolled(int page) {
		if (mScroller.computeScrollOffset()) {
			mImageView.setOffset(mScroller.getCurrX());

			// if changing page, set position to left
			if (page != CURRENT_PAGE) {
				mScrollView.scrollTo(0, mScroller.getCurrY());
			}

			if (!mScroller.isFinished()) {
				mAlbumThread.updatePageScrolling(page);
			} else {
				if (page == NEXT_PAGE) {
					mAlbumThread.nextPage();
				} else if (page == PREVIOUS_PAGE) {
					mAlbumThread.previousPage();
				}
			}
		}
	}

	public void onOpenBegin() {
		showDialog(CommonActivity.DIALOG_WAIT);
	}

	public void onOpenEnd() {
		dismissDialog(CommonActivity.DIALOG_WAIT);
	}

	public Context getContext() {
		return this;
	}
}
