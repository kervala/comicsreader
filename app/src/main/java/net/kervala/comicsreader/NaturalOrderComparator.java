package net.kervala.comicsreader;

/*
 NaturalOrderComparator.java -- Perform 'natural order' comparisons of strings in Java.
 Copyright (C) 2003 by Pierre-Luc Paour <natorder@paour.com>

 Based on the C version by Martin Pool, of which this is more or less a straight conversion.
 Copyright (C) 2000 by Martin Pool <mbp@humbug.org.au>
 
 Fixed some bugs related to zeros and other stuff
 Copyright (C) 2014 by Cedric OCHS <kervala@gmail.com>

 This software is provided 'as-is', without any express or implied
 warranty.  In no event will the authors be held liable for any damages
 arising from the use of this software.

 Permission is granted to anyone to use this software for any purpose,
 including commercial applications, and to alter it and redistribute it
 freely, subject to the following restrictions:

 1. The origin of this software must not be misrepresented; you must not
 claim that you wrote the original software. If you use this software
 in a product, an acknowledgment in the product documentation would be
 appreciated but is not required.
 2. Altered source versions must be plainly marked as such, and must not be
 misrepresented as being the original software.
 3. This notice may not be removed or altered from any source distribution.
*/

// Altered source code, main method is removed and changed some methods to static

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.zip.CRC32;

import android.util.Log;

public class NaturalOrderComparator implements Comparator<String> {
	public int compare(String o1, String o2) {
		return compareStrings(o1, o2);
	}

	static int compareRight(String a, String b) {
		int bias = 0;
		int ia = 0;
		int ib = 0;

		// The longest run of digits wins. That aside, the greatest
		// value wins, but we can't know that it will until we've scanned
		// both numbers to know that they have the same magnitude, so we
		// remember it in BIAS.
		for (;; ia++, ib++) {
			char ca = charAt(a, ia);
			char cb = charAt(b, ib);

			if (!Character.isDigit(ca) && !Character.isDigit(cb)) {
				return bias;
			} else if (!Character.isDigit(ca)) {
				return -1;
			} else if (!Character.isDigit(cb)) {
				return +1;
			} else if (ca < cb) {
				if (bias == 0) {
					bias = -1;
				}
			} else if (ca > cb) {
				if (bias == 0)
					bias = +1;
			} else if (ca == 0 && cb == 0) {
				return bias;
			}
		}
	}

	public static int compareStrings(String o1, String o2) {
		String a = o1.toString();
		String b = o2.toString();

		int ia = 0, ib = 0;
		int nza = 0, nzb = 0;
		char ca, cb;
		int result;

		while (true) {
			// only count the number of zeroes leading the last number compared
			nza = nzb = 0;

			ca = charAt(a, ia);
			cb = charAt(b, ib);

			// skip over leading spaces
			while (Character.isSpaceChar(ca)) {
				ca = charAt(a, ++ia);
			}

			while (Character.isSpaceChar(cb)) {
				cb = charAt(b, ++ib);
			}
			
			// only skip zeros if other is also a digit
			if ((Character.isDigit(cb) && ca == '0') ||
				(Character.isDigit(ca) && cb == '0')) {

				// skip over leading spaces or zeros
				while (ca == '0') {
					++nza;

					ca = charAt(a, ++ia);
				}
				
				// if next character is not a digit, we got a zero
				if (!Character.isDigit(ca)) {
					--ia; --nza; ca = '0';
				}

				while (cb == '0') {
					++nzb;

					cb = charAt(b, ++ib);
				}

				// if next character is not a digit, we got a zero
				if (!Character.isDigit(cb)) {
					--ib; --nzb; cb = '0';
				}
			}

			// process run of digits
			if (Character.isDigit(ca) && Character.isDigit(cb)) {
				if ((result = compareRight(a.substring(ia), b.substring(ib))) != 0) {
					return result;
				}
			}

			if (ca == 0 && cb == 0) {
				// The strings compare the same. Perhaps the caller
				// will want to call strcmp to break the tie.
				return nza - nzb;
			}

			if (ca < cb) {
				return -1;
			} else if (ca > cb) {
				return +1;
			}

			++ia;
			++ib;
		}
	}

	static char charAt(String s, int i) {
		if (i >= s.length()) {
			return 0;
		} else {
			return s.charAt(i);
		}
	}

	static void testCases() {
		List<String> files = new ArrayList<String>();

		files.add("v01f.jpg");
		files.add("0.jpg");
		files.add("1.jpg");
		files.add("001a.jpg");
		files.add("v00f.jpg");
		files.add(" 001b.jpg");
		files.add("02_bonjour.jpg");
		files.add("00a_bonjour.jpg");
		files.add("00b_bonjour.jpg");
		files.add("000000d.jpg");
		files.add("c.jpg");
		files.add("20.jpg");
		files.add("3grgre20.jpg");
		files.add("00 c00.jpg");

		Collections.sort(files, new NaturalOrderComparator());

		// now check if CRC32 of the result is right
		CRC32 crc = new CRC32();

		try {
			crc.update(files.toString().getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
		}

		// compare to CRC32 of the right result
		long value = crc.getValue();

		if (value == 0x1305F4AEl) {
			Log.d("ComicsReader", "Result is right for " + files.toString());
		} else {
			Log.d("ComicsReader", "Result is wrong (" + String.valueOf(value) + ") for " + files.toString());
		}
	}
}
