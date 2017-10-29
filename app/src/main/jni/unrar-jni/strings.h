/*
 * ComicsReader is an Android application to read comics
 * Copyright (C) 2011-2018 Cedric OCHS
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

#ifndef STRINGS_H
#define STRINGS_H
 
#include <string.h>
 
class Strings
{
	public:

	Strings();
	Strings(const char *str, Strings *parent = NULL);
	~Strings();

	void allocateString(const char *str);
	void setString(const char *str) { m_str = (char*)str; }
	void setNext(Strings *next) { m_next = next; }

	const char* getString() const { return m_str; }
	Strings* getNext() const { return m_next; }

	bool isLast() const { return m_next == NULL; }
	size_t size();

	void addString(const char *str);

	private:

	char *m_str;
	Strings *m_next;
};

#endif
