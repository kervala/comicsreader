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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.DisplayMetrics;
import android.widget.TextView;

public class AboutActivity extends Activity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.about);

		final TextView versionView = (TextView)findViewById(R.id.dialog_about_version);
		versionView.setText(getString(R.string.about_version, ComicsParameters.sPackageVersion));

		final TextView unrarVersionView = (TextView)findViewById(R.id.dialog_about_unrar_version);
		unrarVersionView.setText(getString(R.string.about_unrar_version, RarFile.getVersion()));
		
		String revision = getString(R.string.revision);

		if ("".equals(revision)) {
			revision = "local";
		} else {
			revision = revision.substring(0, 16);
		}

		final TextView revisionView = (TextView)findViewById(R.id.dialog_about_revision);
		revisionView.setText(getString(R.string.about_revision, revision));

		String buildDate = getString(R.string.build_date);

		if ("".equals(buildDate)) {
			buildDate = "local";
		}

		final TextView buildDateView = (TextView)findViewById(R.id.dialog_about_build_date);
		buildDateView.setText(getString(R.string.about_build_date, buildDate));

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);

		final TextView view = (TextView)findViewById(R.id.licenses);
		view.setTextSize(dm.widthPixels * 0.75f * dm.density / 80.f);

		// licenses loading in a thread
		new LoadLicensesTask().execute("COMICSREADER", "UNRAR");
	}

	private class LoadLicensesTask extends AsyncTask<String, Integer, SpannableStringBuilder> {
		@Override
		protected SpannableStringBuilder doInBackground(String... licenses) {
			SpannableStringBuilder result = new SpannableStringBuilder();

			int count = licenses.length;

			for(int i = 0; i < count; ++i) {
				appendLicense(result, licenses[i]);
			}

			return result;
		}

		@Override
		protected void onPostExecute(SpannableStringBuilder result) {
			final TextView view = (TextView)findViewById(R.id.licenses);
			view.setText(result);
		}
	}

	private boolean appendLicense(SpannableStringBuilder str, String license) {
		String filename = "LICENSE_" + license;

		InputStream input = null;
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		try {
			// open stream
			input = getAssets().open(filename, AssetManager.ACCESS_BUFFER);

			int count = 0;
			byte data[] = new byte[2048];

			// copy file content
			while ((count = input.read(data)) != -1) {
				byteArrayOutputStream.write(data, 0, count);
			}

			str.append(Html.fromHtml("<br/><br/><h1>" + license + "</h1>"));
			str.append(byteArrayOutputStream.toString());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			closeStream(input);
		}

		return true;
	}

	void closeStream(Closeable stream) {
		if (stream == null) return;

		try {
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
