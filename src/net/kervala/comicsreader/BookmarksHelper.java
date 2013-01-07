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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BookmarksHelper extends SQLiteOpenHelper {
	private static final String sTable = "bookmark";
	private static final String sAlterDb = "ALTER TABLE %s RENAME TO %s_old;";
	private static final String sCreateDb = "CREATE TABLE %s (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, url TEXT, category INTEGER);";
	private static final String sInsertExternal = "INSERT INTO %s (name, url, category) VALUES ('%s', '%s', 0);";
	private static final String sUpgradeFrom1ToLast = "INSERT INTO %s (_id, name, url, category) SELECT _id, name, url, 1 FROM %s_old WHERE _id > 1 ORDER BY _id;";
	private static final String sUpgradeFrom2ToLast = "INSERT INTO %s (_id, name, url, category) SELECT _id, name, url, 'order' FROM %s_old WHERE _id > 1 ORDER BY _id;";
	private static final String sUpgradeFrom3ToLast = "INSERT INTO %s (_id, name, url, category) SELECT _id, name, url, category FROM %s_old WHERE _id > 1 ORDER BY _id;";
	private static final String sDropOldDb = "DROP TABLE %s_old;";
	private String mExternalStorage;
	private SQLiteDatabase mDb;

	public BookmarksHelper(Context context) {
		super(context, "bookmarks.db", null, 4);

		mExternalStorage = context.getString(R.string.external_storage);
	}
	
	public void openDatabase(boolean readOnly) {
		if (mDb != null) {
			if (mDb.isReadOnly()) {
				if (!readOnly) {
					closeDatabase();

					// open it in writable
					mDb = getWritableDatabase();
				}
			} else {
				if (readOnly) {
					closeDatabase();

					// open it in readable
					mDb = getReadableDatabase();
				}
			}
		} else {
			if (readOnly) {
				mDb = getReadableDatabase();
			} else {
				mDb = getWritableDatabase();
			}
		}
	}
	
	public void closeDatabase() {
		if (mDb != null) {
			mDb.close();
		}
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(String.format(sCreateDb, sTable));
		db.execSQL(String.format(sInsertExternal, sTable, mExternalStorage, ComicsParameters.sExternalDirectory.getAbsolutePath()));
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// only upgrade database if new version is greater than old version
		if (oldVersion < newVersion) {
			db.execSQL(String.format(sAlterDb, sTable, sTable));
			db.execSQL(String.format(sCreateDb, sTable));
			db.execSQL(String.format(sInsertExternal, sTable, mExternalStorage, ComicsParameters.sExternalDirectory.getAbsolutePath()));
			if (oldVersion == 3) {
				// don't do anything, only change default entry
				db.execSQL(String.format(sUpgradeFrom3ToLast, sTable, sTable));
			} else if (oldVersion == 2) {
				// rename order field name to category
				db.execSQL(String.format(sUpgradeFrom2ToLast, sTable, sTable));
			} else 
				if (oldVersion == 1) {
				// add order field
				db.execSQL(String.format(sUpgradeFrom1ToLast, sTable, sTable));
			}
			db.execSQL(String.format(sDropOldDb, sTable));
		}
	}

	public boolean setBookmark(int id, String name, String url) {
		// fix remote path
		if (!url.startsWith("/") && !url.startsWith("http://")) {
			url = "http://" + url;
		}

		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("url", url);
		values.put("category", 1);

		openDatabase(false);
		
		if (id == -1) {
			return mDb.insert(sTable, null, values) > 0;
		}
		
		return mDb.update(sTable, values, "_id = ?", new String[] {Integer.toString(id)}) > 0;
	}

	public boolean deleteBookmark(int id) {
		openDatabase(false);

		return mDb.delete(sTable, "_id=?", new String[] {Integer.toString(id)}) == 1;
	}

	public Cursor getBookmarkCursor() {
		openDatabase(true);

		return mDb.query(sTable, null, null, null, null, null, "category, name");
	}
}
