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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class AlbumParameters {
	static public boolean highQuality = false;
	static public boolean fitToScreen = true;
	static public int scale = 1;
	static public boolean rightToLeft = false;
	static public boolean doublePage = false;
	static public boolean fullScreen = false;
	static public int zoom = 0;
	static public int overlayDuration = 5000;
	static public int edgesResistance = 1;
	static public int pageTransitionSpeed = 2;
	static public boolean autoRotate = false;
	static public boolean useMinimumSize = false;
	
	static public boolean getAlbumPreferences(Context context) {
		boolean oldHighQuality = highQuality;
		boolean oldfitToScreen = fitToScreen;
		int oldScale = scale;
		boolean oldRightToLeft = rightToLeft;
		boolean oldDoublePage = doublePage;
		boolean oldFullScreen = fullScreen;
		int oldZoom = zoom;
		boolean oldAutoRotate = autoRotate;
		boolean oldUseMinimumSize = useMinimumSize;

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		highQuality = prefs.getBoolean("preference_high_quality", false);
		fullScreen = prefs.getBoolean("preference_full_screen", false);
		zoom = Integer.parseInt(prefs.getString("preference_zoom", "1"));
		doublePage = prefs.getBoolean("preference_double_page", false);
		scale = Integer.parseInt(prefs.getString("preference_sample", "0"));
		fitToScreen = prefs.getBoolean("preference_fit_to_screen", true);
		overlayDuration = Integer.parseInt(prefs.getString("preference_overlay_duration", "5000"));
		edgesResistance = Integer.parseInt(prefs.getString("preference_edges_resistance", "1"));
		edgesWidth = Integer.parseInt(prefs.getString("preference_edges_width", "1"));
		pageTransitionSpeed = Integer.parseInt(prefs.getString("preference_page_transition_speed", "2"));
		rightToLeft = prefs.getBoolean("preference_reading_direction", false);
		autoRotate = prefs.getBoolean("preference_auto_rotate", false);
		useMinimumSize = prefs.getBoolean("preference_use_minimum_size", false);

		switch(pageTransitionSpeed) {
			case 1:
			pageTransitionSpeed = 8;
			break;

			case 3:
			pageTransitionSpeed = 10;
			break;

			default:
			pageTransitionSpeed = 9;
			break;
		}

		// check if settings changed
		return oldHighQuality != highQuality ||	oldfitToScreen != fitToScreen ||
			oldScale != scale || oldRightToLeft != rightToLeft ||
			oldDoublePage != doublePage || oldFullScreen != fullScreen ||
			oldZoom != zoom || oldAutoRotate != autoRotate ||
			oldUseMinimumSize != useMinimumSize;
	}
}
