/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2015, 2016, 2017, 2018  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-controller.
 *
 * semanticcms-core-controller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-controller is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-controller.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.controller;

import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.net.DomainName;
import com.aoindustries.net.Path;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.validation.ValidationException;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.local.PageContext;
import java.net.MalformedURLException;
import java.util.NoSuchElementException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

/**
 * Helper utilities for resolving  {@link PageRef PageRefs}.
 *
 * TODO: Parts of this go to pages-local?
 */
public class PageRefResolver {

	/**
	 * Finds the path to the current page.
	 * The current page must be in a Book.
	 *
	 * @see  #getCurrentPageRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, boolean)
	 */
	public static PageRef getCurrentPageRef(ServletContext servletContext, HttpServletRequest request) throws ServletException {
		return getCurrentPageRef(servletContext, request, true);
	}

	/**
	 * Finds the path to the current page, optionally returning {@code null} when the
	 * current page is not in a book.  The page must be in a published book.
	 *
	 * @param requireBook affects the behavior when the current page is not in a book.
	 *                    When {@code true}, a {@link ServletException} is thrown.
	 *                    When {@code false}, {@code null} is returned.
	 *
	 * @see  #getCurrentPageRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest)
	 */
	public static PageRef getCurrentPageRef(ServletContext servletContext, HttpServletRequest request, boolean requireBook) throws ServletException {
		String pagePath = Dispatcher.getCurrentPagePath(request);
		// TODO: get local book distinct from get published book, for local content that is not published
		Book book = SemanticCMS.getInstance(servletContext).getPublishedBook(pagePath);
		if(book == null) {
			if(requireBook) {
				throw new ServletException("Book not found for pagePath: " + pagePath);
			} else {
				return null;
			}
		}
		BookRef bookRef = book.getBookRef();
		String bookPrefix = bookRef.getPrefix();
		assert pagePath.startsWith(bookPrefix);
		try {
			return new PageRef(
				bookRef,
				Path.valueOf(pagePath.substring(bookPrefix.length()))
			);
		} catch(ValidationException e) {
			throw new ServletException(e);
		}
	}

	/**
	 * Resolves a {@link PageRef}.
	 * <p>
	 * When domain is provided, book is required.  When domain is not provided,
	 * defaults to the domain of the current page.
	 * </p>
	 * <p>
	 * When book is not provided, defaults to the book of the current page.
	 * TODO: Is there ever a context where there is not a "current page" with remote repositories?
	 * </p>
	 * <p>
	 * When book is not provided, path may be book-relative path, which will be interpreted relative
	 * to the current page.
	 * </p>
	 *
	 * @param  path  required non-empty
	 *
	 * @throws ServletException If no book provided and the current page is not within a book's content.
	 *
	 * @see  #getPageRef(com.aoindustries.net.DomainName, com.aoindustries.net.Path, java.lang.String)
	 * @see  ResourceRefResolver#getResourceRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, com.aoindustries.net.DomainName, com.aoindustries.net.Path, java.lang.String)
	 */
	public static PageRef getPageRef(
		ServletContext servletContext,
		HttpServletRequest request,
		DomainName domain,
		Path book,
		String path
	) throws ServletException, MalformedURLException {
		try {
			NullArgumentException.checkNotNull(path, "path");
			if(path.isEmpty()) throw new IllegalArgumentException("path is empty");
			if(domain != null && book == null) {
				throw new IllegalArgumentException("book is required when domain is provided.");
			}
			SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			if(book == null) {
				assert domain == null;
				// When book not provided, path is relative to current page
				String currentPagePath = Dispatcher.getCurrentPagePath(request);
				// TODO: get local book distinct from get published book, for local content that is not published
				Book currentBook = semanticCMS.getPublishedBook(currentPagePath);
				if(currentBook == null) throw new ServletException("book attribute required when not in a book's content: " + currentPagePath);
				BookRef currentBookRef = currentBook.getBookRef();
				String bookPrefix = currentBookRef.getPrefix();
				assert currentPagePath.startsWith(bookPrefix);
				return new PageRef(
					currentBookRef,
					Path.valueOf(
						ServletUtil.getAbsolutePath(
							currentPagePath.substring(bookPrefix.length()),
							path
						)
					)
				);
			} else {
				if(!path.startsWith("/")) throw new ServletException("When book provided, path must begin with a slash (/): " + path);
				// domain of current page when domain not provided
				if(domain == null) {
					String currentPagePath = Dispatcher.getCurrentPagePath(request);
					// TODO: get local book distinct from get published book, for local content that is not published
					Book currentBook = semanticCMS.getPublishedBook(currentPagePath);
					if(currentBook == null) throw new ServletException("domain attribute required when not in a book's content: " + currentPagePath);
					domain = currentBook.getBookRef().getDomain();
				}
				BookRef bookRef = new BookRef(domain, book);
				// Make sure book exists
				try {
					return new PageRef(
						semanticCMS.getBook(bookRef).getBookRef(), // Use BookRef from Book, since it is a shared long-lived object
						Path.valueOf(path)
					);
				} catch(NoSuchElementException e) {
					throw new ServletException("Reference to missing book not allowed: " + bookRef, e);
				}
			}
		} catch(ValidationException e) {
			throw new ServletException(e);
		}
	}

	/**
	 * Gets a {@link PageRef} in the current {@link PageContext page context}.
	 *
	 * @see  #getPageRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, com.aoindustries.net.DomainName, com.aoindustries.net.Path, java.lang.String)
	 * @see  PageContext
	 * @see  ResourceRefResolver#getResourceRef(com.aoindustries.net.DomainName, com.aoindustries.net.Path, java.lang.String)
	 */
	public static PageRef getPageRef(DomainName domain, Path book, String path) throws ServletException, MalformedURLException {
		return getPageRef(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			domain,
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
