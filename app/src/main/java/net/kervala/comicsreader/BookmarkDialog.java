/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2018 Cedric OCHS
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

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;

public class BookmarkDialog extends Dialog implements View.OnClickListener {
	private EditText mTitleEdit;
	private EditText mUrlEdit;
	private Button mOkButton;
	private Button mCancelButton;
	private WeakReference<Handler> mHandler;
	private int mId;

	public BookmarkDialog(Context context, Handler handler) {
		super(context);
		
		mHandler = new WeakReference<>(handler);

		Window window = getWindow();
		
		// must be called before setContentView
		window.requestFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.dialog_bookmark);

		// must be called after setContentView
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); 
		
		mTitleEdit = (EditText)findViewById(R.id.bookmark_title);
		mUrlEdit = (EditText)findViewById(R.id.bookmark_url);

		mOkButton = (Button)findViewById(R.id.ok);
		mOkButton.setOnClickListener(this);

		mCancelButton = (Button)findViewById(R.id.cancel);
		mCancelButton.setOnClickListener(this);
	}
	
	public void setTitle(String title) {
		mTitleEdit.setText(title);
		mTitleEdit.requestFocus();
	}

	public void setUrl(String url) {
		mUrlEdit.setText(url);
	}

	public void setId(int id) {
		mId = id;
	}
	
	public void onClick(View v) {
		if (v == mOkButton) {
			Message msg = mHandler.get().obtainMessage(BrowserActivity.ACTION_BOOKMARK);
			Bundle b = new Bundle();
			b.putString("title", mTitleEdit.getText().toString());
			b.putString("url", mUrlEdit.getText().toString());
			b.putInt("id", mId);
			msg.setData(b);
			mHandler.get().sendMessage(msg);
			dismiss();
		} else {
			cancel();
		}
	}
}
