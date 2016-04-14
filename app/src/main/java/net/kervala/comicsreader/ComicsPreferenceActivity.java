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

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class ComicsPreferenceActivity extends PreferenceActivity implements OnPreferenceClickListener {
	private Preference mClearThumbnails;
	private Preference mClearAlbums;
	private Preference mAbout;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);

		mClearThumbnails = findPreference("preference_clear_cache_thumbnails");
		mClearAlbums = findPreference("preference_clear_cache_albums");
		mAbout = findPreference("preference_about");

		if (mClearThumbnails != null) {
			mClearThumbnails.setOnPreferenceClickListener(this);
		}

		if (mClearAlbums != null) {
			mClearAlbums.setOnPreferenceClickListener(this);
		}

		if (mAbout != null) {
			mAbout.setOnPreferenceClickListener(this);
		}
	}

	public boolean onPreferenceClick(Preference preference) {
		if (preference == mClearThumbnails) {
			ComicsParameters.clearThumbnailsCache();
			Toast.makeText(this, R.string.preference_clear_cache_thumbnails_toast, Toast.LENGTH_SHORT).show();
		} else if (preference == mClearAlbums) {
			ComicsParameters.clearDownloadedAlbumsCache();
			Toast.makeText(this, R.string.preference_clear_cache_albums_toast, Toast.LENGTH_SHORT).show();
		} else if (preference == mAbout) {
			startActivity(new Intent(this, AboutActivity.class));
		}
		return false;
	}
}
