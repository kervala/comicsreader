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

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.text.util.Linkify;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

public class ErrorDialog extends AlertDialog implements View.OnClickListener {
	private TextView mTextView;

	public ErrorDialog(Context context) {
		super(context);
		
		getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

		setCanceledOnTouchOutside(true);

		setTitle(context.getString(R.string.dialog_error_title));
		setCancelable(true);
		setIcon(R.drawable.ic_dialog_alert);

		// Set up the TextView
		mTextView = new TextView(getContext());

		// Set some padding
		mTextView.setPadding(5, 5, 5, 5);
		mTextView.setOnClickListener(this);

		setView(mTextView);
	}
	
	public void setError(String error) {
		// Set up the final string
		mTextView.setText(error);

		// Now linkify the text
		Linkify.addLinks(mTextView, Linkify.WEB_URLS|Linkify.EMAIL_ADDRESSES);
	}

	public void onClick(View view) {
		cancel();
	}
}
