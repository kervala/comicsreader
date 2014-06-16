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

import android.app.Dialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.content.Context;
import android.widget.Button;
import android.widget.TextView;

public class TextDialog extends Dialog implements View.OnClickListener {
	private TextView mText;

	public TextDialog(Context context) {
		super(context);

		Window window = getWindow();

		// must be called before setContentView
		window.requestFeature(Window.FEATURE_LEFT_ICON);

		setCanceledOnTouchOutside(true);

		setContentView(R.layout.dialog_text);
		
		setFeatureDrawable(Window.FEATURE_LEFT_ICON, context.getResources().getDrawable(R.drawable.icon));

		// must be called after setContentView
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); 

		mText = (TextView)findViewById(R.id.dialog_text);
		mText.setMovementMethod(LinkMovementMethod.getInstance());

		mText.setTextColor(mText.getTextColors().getDefaultColor());

		final Button button = (Button)findViewById(R.id.button);
		button.setOnClickListener(this);
	}

	public void setText(String text) {
		mText.setText(text);
		Linkify.addLinks(mText, Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
	}

	public void setHtml(String html) {
		mText.setText(Html.fromHtml(html, null, null));
	}

	public void onClick(View v) {
		cancel();
	}
}
