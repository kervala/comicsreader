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

#include "strings.h"

Strings::Strings()
{
	setString(NULL);
	setNext(NULL);
}

Strings::~Strings()
{
	if (m_next) delete m_next;
	if (m_str) delete [] m_str;
}

void Strings::allocateString(const char *str)
{
	if (str)
	{
		m_str = new char[strlen(str)+1];
		strcpy(m_str, str);
	}
	else
	{
		m_str = NULL;
	}
}

size_t Strings::size()
{
	size_t count = 0;
	Strings *tmp = this;

	while(tmp)
	{
		if (tmp->getString()) ++count;

		tmp = tmp->getNext();
	}

	return count;
}

void Strings::addString(const char *str)
{
	// don't do anything if str is NULL
	if (str == NULL) return;

	if (m_str == NULL)
	{
		allocateString(str);
	}
	else
	{
		Strings *tmp = this;

		while(!tmp->isLast()) tmp = tmp->getNext();

		Strings *next = new Strings();
		next->allocateString(str);

		if (tmp) tmp->setNext(next);
	}
}
