/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2011-2018 Cedric OCHS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kervala.comicsreader;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.Scroller;

import java.util.List;

/**
 * Layout container for a view hierarchy that can be scrolled by the user,
 * allowing it to be larger than the physical display. A FullScrollView is a
 * {@link FrameLayout}, meaning you should place one child in it containing the
 * entire contents to scroll; this child may itself be a layout manager with a
 * complex hierarchy of objects. A child that is often used is a
 * {@link LinearLayout} in a vertical orientation, presenting a vertical array
 * of top-level items that the user can scroll through.
 * 
 * <p>
 * The {@link TextView} class also takes care of its own scrolling, so does not
 * require a ScrollView, but using the two together is possible to achieve the
 * effect of a text view within a larger container.
 * 
 * <p>
 * FullScrollView supports both vertical and horizontal scrolling.
 */
public class FullScrollView extends FrameLayout {
	static final String TAG = "FullScrollView";

	static final int ANIMATED_SCROLL_GAP = 250;

	static final float MAX_SCROLL_FACTOR = 0.5f;

	private long mLastScroll;

	private final Rect mTempRect = new Rect();
	private Scroller mScroller;

	/**
	 * Flag to indicate that we are moving focus ourselves. This is so the code
	 * that watches for focus changes initiated outside this ScrollView knows
	 * that it does not have to do anything.
	 */
	private boolean mScrollViewMovedFocus;

	/**
	 * Position of the last motion event.
	 */
	private float mLastMotionX;
	private float mLastMotionY;

	/**
	 * True when the layout has changed but the traversal has not come through
	 * yet. Ideally the view hierarchy would keep track of this for us.
	 */
	private boolean mIsLayoutDirty = true;

	/**
	 * The child to give focus to in the event that a child has requested focus
	 * while the layout is dirty. This prevents the scroll from being wrong if
	 * the child has not been laid out before requesting focus.
	 */
	private View mChildToScrollTo = null;

	/**
	 * True if the user is currently dragging this FullScrollView around. This
	 * is not the same as 'is being flinged', which can be checked by
	 * mScroller.isFinished() (flinging begins when the user lifts his finger).
	 */
	private boolean mIsBeingDragged = false;

	/**
	 * Determines speed during touch scrolling
	 */
	private VelocityTracker mVelocityTracker;

	/**
	 * When set to true, the scroll view measure its child to make it fill the
	 * currently visible area.
	 */
	private boolean mFillViewport;

	/**
	 * Whether arrow scrolling is animated.
	 */
	private boolean mSmoothScrollingEnabled = true;

	private int mTouchSlop;
	private int mMinimumVelocity;
	private int mMaximumVelocity;
	
	private OnSizeChangedListener mSizeChangedListener;

	public FullScrollView(Context context) {
		this(context, null);
	}

	public FullScrollView(Context context, AttributeSet attrs) {
		this(context, attrs, android.R.style.Widget_ScrollView);
	}

	public FullScrollView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		initFullScrollView();

		setFillViewport(true);
	}
	
	public void setSizeChangedListener(OnSizeChangedListener listener) {
		mSizeChangedListener = listener;
	}

	@Override
	protected float getTopFadingEdgeStrength() {
		if (getChildCount() == 0) {
			return 0.0f;
		}

		final int length = getVerticalFadingEdgeLength();
		if (getScrollY() < length) {
			return getScrollY() / (float) length;
		}

		return 1.0f;
	}

	@Override
	protected float getBottomFadingEdgeStrength() {
		if (getChildCount() == 0) {
			return 0.0f;
		}

		final int length = getVerticalFadingEdgeLength();
		final int bottomEdge = getHeight() - getPaddingBottom();
		final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
		if (span < length) {
			return span / (float) length;
		}

		return 1.0f;
	}

	@Override
	protected float getLeftFadingEdgeStrength() {
		if (getChildCount() == 0) {
			return 0.0f;
		}

		final int length = getHorizontalFadingEdgeLength();
		if (getScrollX() < length) {
			return getScrollX() / (float) length;
		}

		return 1.0f;
	}

	@Override
	protected float getRightFadingEdgeStrength() {
		if (getChildCount() == 0) {
			return 0.0f;
		}

		final int length = getHorizontalFadingEdgeLength();
		final int rightEdge = getWidth() - getPaddingRight();
		final int span = getChildAt(0).getRight() - getScrollX() - rightEdge;
		if (span < length) {
			return span / (float) length;
		}

		return 1.0f;
	}

	/**
	 * @return The maximum amount this scroll view will scroll in response to an
	 *         arrow event.
	 */
	public int getMaxScrollAmountX() {
		return (int) (MAX_SCROLL_FACTOR * (getRight() - getLeft()));
	}

	/**
	 * @return The maximum amount this scroll view will scroll in response to an
	 *         arrow event.
	 */
	public int getMaxScrollAmountY() {
		return (int) (MAX_SCROLL_FACTOR * (getBottom() - getTop()));
	}

	private void initFullScrollView() {
		mScroller = new Scroller(getContext());
		setFocusable(true);
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setWillNotDraw(false);
		final ViewConfiguration configuration = ViewConfiguration.get(getContext());
		mTouchSlop = configuration.getScaledTouchSlop();
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
	}

	@Override
	public void addView(View child) {
		if (getChildCount() > 0) {
			throw new IllegalStateException(
					"FullScrollView can host only one direct child");
		}

		super.addView(child);
	}

	@Override
	public void addView(View child, int index) {
		if (getChildCount() > 0) {
			throw new IllegalStateException(
					"FullScrollView can host only one direct child");
		}

		super.addView(child, index);
	}

	@Override
	public void addView(View child, ViewGroup.LayoutParams params) {
		if (getChildCount() > 0) {
			throw new IllegalStateException(
					"FullScrollView can host only one direct child");
		}

		super.addView(child, params);
	}

	@Override
	public void addView(View child, int index, ViewGroup.LayoutParams params) {
		if (getChildCount() > 0) {
			throw new IllegalStateException(
					"FullScrollView can host only one direct child");
		}

		super.addView(child, index, params);
	}

	/**
	 * @return Returns true this FullScrollView can be scrolled
	 */
	public boolean canScroll() {
		View child = getChildAt(0);
		if (child != null) {
			int childWidth = child.getWidth();
			int childHeight = child.getHeight();
			return (getWidth() < childWidth + getPaddingLeft()
					+ getPaddingRight())
					|| (getHeight() < childHeight + getPaddingTop()
							+ getPaddingBottom());
		}
		return false;
	}

	/**
	 * Indicates whether this ScrollView's content is stretched to fill the
	 * viewport.
	 * 
	 * @return True if the content fills the viewport, false otherwise.
	 */
	public boolean isFillViewport() {
		return mFillViewport;
	}

	/**
	 * Indicates this ScrollView whether it should stretch its content height to
	 * fill the viewport or not.
	 * 
	 * @param fillViewport
	 *            True to stretch the content's height to the viewport's
	 *            boundaries, false otherwise.
	 */
	public void setFillViewport(boolean fillViewport) {
		if (fillViewport != mFillViewport) {
			mFillViewport = fillViewport;
			requestLayout();
		}
	}

	/**
	 * @return Whether arrow scrolling will animate its transition.
	 */
	public boolean isSmoothScrollingEnabled() {
		return mSmoothScrollingEnabled;
	}

	/**
	 * Set whether arrow scrolling will animate its transition.
	 * 
	 * @param smoothScrollingEnabled
	 *            whether arrow scrolling will animate its transition
	 */
	public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
		mSmoothScrollingEnabled = smoothScrollingEnabled;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if (!mFillViewport) {
			return;
		}

		if (getChildCount() < 1) {
			return;
		}

		final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		if (heightMode != MeasureSpec.UNSPECIFIED
				&& widthMode != MeasureSpec.UNSPECIFIED) {
			final View child = getChildAt(0);
			int width = getMeasuredWidth();
			int height = getMeasuredHeight();

			final FrameLayout.LayoutParams lp = (LayoutParams) child
					.getLayoutParams();

			int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
					getPaddingLeft() + getPaddingRight(), lp.width);
			int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
					getPaddingTop() + getPaddingBottom(), lp.height);

			if (child.getMeasuredWidth() < width) {
				width -= getPaddingLeft();
				width -= getPaddingRight();
				childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width,
						MeasureSpec.EXACTLY);

			}

			if (child.getMeasuredHeight() < height) {
				height -= getPaddingTop();
				height -= getPaddingBottom();
				childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height,
						MeasureSpec.EXACTLY);

			}

			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// Let the focused view and/or our descendants get the key first
		return super.dispatchKeyEvent(event) || executeKeyEvent(event);
	}

	/**
	 * You can call this function yourself to have the scroll view perform
	 * scrolling from a key event, just as if the event had been dispatched to
	 * it by the view hierarchy.
	 * 
	 * @param event
	 *            The key event to execute.
	 * @return Return true if the event was handled, else false.
	 */
	public boolean executeKeyEvent(KeyEvent event) {
		mTempRect.setEmpty();

		if (!canScroll()) {
			if (isFocused() && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
				View currentFocused = findFocus();
				if (currentFocused == this)
					currentFocused = null;
				View nextFocused = FocusFinder.getInstance().findNextFocus(
						this, currentFocused, View.FOCUS_DOWN);
				return nextFocused != null && nextFocused != this
						&& nextFocused.requestFocus(View.FOCUS_DOWN);
			}
			return false;
		}

		boolean handled = false;
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_DPAD_UP:
				if (!event.isAltPressed()) {
					handled = arrowScroll(View.FOCUS_UP);
				} else {
					handled = fullScroll(View.FOCUS_UP);
				}
				break;
			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (!event.isAltPressed()) {
					handled = arrowScroll(View.FOCUS_DOWN);
				} else {
					handled = fullScroll(View.FOCUS_DOWN);
				}
				break;
			case KeyEvent.KEYCODE_SPACE:
				pageScroll(event.isShiftPressed() ? View.FOCUS_UP
						: View.FOCUS_DOWN);
				break;
			}
		}

		return handled;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		/*
		 * This method JUST determines whether we want to intercept the motion.
		 * If we return true, onMotionEvent will be called and we do the actual
		 * scrolling there.
		 */

		/*
		 * Shortcut the most recurring case: the user is in the dragging state
		 * and he is moving his finger. We want to intercept this motion.
		 */
		final int action = ev.getAction();
		if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
			return true;
		}

		if (!canScroll()) {
			mIsBeingDragged = false;
			return false;
		}

		final float x = ev.getX();
		final float y = ev.getY();

		switch (action) {
		case MotionEvent.ACTION_MOVE: {
			/*
			 * mIsBeingDragged == false, otherwise the shortcut would have
			 * caught it. Check whether the user has moved far enough from his
			 * original down touch.
			 */

			/*
			 * Locally do absolute value. mLastMotionY is set to the y value of
			 * the down event.
			 */
			final int xDiff = (int) Math.abs(x - mLastMotionX);
			final int yDiff = (int) Math.abs(y - mLastMotionY);
			if (yDiff > mTouchSlop || xDiff > mTouchSlop) {
				mIsBeingDragged = true;
				mLastMotionX = x;
				mLastMotionY = y;
			}
			break;
		}

		case MotionEvent.ACTION_DOWN: {
			/* Remember location of down touch */
			mLastMotionX = x;
			mLastMotionY = y;

			/*
			 * If being flinged and user touches the screen, initiate drag;
			 * otherwise don't. mScroller.isFinished should be false when being
			 * flinged.
			 */
			mIsBeingDragged = true; // !mScroller.isFinished();
			break;
		}

		case MotionEvent.ACTION_CANCEL:
		case MotionEvent.ACTION_UP:
			/* Release the drag */
			mIsBeingDragged = false;
			break;
		}

		/*
		 * The only time we want to intercept motion events is if we are in the
		 * drag mode.
		 */
		return mIsBeingDragged;
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
			// Don't handle edge touches immediately -- they may actually belong
			// to one of our
			// descendants.
			return false;
		}

		if (!canScroll()) {
			return false;
		}

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);

		final int action = ev.getAction();
		final float x = ev.getX();
		final float y = ev.getY();

		switch (action) {
		case MotionEvent.ACTION_DOWN: {
			/*
			 * If being flinged and user touches, stop the fling. isFinished
			 * will be false if being flinged.
			 */
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}

			// Remember where the motion event started
			mLastMotionX = x;
			mLastMotionY = y;
			break;
		}
		case MotionEvent.ACTION_MOVE:
			if (mIsBeingDragged) {
				// Scroll to follow the motion event
				final int deltaX = (int) (mLastMotionX - x);
				final int deltaY = (int) (mLastMotionY - y);
				mLastMotionX = x;
				mLastMotionY = y;

				int scrollDeltaX = 0;
				int scrollDeltaY = 0;

				if (deltaX < 0) {
					if (getScrollX() > 0) {
						scrollDeltaX = deltaX;
					}
				} else if (deltaX > 0) {
					final int rightEdge = getWidth() - getPaddingRight();
					final int availableToScrollX = getChildAt(0).getRight()
							- getScrollX() - rightEdge;
					if (availableToScrollX > 0) {
						scrollDeltaX = Math.min(availableToScrollX, deltaX);
					}
				}

				if (deltaY < 0) {
					if (getScrollY() > 0) {
						scrollDeltaY = deltaY;
					}
				} else if (deltaY > 0) {
					final int bottomEdge = getHeight() - getPaddingBottom();
					final int availableToScrollY = getChildAt(0).getBottom()
							- getScrollY() - bottomEdge;
					if (availableToScrollY > 0) {
						scrollDeltaY = Math.min(availableToScrollY, deltaY);
					}
				}

				if (scrollDeltaX != 0 || scrollDeltaY != 0) {
					scrollBy(scrollDeltaX, scrollDeltaY);
				}
			}

			break;
		case MotionEvent.ACTION_UP:
			final VelocityTracker velocityTracker = mVelocityTracker;
			velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
			int initialVelocityX = (int) velocityTracker.getXVelocity();
			int initialVelocityY = (int) velocityTracker.getYVelocity();

			if (((Math.abs(initialVelocityX) > mMinimumVelocity) || (Math
					.abs(initialVelocityY) > mMinimumVelocity))
					&& getChildCount() > 0) {
				fling(-initialVelocityX, -initialVelocityY);
			}

			if (mVelocityTracker != null) {
				mVelocityTracker.recycle();
				mVelocityTracker = null;
			}
		}
		return true;
	}

	/**
	 * <p>
	 * Finds the next focusable component that fits in this View's bounds
	 * (excluding fading edges) pretending that this View's top is located at
	 * the parameter top.
	 * </p>
	 * 
	 * @param topFocus
	 *            look for a candidate is the one at the top of the bounds if
	 *            topFocus is true, or at the bottom of the bounds if topFocus
	 *            is false
	 * @param top
	 *            the top offset of the bounds in which a focusable must be
	 *            found (the fading edge is assumed to start at this position)
	 * @param preferredFocusable
	 *            the View that has highest priority and will be returned if it
	 *            is within my bounds (null is valid)
	 * @return the next focusable component in the bounds or null if none can be
	 *         found
	 */
	private View findFocusableViewInMyBounds(final boolean leftFocus,
			final boolean topFocus, final int left, final int top,
			View preferredFocusable) {
		/*
		 * The fading edge's transparent side should be considered for focus
		 * since it's mostly visible, so we divide the actual fading edge length
		 * by 2.
		 */
		final int fadingEdgeLengthX = getHorizontalFadingEdgeLength() / 2;
		final int fadingEdgeLengthY = getVerticalFadingEdgeLength() / 2;
		final int leftWithoutFadingEdge = left + fadingEdgeLengthX;
		final int topWithoutFadingEdge = top + fadingEdgeLengthY;
		final int rightWithoutFadingEdge = left + getWidth()
				- fadingEdgeLengthX;
		final int bottomWithoutFadingEdge = top + getHeight()
				- fadingEdgeLengthY;

		if ((preferredFocusable != null)
				&& (preferredFocusable.getTop() < bottomWithoutFadingEdge)
				&& (preferredFocusable.getBottom() > topWithoutFadingEdge)
				&& (preferredFocusable.getLeft() < leftWithoutFadingEdge)
				&& (preferredFocusable.getRight() > rightWithoutFadingEdge)) {
			return preferredFocusable;
		}

		return findFocusableViewInBounds(leftFocus, topFocus,
				leftWithoutFadingEdge, topWithoutFadingEdge,
				rightWithoutFadingEdge, bottomWithoutFadingEdge);
	}

	/**
	 * <p>
	 * Finds the next focusable component that fits in the specified bounds.
	 * </p>
	 * 
	 * @param topFocus
	 *            look for a candidate is the one at the top of the bounds if
	 *            topFocus is true, or at the bottom of the bounds if topFocus
	 *            is false
	 * @param top
	 *            the top offset of the bounds in which a focusable must be
	 *            found
	 * @param bottom
	 *            the bottom offset of the bounds in which a focusable must be
	 *            found
	 * @return the next focusable component in the bounds or null if none can be
	 *         found
	 */
	private View findFocusableViewInBounds(boolean leftFocus, boolean topFocus,
			int left, int top, int right, int bottom) {

		List<View> focusables = getFocusables(View.FOCUS_FORWARD);
		View focusCandidate = null;

		/*
		 * A fully contained focusable is one where its top is below the bound's
		 * top, and its bottom is above the bound's bottom. A partially
		 * contained focusable is one where some part of it is within the
		 * bounds, but it also has some part that is not within bounds. A fully
		 * contained focusable is preferred to a partially contained focusable.
		 */
		boolean foundFullyContainedFocusable = false;

		int count = focusables.size();
		for (int i = 0; i < count; i++) {
			View view = focusables.get(i);
			int viewLeft = view.getLeft();
			int viewTop = view.getTop();
			int viewRight = view.getRight();
			int viewBottom = view.getBottom();

			if (top < viewBottom && viewTop < bottom && left < viewRight
					&& viewLeft < right) {
				/*
				 * the focusable is in the target area, it is a candidate for
				 * focusing
				 */

				final boolean viewIsFullyContained = (top < viewTop)
						&& (viewBottom < bottom) && (left < viewLeft)
						&& (viewRight < right);

				if (focusCandidate == null) {
					/* No candidate, take this one */
					focusCandidate = view;
					foundFullyContainedFocusable = viewIsFullyContained;
				} else {
					final boolean viewIsCloserToBoundary = (topFocus && viewTop < focusCandidate
							.getTop())
							|| (!topFocus && viewBottom > focusCandidate
									.getBottom())
							|| (leftFocus && viewLeft < focusCandidate
									.getLeft())
							|| (!leftFocus && viewRight > focusCandidate
									.getRight());

					if (foundFullyContainedFocusable) {
						if (viewIsFullyContained && viewIsCloserToBoundary) {
							/*
							 * We're dealing with only fully contained views, so
							 * it has to be closer to the boundary to beat our
							 * candidate
							 */
							focusCandidate = view;
						}
					} else {
						if (viewIsFullyContained) {
							/*
							 * Any fully contained view beats a partially
							 * contained view
							 */
							focusCandidate = view;
							foundFullyContainedFocusable = true;
						} else if (viewIsCloserToBoundary) {
							/*
							 * Partially contained view beats another partially
							 * contained view if it's closer
							 */
							focusCandidate = view;
						}
					}
				}
			}
		}

		return focusCandidate;
	}

	/**
	 * <p>
	 * Handles scrolling in response to a "page up/down" shortcut press. This
	 * method will scroll the view by one page up or down and give the focus to
	 * the topmost/bottommost component in the new visible area. If no component
	 * is a good candidate for focus, this scrollview reclaims the focus.
	 * </p>
	 * 
	 * @param direction
	 *            the scroll direction: {@link android.view.View#FOCUS_UP} to go
	 *            one page up or {@link android.view.View#FOCUS_DOWN} to go one
	 *            page down
	 * @return true if the key event is consumed by this method, false otherwise
	 */
	public boolean pageScroll(int direction) {
		boolean down = direction == View.FOCUS_DOWN;
		boolean right = direction == View.FOCUS_RIGHT;
		int width = getWidth();
		int height = getHeight();

		if (right) {
			mTempRect.left = getScrollX() + width;
			int count = getChildCount();
			if (count > 0) {
				View view = getChildAt(count - 1);
				if (mTempRect.left + width > view.getRight()) {
					mTempRect.left = view.getRight() - width;
				}
			}
		} else {
			mTempRect.left = getScrollX() - width;
			if (mTempRect.left < 0) {
				mTempRect.left = 0;
			}
		}
		mTempRect.right = mTempRect.left + width;

		if (down) {
			mTempRect.top = getScrollY() + height;
			int count = getChildCount();
			if (count > 0) {
				View view = getChildAt(count - 1);
				if (mTempRect.top + height > view.getBottom()) {
					mTempRect.top = view.getBottom() - height;
				}
			}
		} else {
			mTempRect.top = getScrollY() - height;
			if (mTempRect.top < 0) {
				mTempRect.top = 0;
			}
		}
		mTempRect.bottom = mTempRect.top + height;

		return scrollAndFocus(direction, mTempRect.left, mTempRect.top,
				mTempRect.right, mTempRect.bottom);
	}

	/**
	 * <p>
	 * Handles scrolling in response to a "home/end" shortcut press. This method
	 * will scroll the view to the top or bottom and give the focus to the
	 * topmost/bottommost component in the new visible area. If no component is
	 * a good candidate for focus, this scrollview reclaims the focus.
	 * </p>
	 * 
	 * @param direction
	 *            the scroll direction: {@link android.view.View#FOCUS_UP} to go
	 *            the top of the view or {@link android.view.View#FOCUS_DOWN} to
	 *            go the bottom
	 * @return true if the key event is consumed by this method, false otherwise
	 */
	public boolean fullScroll(int direction) {
		boolean down = direction == View.FOCUS_DOWN;
		boolean right = direction == View.FOCUS_RIGHT;
		int width = getWidth();
		int height = getHeight();

		mTempRect.left = 0;
		mTempRect.top = 0;
		mTempRect.right = width;
		mTempRect.bottom = height;

		if (right) {
			int count = getChildCount();
			if (count > 0) {
				View view = getChildAt(count - 1);
				mTempRect.right = view.getRight();
				mTempRect.left = mTempRect.right - width;
			}
		}

		if (down) {
			int count = getChildCount();
			if (count > 0) {
				View view = getChildAt(count - 1);
				mTempRect.bottom = view.getBottom();
				mTempRect.top = mTempRect.bottom - height;
			}
		}

		return scrollAndFocus(direction, mTempRect.left, mTempRect.top,
				mTempRect.right, mTempRect.bottom);
	}

	/**
	 * <p>
	 * Scrolls the view to make the area defined by <code>top</code> and
	 * <code>bottom</code> visible. This method attempts to give the focus to a
	 * component visible in this area. If no component can be focused in the new
	 * visible area, the focus is reclaimed by this scrollview.
	 * </p>
	 * 
	 * @param direction
	 *            the scroll direction: {@link android.view.View#FOCUS_UP} to go
	 *            upward {@link android.view.View#FOCUS_DOWN} to downward
	 * @param top
	 *            the top offset of the new area to be made visible
	 * @param bottom
	 *            the bottom offset of the new area to be made visible
	 * @return true if the key event is consumed by this method, false otherwise
	 */
	private boolean scrollAndFocus(int direction, int left, int top, int right,
			int bottom) {
		boolean handled = true;

		int width = getWidth();
		int height = getHeight();
		int containerLeft = getScrollX();
		int containerTop = getScrollY();
		int containerRight = containerLeft + width;
		int containerBottom = containerTop + height;
		boolean tleft = direction == View.FOCUS_LEFT;
		boolean up = direction == View.FOCUS_UP;

		View newFocused = findFocusableViewInBounds(tleft, up, left, top,
				right, bottom);
		if (newFocused == null) {
			newFocused = this;
		}

		int deltaX = 0;
		int deltaY = 0;

		if (left < containerLeft || right > containerRight) {
			deltaX = tleft ? (left - containerLeft) : (right - containerRight);
		}

		if (top < containerTop || bottom > containerBottom) {
			deltaY = up ? (top - containerTop) : (bottom - containerBottom);
		}

		if (deltaX != 0 || deltaY != 0) {
			doScroll(deltaX, deltaY);
		} else {
			handled = false;
		}

		if (newFocused != findFocus() && newFocused.requestFocus(direction)) {
			mScrollViewMovedFocus = true;
			mScrollViewMovedFocus = false;
		}

		return handled;
	}

	/**
	 * Handle scrolling in response to an up or down arrow click.
	 * 
	 * @param direction
	 *            The direction corresponding to the arrow key that was pressed
	 * @return True if we consumed the event, false otherwise
	 */
	public boolean arrowScroll(int direction) {

		View currentFocused = findFocus();
		if (currentFocused == this)
			currentFocused = null;

		View nextFocused = FocusFinder.getInstance().findNextFocus(this,
				currentFocused, direction);

		final int maxJumpX = getMaxScrollAmountX();
		final int maxJumpY = getMaxScrollAmountY();

		if (nextFocused != null
				&& isWithinDeltaOfScreen(nextFocused, maxJumpX, maxJumpY)) {
			nextFocused.getDrawingRect(mTempRect);
			offsetDescendantRectToMyCoords(nextFocused, mTempRect);
			int scrollDeltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
			int scrollDeltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);
			doScroll(scrollDeltaX, scrollDeltaY);
			nextFocused.requestFocus(direction);
		} else {
			// no new focus
			int scrollDeltaX = maxJumpX;
			int scrollDeltaY = maxJumpY;

			if (direction == View.FOCUS_LEFT && getScrollX() < scrollDeltaX) {
				scrollDeltaX = getScrollX();
			} else if (direction == View.FOCUS_RIGHT) {
				if (getChildCount() > 0) {
					int daRight = getChildAt(0).getRight();

					int screenRight = getScrollX() + getWidth();

					if (daRight - screenRight < maxJumpY) {
						scrollDeltaY = daRight - screenRight;
					}
				}
			}

			if (direction == View.FOCUS_UP && getScrollY() < scrollDeltaY) {
				scrollDeltaY = getScrollY();
			} else if (direction == View.FOCUS_DOWN) {
				if (getChildCount() > 0) {
					int daBottom = getChildAt(0).getBottom();

					int screenBottom = getScrollY() + getHeight();

					if (daBottom - screenBottom < maxJumpY) {
						scrollDeltaY = daBottom - screenBottom;
					}
				}
			}

			if (scrollDeltaX == 0 && scrollDeltaY == 0) {
				return false;
			}

			doScroll(direction == View.FOCUS_RIGHT ? scrollDeltaX
					: -scrollDeltaX,
					direction == View.FOCUS_DOWN ? scrollDeltaY : -scrollDeltaY);
		}

		if (currentFocused != null && currentFocused.isFocused()
				&& isOffScreen(currentFocused)) {
			// previously focused item still has focus and is off screen, give
			// it up (take it back to ourselves)
			// (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we
			// are
			// sure to
			// get it)
			final int descendantFocusability = getDescendantFocusability(); // save
			setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
			requestFocus();
			setDescendantFocusability(descendantFocusability); // restore
		}
		return true;
	}

	/**
	 * @return whether the descendant of this scroll view is scrolled off
	 *         screen.
	 */
	private boolean isOffScreen(View descendant) {
		return !isWithinDeltaOfScreen(descendant, 0, 0);
	}

	/**
	 * @return whether the descendant of this scroll view is within delta pixels
	 *         of being on the screen.
	 */
	private boolean isWithinDeltaOfScreen(View descendant, int deltaX,
			int deltaY) {
		descendant.getDrawingRect(mTempRect);
		offsetDescendantRectToMyCoords(descendant, mTempRect);

		return ((mTempRect.right + deltaX) >= getScrollX() && (mTempRect.left - deltaX) <= (getScrollX() + getWidth()))
				&& ((mTempRect.bottom + deltaY) >= getScrollY() && (mTempRect.top - deltaY) <= (getScrollY() + getHeight()));
	}

	/**
	 * Smooth scroll by a X and Y delta
	 * 
	 * @param deltaX
	 *            the number of pixels to scroll by on the X axis
	 * @param deltaY
	 *            the number of pixels to scroll by on the Y axis
	 */
	private void doScroll(int deltaX, int deltaY) {
		if (deltaX != 0 || deltaY != 0) {
			if (mSmoothScrollingEnabled) {
				smoothScrollBy(deltaX, deltaY);
			} else {
				scrollBy(deltaX, deltaY);
			}
		}
	}

	/**
	 * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
	 * 
	 * @param dx
	 *            the number of pixels to scroll by on the X axis
	 * @param dy
	 *            the number of pixels to scroll by on the Y axis
	 */
	public final void smoothScrollBy(int dx, int dy) {
		long duration = AnimationUtils.currentAnimationTimeMillis()
				- mLastScroll;
		if (duration > ANIMATED_SCROLL_GAP) {
			mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
			invalidate();
		} else {
			if (!mScroller.isFinished()) {
				mScroller.abortAnimation();
			}
			scrollBy(dx, dy);
		}
		mLastScroll = AnimationUtils.currentAnimationTimeMillis();
	}

	/**
	 * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
	 * 
	 * @param x
	 *            the position where to scroll on the X axis
	 * @param y
	 *            the position where to scroll on the Y axis
	 */
	public final void smoothScrollTo(int x, int y) {
		smoothScrollBy(x - getScrollX(), y - getScrollY());
	}

	/**
	 * <p>
	 * The scroll range of a scroll view is the overall width of all of its
	 * children.
	 * </p>
	 */
	@Override
	protected int computeHorizontalScrollRange() {
		final int count = getChildCount();
		final int contentWidth = getWidth() - getPaddingRight()
				- getPaddingLeft();
		if (count == 0) {
			return contentWidth;
		}

		int scrollRange = getChildAt(0).getRight();
		final int scrollX = getScrollX();
		final int overscrollRight = Math.max(0, scrollRange - contentWidth);
		if (scrollX < 0) {
			scrollRange -= scrollX;
		} else if (scrollX > overscrollRight) {
			scrollRange += scrollX - overscrollRight;
		}

		return scrollRange;
	}

	/**
	 * <p>
	 * The scroll range of a scroll view is the overall height of all of its
	 * children.
	 * </p>
	 */
	@Override
	protected int computeVerticalScrollRange() {
		final int count = getChildCount();
		final int contentHeight = getHeight() - getPaddingBottom()
				- getPaddingTop();
		if (count == 0) {
			return contentHeight;
		}

		int scrollRange = getChildAt(0).getBottom();
		final int scrollY = getScrollY();
		final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
		if (scrollY < 0) {
			scrollRange -= scrollY;
		} else if (scrollY > overscrollBottom) {
			scrollRange += scrollY - overscrollBottom;
		}

		return scrollRange;
	}

	@Override
	protected int computeHorizontalScrollOffset() {
		return Math.max(0, super.computeHorizontalScrollOffset());
	}

	@Override
	protected int computeVerticalScrollOffset() {
		return Math.max(0, super.computeVerticalScrollOffset());
	}

	@Override
	protected void measureChild(View child, int parentWidthMeasureSpec,
			int parentHeightMeasureSpec) {
		int childWidthMeasureSpec;
		int childHeightMeasureSpec;

		childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0,
				MeasureSpec.UNSPECIFIED);
		childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0,
				MeasureSpec.UNSPECIFIED);

		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	@Override
	protected void measureChildWithMargins(View child,
			int parentWidthMeasureSpec, int widthUsed,
			int parentHeightMeasureSpec, int heightUsed) {
		final MarginLayoutParams lp = (MarginLayoutParams) child
				.getLayoutParams();

		final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
				lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED);

		final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
				lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);

		child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			// This is called at drawing time by ViewGroup. We don't want to
			// re-show the scrollbars at this point, which scrollTo will do,
			// so we replicate most of scrollTo here.
			//
			// It's a little odd to call onScrollChanged from inside the
			// drawing.
			//
			// It is, except when you remember that computeScroll() is used to
			// animate scrolling. So unless we want to defer the
			// onScrollChanged()
			// until the end of the animated scrolling, we don't really have a
			// choice here.
			//
			// I agree. The alternative, which I think would be worse, is to
			// post
			// something and tell the subclasses later. This is bad because
			// there
			// will be a window where mScrollX/Y is different from what the app
			// thinks it is.
			//
			int oldX = getScrollX();
			int oldY = getScrollY();
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();
			if (getChildCount() > 0) {
				View child = getChildAt(0);
				int scrollX = clamp(x, getWidth() - getPaddingRight()
						- getPaddingLeft(), child.getWidth());
				int scrollY = clamp(y, getHeight() - getPaddingBottom()
						- getPaddingTop(), child.getHeight());
				scrollTo(scrollX, scrollY);
			} else {
				scrollTo(x, y);
			}
			if (oldX != getScrollX() || oldY != getScrollY()) {
				onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
			}

			// Keep on drawing until the animation has finished.
			postInvalidate();
		}
	}

	/**
	 * Scrolls the view to the given child.
	 * 
	 * @param child
	 *            the View to scroll to
	 */
	private void scrollToChild(View child) {
		child.getDrawingRect(mTempRect);

		/* Offset from child's local coordinates to ScrollView coordinates */
		offsetDescendantRectToMyCoords(child, mTempRect);

		int scrollDeltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
		int scrollDeltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);

		if (scrollDeltaX != 0 || scrollDeltaY != 0) {
			scrollBy(scrollDeltaX, scrollDeltaY);
		}
	}

	/**
	 * If rect is off screen, scroll just enough to get it (or at least the
	 * first screen size chunk of it) on screen.
	 * 
	 * @param rect
	 *            The rectangle.
	 * @param immediate
	 *            True to scroll immediately without animation
	 * @return true if scrolling was performed
	 */
	private boolean scrollToChildRect(Rect rect, boolean immediate) {
		final int deltaX = computeScrollDeltaXToGetChildRectOnScreen(rect);
		final int deltaY = computeScrollDeltaYToGetChildRectOnScreen(rect);
		final boolean scroll = (deltaX != 0) || (deltaY != 0);
		if (scroll) {
			if (immediate) {
				scrollBy(deltaX, deltaY);
			} else {
				smoothScrollBy(deltaX, deltaY);
			}
		}
		return scroll;
	}

	/**
	 * Compute the amount to scroll in the Y direction in order to get a
	 * rectangle completely on the screen (or, if taller than the screen, at
	 * least the first screen size chunk of it).
	 * 
	 * @param rect
	 *            The rect.
	 * @return The scroll delta.
	 */
	protected int computeScrollDeltaXToGetChildRectOnScreen(Rect rect) {
		if (getChildCount() == 0)
			return 0;

		int width = getWidth();
		int screenLeft = getScrollX();
		int screenRight = screenLeft + width;

		int fadingEdgeX = getHorizontalFadingEdgeLength();

		// leave room for left fading edge as long as rect isn't at very left
		if (rect.left > 0) {
			screenLeft += fadingEdgeX;
		}

		// leave room for right fading edge as long as rect isn't at very right
		if (rect.right < getChildAt(0).getWidth()) {
			screenRight -= fadingEdgeX;
		}

		int scrollXDelta = 0;

		if (rect.right > screenRight && rect.left > screenLeft) {
			// need to move down to get it in view: move down just enough so
			// that the entire rectangle is in view (or at least the first
			// screen size chunk).

			if (rect.width() > width) {
				// just enough to get screen size chunk on
				scrollXDelta += (rect.left - screenLeft);
			} else {
				// get entire rect at right of screen
				scrollXDelta += (rect.right - screenRight);
			}

			// make sure we aren't scrolling beyond the end of our content
			int right = getChildAt(0).getRight();
			int distanceToRight = right - screenRight;
			scrollXDelta = Math.min(scrollXDelta, distanceToRight);

		} else if (rect.left < screenLeft && rect.right < screenRight) {
			// need to move up to get it in view: move up just enough so that
			// entire rectangle is in view (or at least the first screen
			// size chunk of it).

			if (rect.width() > width) {
				// screen size chunk
				scrollXDelta -= (screenRight - rect.right);
			} else {
				// entire rect at top
				scrollXDelta -= (screenLeft - rect.left);
			}

			// make sure we aren't scrolling any further than the top our
			// content
			scrollXDelta = Math.max(scrollXDelta, -getScrollX());
		}
		return scrollXDelta;
	}

	/**
	 * Compute the amount to scroll in the Y direction in order to get a
	 * rectangle completely on the screen (or, if taller than the screen, at
	 * least the first screen size chunk of it).
	 * 
	 * @param rect
	 *            The rect.
	 * @return The scroll delta.
	 */
	protected int computeScrollDeltaYToGetChildRectOnScreen(Rect rect) {
		if (getChildCount() == 0)
			return 0;

		int height = getHeight();
		int screenTop = getScrollY();
		int screenBottom = screenTop + height;

		int fadingEdgeY = getVerticalFadingEdgeLength();

		// leave room for top fading edge as long as rect isn't at very top
		if (rect.top > 0) {
			screenTop += fadingEdgeY;
		}

		// leave room for bottom fading edge as long as rect isn't at very
		// bottom
		if (rect.bottom < getChildAt(0).getHeight()) {
			screenBottom -= fadingEdgeY;
		}

		int scrollYDelta = 0;

		if (rect.bottom > screenBottom && rect.top > screenTop) {
			// need to move down to get it in view: move down just enough so
			// that the entire rectangle is in view (or at least the first
			// screen size chunk).

			if (rect.height() > height) {
				// just enough to get screen size chunk on
				scrollYDelta += (rect.top - screenTop);
			} else {
				// get entire rect at bottom of screen
				scrollYDelta += (rect.bottom - screenBottom);
			}

			// make sure we aren't scrolling beyond the end of our content
			int bottom = getChildAt(0).getBottom();
			int distanceToBottom = bottom - screenBottom;
			scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

		} else if (rect.top < screenTop && rect.bottom < screenBottom) {
			// need to move up to get it in view: move up just enough so that
			// entire rectangle is in view (or at least the first screen
			// size chunk of it).

			if (rect.height() > height) {
				// screen size chunk
				scrollYDelta -= (screenBottom - rect.bottom);
			} else {
				// entire rect at top
				scrollYDelta -= (screenTop - rect.top);
			}

			// make sure we aren't scrolling any further than the top our
			// content
			scrollYDelta = Math.max(scrollYDelta, -getScrollY());
		}
		return scrollYDelta;
	}

	@Override
	public void requestChildFocus(View child, View focused) {
		if (!mScrollViewMovedFocus) {
			if (!mIsLayoutDirty) {
				scrollToChild(focused);
			} else {
				// The child may not be laid out yet, we can't compute the
				// scroll yet
				mChildToScrollTo = focused;
			}
		}
		super.requestChildFocus(child, focused);
	}

	/**
	 * When looking for focus in children of a scroll view, need to be a little
	 * more careful not to give focus to something that is scrolled off screen.
	 * 
	 * This is more expensive than the default {@link android.view.ViewGroup}
	 * implementation, otherwise this behavior might have been made the default.
	 */
	@Override
	protected boolean onRequestFocusInDescendants(int direction,
			Rect previouslyFocusedRect) {

		// convert from forward / backward notation to up / down / left / right
		// (ugh).
		if (direction == View.FOCUS_FORWARD) {
			direction = View.FOCUS_DOWN;
		} else if (direction == View.FOCUS_BACKWARD) {
			direction = View.FOCUS_UP;
		}

		final View nextFocus = previouslyFocusedRect == null ? FocusFinder
				.getInstance().findNextFocus(this, null, direction)
				: FocusFinder.getInstance().findNextFocusFromRect(this,
						previouslyFocusedRect, direction);

		if (nextFocus == null) {
			return false;
		}

		if (isOffScreen(nextFocus)) {
			return false;
		}

		return nextFocus.requestFocus(direction, previouslyFocusedRect);
	}

	@Override
	public boolean requestChildRectangleOnScreen(View child, Rect rectangle,
			boolean immediate) {
		// offset into coordinate space of this scroll view
		rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop()
				- child.getScrollY());

		return scrollToChildRect(rectangle, immediate);
	}

	@Override
	public void requestLayout() {
		mIsLayoutDirty = true;
		super.requestLayout();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		mIsLayoutDirty = false;
		// Give a child focus if it needs it
		if (mChildToScrollTo != null
				&& isViewDescendantOf(mChildToScrollTo, this)) {
			scrollToChild(mChildToScrollTo);
		}
		mChildToScrollTo = null;

		// Calling this with the present values causes it to re-clam them
		scrollTo(getScrollX(), getScrollY());
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		if (mSizeChangedListener != null) {
			mSizeChangedListener.onSizeChanged(w, h, oldw, oldh);
		}

		View currentFocused = findFocus();
		if (null == currentFocused || this == currentFocused)
			return;

		final int maxJumpX = getRight() - getLeft();
		final int maxJumpY = getBottom() - getTop();

		if (isWithinDeltaOfScreen(currentFocused, maxJumpX, maxJumpY)) {
			currentFocused.getDrawingRect(mTempRect);
			offsetDescendantRectToMyCoords(currentFocused, mTempRect);
			int scrollDeltaX = computeScrollDeltaXToGetChildRectOnScreen(mTempRect);
			int scrollDeltaY = computeScrollDeltaYToGetChildRectOnScreen(mTempRect);
			doScroll(scrollDeltaX, scrollDeltaY);
		}
	}

	/**
	 * Return true if child is an descendant of parent, (or equal to the
	 * parent).
	 */
	private boolean isViewDescendantOf(View child, View parent) {
		if (child == parent) {
			return true;
		}

		final ViewParent theParent = child.getParent();
		return (theParent instanceof ViewGroup)
				&& isViewDescendantOf((View) theParent, parent);
	}

	/**
	 * Fling the scroll view
	 * 
	 * @param velocityX
	 *            The initial velocity in the X direction. Positive numbers mean
	 *            that the finger/cursor is moving right the screen, which means
	 *            we want to scroll towards the left.
	 * @param velocityY
	 *            The initial velocity in the Y direction. Positive numbers mean
	 *            that the finger/cursor is moving down the screen, which means
	 *            we want to scroll towards the top.
	 */
	public void fling(int velocityX, int velocityY) {
		if (getChildCount() > 0) {
			int height = getHeight() - getPaddingBottom() - getPaddingTop();
			int bottom = getChildAt(0).getHeight();
			int width = getWidth() - getPaddingRight() - getPaddingLeft();
			int right = getChildAt(0).getWidth();

			mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY,
					0, right - width, 0, bottom - height);

			final boolean movingRight = velocityX > 0;
			final boolean movingDown = velocityY > 0;

			View newFocused = findFocusableViewInMyBounds(movingRight,
					movingDown, mScroller.getFinalX(), mScroller.getFinalY(),
					findFocus());
			if (newFocused == null) {
				newFocused = this;
			}

			if (newFocused != findFocus()
					&& newFocused.requestFocus(movingDown ? View.FOCUS_DOWN
							: View.FOCUS_UP)) {
				mScrollViewMovedFocus = true;
				mScrollViewMovedFocus = false;
			}

			invalidate();
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * <p>
	 * This version also clamps the scrolling to the bounds of our child.
	 */
	@Override
	public void scrollTo(int x, int y) {
		// we rely on the fact the View.scrollBy calls scrollTo.
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(),
					child.getWidth());
			y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(),
					child.getHeight());
			if (x != getScrollX() || y != getScrollY()) {
				super.scrollTo(x, y);
			}
		}
	}

	private int clamp(int n, int my, int child) {
		if (my >= child || n < 0) {
			/*
			 * my >= child is this case: |--------------- me ---------------|
			 * |------ child ------| or |--------------- me ---------------|
			 * |------ child ------| or |--------------- me ---------------|
			 * |------ child ------|
			 * 
			 * n < 0 is this case: |------ me ------| |-------- child --------|
			 * |-- mScrollX --|
			 */
			return 0;
		}
		if ((my + n) > child) {
			/*
			 * this case: |------ me ------| |------ child ------| |-- mScrollX
			 * --|
			 */
			return child - my;
		}
		return n;
	}
	
	interface OnSizeChangedListener {
		public void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight);
	}
}
