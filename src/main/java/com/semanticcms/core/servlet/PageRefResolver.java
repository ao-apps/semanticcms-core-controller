/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.ServletUtil;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

/**
 * Helper utilities for resolving PageRefs.
 */
public class PageRefResolver {

	private static final Logger logger = Logger.getLogger(PageRefResolver.class.getName());
	static {
		// TODO: Remove for production
		//logger.setLevel(Level.ALL);
	}

	/**
	 * Finds the path to the current page.
	 * The current page must be in a Book.
	 */
	public static PageRef getCurrentPageRef(ServletContext servletContext, HttpServletRequest request) throws ServletException {
		String pagePath = Dispatcher.getCurrentPagePath(request);
		Book book = SemanticCMS.getInstance(servletContext).getBook(pagePath);
		if(book == null) throw new ServletException("Book not found for pagePath: " + pagePath);
		String bookPrefix = book.getPathPrefix();
		if(!pagePath.startsWith(bookPrefix)) throw new AssertionError();
		return new PageRef(book, pagePath.substring(bookPrefix.length()));
	}

	/**
	 * Resolves a PageRef.  If book is provided, path may be book-relative path, which will be interpreted relative
	 * to the current page.
	 * <p>
	 * Resolves the bookName to use.  If the provided book is null, uses the book
	 * of the current page.  Otherwise, uses the provided book name.
	 * </p>
	 *
	 * @see  #getBookName
	 *
	 * @throws ServletException If no book provided and the current page is not within a book's content.
	 */
	public static PageRef getPageRef(ServletContext servletContext, HttpServletRequest request, String book, String path) throws ServletException, MalformedURLException {
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		if(book == null) {
			// When book not provided, path is relative to current page
			String currentPagePath = Dispatcher.getCurrentPagePath(request);
			Book currentBook = semanticCMS.getBook(currentPagePath);
			if(currentBook == null) throw new ServletException("book attribute required when not in a book's content");
			String currentBookPath = currentPagePath.substring(currentBook.getPathPrefix().length());
			String absolutePath = ServletUtil.getAbsolutePath(currentBookPath, path);
			if("index-tasklog-export-to-blackberry.xml".equals(path)) {
				if(logger.isLoggable(Level.FINE)) {
					logger.log(
						Level.FINE,
						"book={0}\n"
						+ "path={1}\n"
						+ "currentPagePath={2}\n"
						+ "currentBook={3}\n"
						+ "currentBookPath={4}\n"
						+ "absolutePath={5}",
						new Object[] {
							book,
							path,
							currentPagePath,
							currentBook,
							currentBookPath,
							absolutePath
						}
					);
				}
			}
			return new PageRef(currentBook, absolutePath);
		} else {
			if(!path.startsWith("/")) throw new ServletException("When book provided, path must begin with a slash (/): " + path);
			Book foundBook = semanticCMS.getBooks().get(book);
			if(foundBook != null) {
				return new PageRef(foundBook, path);
			} else {
				// Missing book
				if(!semanticCMS.getMissingBooks().contains(book)) {
					throw new ServletException("Reference to missing book not allowed: " + book);
				}
				return new PageRef(book, path);
			}
		}
	}

	/**
	 * Gets a PageRef in the current page context.
	 *
	 * @see  #getPageRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, java.lang.String, java.lang.String)
	 * @see  PageContext
	 */
	public static PageRef getPageRef(String book, String path) throws ServletException, MalformedURLException {
		return getPageRef(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			book,
			path
		);
	}

	/**
	 * Make no instances.
	 */
	private PageRefResolver() {
	}
}
