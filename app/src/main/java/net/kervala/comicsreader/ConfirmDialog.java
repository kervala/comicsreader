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
import android.os.Handler;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

public class ConfirmDialog extends AlertDialog {
	public ConfirmDialog(Context context, Handler handler, String str) {
		super(context);

		getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

		setTitle(context.getString(R.string.dialog_confirm_title));
		setCancelable(true);
		setIcon(R.drawable.ic_dialog_info);
		
		// Set up the TextView
		final TextView message = new TextView(context);

		// Set some padding
		message.setPadding(5, 5, 5, 5);

		// Set up the final string
		message.setText(str);

		setView(message);

		setButton(BUTTON_POSITIVE, context.getString(R.string.yes), handler.obtainMessage(BrowserActivity.ACTION_CONFIRM_YES));
		setButton(BUTTON_NEGATIVE, context.getString(R.string.no), handler.obtainMessage(BrowserActivity.ACTION_CONFIRM_NO));
	}
}
