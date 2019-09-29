/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-servlet.
 *
 * semanticcms-core-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.servlet;

import com.aoindustries.servlet.http.HttpServletUtil;
import com.semanticcms.core.model.Book;
import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

/**
 * Utilities for working with books.
 */
final public class BookUtils {

	private static final Logger logger = Logger.getLogger(BookUtils.class.getName());

	/**
	 * Optional initialization parameter providing the canonical base URL.
	 */
	private static final String CANONICAL_BASE_WARNED_ATTRIBUTE = BookUtils.class.getName() + ".getCanonicalBase.autoWarned.";

	/**
	 * Gets the canonical base URL, not including any trailing slash, such as
	 * <code>https://example.com</code>
	 * This is configured in the book via the "canonicalBase" setting.
	 * <p>
	 * TODO: Create central per-request warnings list that could be reported during development mode, include this warning on requests.
	 * TODO: Also could use that for broken link detection instead of throwing exceptions.
	 * </p>
	 */
	public static String getCanonicalBase(ServletContext servletContext, HttpServletRequest request, Book book) throws MalformedURLException {
		String canonicalBase = book.getCanonicalBase();
		if(canonicalBase == null) {
			String autoCanonical = HttpServletUtil.getAbsoluteURL(request, book.getPathPrefix());
			if(
				// Logger checked first, so if warnings enabled mid-run, will get first warning still
				logger.isLoggable(Level.WARNING)
			) {
				String bookName = book.getName();
				String warningAttribute = CANONICAL_BASE_WARNED_ATTRIBUTE + bookName;
				if(servletContext.getAttribute(warningAttribute) == null) {
					servletContext.setAttribute(warningAttribute, true);
					logger.warning("Using generated canonical base URL, please configure the \"canonicalBase\" setting in the \"" + bookName + "\" book: " + autoCanonical);
				}
			}
			return autoCanonical;
		} else {
			return canonicalBase;
		}
	}

	/**
	 * Make no instances.
	 */
	private BookUtils() {
	}
}
