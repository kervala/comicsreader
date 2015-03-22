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

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import android.os.Handler;

public class ComicsAuthenticator extends Authenticator {
	private Handler mHandler;
	private String mLogin;
	private String mPassword;
	private boolean mValidated = false;
	private boolean mReset = false;
	static public ComicsAuthenticator sInstance;
	
	public ComicsAuthenticator(Handler handler) {
		mHandler = handler;
		sInstance = this;
	}
	
	@Override
	protected PasswordAuthentication getPasswordAuthentication() {
		if (mLogin == null || mPassword == null) {
			mHandler.sendEmptyMessage(BrowserActivity.ACTION_ASK_LOGIN);

			// wait until dialog is validated
			synchronized(this) {
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (!mValidated) return null;
		}
		
		if (mReset && mLogin != null && mPassword != null) {
			mReset = false;

			return new PasswordAuthentication(mLogin, mPassword.toCharArray());
		}
		
		return null;
	}

	/**
	 * Set login
	 * 
	 * @param login Login entered by user
	 */
	public void setLogin(String login) {
		mLogin = login;
	}

	/**
	 * Set password
	 * 
	 * @param password Password entered by user
	 */
	public void setPassword(String password) {
		mPassword = password;
	}
	
	/**
	 * Reset all variables before asking for a login and password
	 */
	public void reset() {
		mReset = true;
		mValidated = false;
	}

	/**
	 * Set whether the user clicked on OK or Cancel in login dialog
	 * 
	 * @param on true if clicked on OK
	 */
	public void validate(boolean on) {
		mValidated = on;

		synchronized(this) {
			notify();
		}
	}
	
	public boolean isValidated() {
		return mValidated;
	}
	
	/**
	 * Set if authentication was a success or a failure
	 * 
	 * @param result true if a success 
	 */
	public void setResult(boolean result) {
		if (!result) {
			mLogin = null;
			mPassword = null;
		}
	}
}
