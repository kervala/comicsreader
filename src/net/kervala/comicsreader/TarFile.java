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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class TarFile {
	private class TarEntry {
		String filename;
		int offset = 0;
		int filesize = 0;
	}
	
	protected String mName;
	protected List<TarEntry> mEntries = new ArrayList<TarEntry>();

	public TarFile(File file) throws IOException {
		if (file == null || !open(file.getAbsolutePath())) {
			throw new IOException();
		}
	}

	public TarFile(String filename) throws IOException {
		open(filename);
	}

	private boolean open(String filename) throws IOException {
		if (filename == null) {
			throw new IOException();
		}
		
		mName = filename;

		return readEntries();
	}

	public void close() {
		mEntries = null;
		mName = null;
	}

	public List<String> entries() {
		List<String> entries = new ArrayList<String>();

		for(TarEntry entry: mEntries) {
			entries.add(entry.filename);
		}

		return entries;
	}

	public byte [] getBytes(String entry) {
		byte [] buffer = null;
		
		for(TarEntry ent: mEntries) {
			if (ent.filename != null && ent.filename.equals(entry)) {
				File file = new File(mName);

				try {
					InputStream is = new FileInputStream(file);
					BufferedInputStream in = new BufferedInputStream(is);

					if (in.skip(ent.offset) == ent.offset) {
						buffer = ComicsHelpers.inputStreamToBytes(in, ent.filesize);
					}
					
					is.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			
				break;
			}
		}

		return buffer;
	}

	public String getName() {
		return mName;
	}

	public int size() {
		return mEntries.size();
	}
	
	private boolean readEntries() {
		byte [] bufferFilename = new byte[100];
		byte [] bufferFilesize = new byte[12];
		
		BufferedInputStream in = null;
		
		File file = new File(mName);
		
		boolean res = false;
		int offset = 0;
		
		try {
			in = new BufferedInputStream(new FileInputStream(file));

			while(in.read(bufferFilename, 0, bufferFilename.length) == bufferFilename.length) {
				String filename = new String(bufferFilename, "UTF-8");

				// we are in padding, this is the end
				if (bufferFilename[0] == 0) {
					res = true;
					break;
				}

				int filesize = 0;
				
				// file mode
				in.skip(8);

				// owner
				in.skip(8);

				// group
				in.skip(8);

				if (in.read(bufferFilesize, 0, bufferFilesize.length) == bufferFilesize.length) {
					String s = new String(bufferFilesize, "UTF-8");
					
					try {
						filesize = Integer.parseInt(s.trim(), 8);
					} catch(NumberFormatException e) {
						break;
					}
				}

				// date
				in.skip(12);

				// checksum
				in.skip(8);

				// file type
				in.skip(1);

				// linked filename
				in.skip(100);
				
				// misc
				in.skip(255);
				
				// skip header
				offset += 512;
				
				if (filesize > 0) {
					// skip file content
					in.skip(filesize);
					
					int len = 512 - (filesize % 512);
					
					if (len > 0) {
						in.skip(len);
					}

					if (filename != null) {
						TarEntry entry = new TarEntry();
						entry.filename = filename.trim();
						entry.filesize = filesize;
						entry.offset = offset;
						mEntries.add(entry);
					}
					
					offset += filesize + len;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return res;
	}
}
