/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2015 Cedric OCHS
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
import java.util.List;
import java.util.Stack;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ThumbnailAdapter extends BaseAdapter {
	private final Stack<ThumbnailItem> mQueue = new Stack<ThumbnailItem>();
	private final List<ThumbnailItem> mItems;
	private final WeakReference<Handler> mHandler;
	private final LayoutInflater mInflater;
	private final int mItemsCount;
	private final int mResItem;
	private ItemsLoader mLoaderThread;
	private boolean mInit = false;

	public ThumbnailAdapter(Context context, Handler handler, ArrayList<ThumbnailItem> items, int resItem) {
		mItems = items;
		mItemsCount = items.size();
		mHandler = new WeakReference<Handler>(handler);
		mResItem = resItem;
		mInit = true;

		// Cache the LayoutInflate to avoid asking for a new one each time.
		mInflater = LayoutInflater.from(context);
	}

	public void init() {
		mInit = true;
	}

	public void refresh() {
		mInit = true;

		notifyDataSetChanged();
	}

	public void reset() {
		mInit = false;

		stopThread();

		mHandler.get().post(new Runnable() {
			public void run() {
				// recycle all items
				for(ThumbnailItem item: mItems) {
					item.recycle();
				}

				// force reloading all drawables to be sure no recycled bitmaps are still used
				notifyDataSetChanged();
			}
		});
	}

	public void stopThread() {
		if (mLoaderThread != null) {
			mLoaderThread.interrupt();
			mLoaderThread = null;
		}
	}

	public boolean optimize(int first, int last) {
		// if memory used for bitmaps is greater than MAX_USED_MEMORY
		if (ThumbnailItem.sUsedMemory < ThumbnailItem.MAX_USED_MEMORY) return false;

		int count = (last - first + 1) * 3;

		// recycle previous bitmaps
		if (first > count) {
			for(int i = 0; i < first - count; ++i) {
				mItems.get(i).recycle();
			}
		}

		// recycle next bitmaps
		if (last < mItemsCount - count) {
			for(int i = last + count; i < mItemsCount; ++i) {
				mItems.get(i).recycle();
			}
		}

		return true;
	}

	public int getCount() {
		return mItemsCount;
	}

	public Object getItem(int position) {
		return mItems.get(position);
	}

	public long getItemId(int position) {
		return position;
	}

	public int getItemPosition(String text) {
		if (text != null) {
			for(int i = 0; i < mItems.size(); ++i) {
				if (text.equals(mItems.get(i).getText())) return i;
			}
		}

		return -1;
	}

	public View getView(int position, View convertView, ViewGroup parent) {
		final ThumbnailItem item = mItems.get(position);

		if (convertView == null) {
			convertView = mInflater.inflate(mResItem, null);
		}

		item.index = position;
		item.updateView((TextView)convertView);
		convertView.setTag(item);

		// add item to the queue only if icon not yet displayed
		if (mInit && item.getStatus() < ThumbnailItem.STATUS_UPDATED) {
			addItem(item);
		}

		return convertView;
	}

	private void addItem(ThumbnailItem item) {
		// add item to the queue
		synchronized (mQueue) {
			mQueue.push(item);
			mQueue.notify();
		}

		if (mLoaderThread == null) {
			mLoaderThread = new ItemsLoader();
			mLoaderThread.setPriority(Thread.MIN_PRIORITY);
		}

		// start thread if it's not started yet
		if (mLoaderThread.getState() == Thread.State.NEW) {
			mLoaderThread.start();
		}
	}

	private class ItemsLoader extends Thread {
		@Override
		public void run() {
			try {
				while (!Thread.interrupted()) {
					ThumbnailItem item = null;

					// thread waits until there are any images to load in the queue
					synchronized (mQueue) {
						if (mQueue.size() == 0) {
							mQueue.wait();
						} else {
							item = mQueue.pop();
						}
					}

					if (item != null) {
						// update icon
						if (item.update() && item.getStatus() == ThumbnailItem.STATUS_UPDATED) {
							if (Thread.interrupted()) break;

							// ask to refresh this item
							if (item.index != -1) {
								final Message msg = mHandler.get().obtainMessage(BrowserActivity.ACTION_UPDATE_ITEM);
								final Bundle bundle = new Bundle();
								bundle.putInt("index", item.index);
								msg.setData(bundle);
								mHandler.get().sendMessage(msg);
							}
						} else {
							// an error occurred, put to queue again
//							addItem(item);
						}
					}
				}
			} catch (InterruptedException e) {
				// allows thread to exit
			}
		}
	}
}
