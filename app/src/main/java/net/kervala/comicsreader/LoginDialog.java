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

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class LoginDialog extends Dialog implements View.OnClickListener {
	private EditText mUsernameEdit;
	private EditText mPasswordEdit;
	private CheckBox mRememberPasswordCheckBox;
	private Button mOkButton;
	private Button mCancelButton;
	private WeakReference<Handler> mHandler;

	public LoginDialog(Context context, Handler handler) {
		super(context);
		
		mHandler = new WeakReference<Handler>(handler);

		Window window = getWindow();
		
		// must be called before setContentView
		window.requestFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.dialog_login);

		// must be called after setContentView
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); 
		
		mUsernameEdit = (EditText)findViewById(R.id.username);
		mPasswordEdit = (EditText)findViewById(R.id.password);

		mOkButton = (Button)findViewById(R.id.ok);
		mOkButton.setOnClickListener(this);

		mCancelButton = (Button)findViewById(R.id.cancel);
		mCancelButton.setOnClickListener(this);
		
		mRememberPasswordCheckBox = (CheckBox)findViewById(R.id.remember_password);
	}
	
	public void setUsername(String username) {
		mUsernameEdit.setText(username);
		mUsernameEdit.requestFocus();
	}

	public void setPassword(String password) {
		mPasswordEdit.setText(password);
	}
	
	public void setRememberPassword(boolean remember) {
		mRememberPasswordCheckBox.setChecked(remember);
	}
	
	public void onClick(View v) {
		if (v == mOkButton) {
			Message msg = mHandler.get().obtainMessage(BrowserActivity.ACTION_LOGIN);
			Bundle b = new Bundle();
			b.putString("username", mUsernameEdit.getText().toString());
			b.putString("password", mPasswordEdit.getText().toString());
			b.putBoolean("remember_password", mRememberPasswordCheckBox.isChecked());
			msg.setData(b);
			mHandler.get().sendMessage(msg);
			dismiss();
		} else {
			mHandler.get().sendEmptyMessage(BrowserActivity.ACTION_CANCEL_LOGIN);
			cancel();
		}
	}
}
