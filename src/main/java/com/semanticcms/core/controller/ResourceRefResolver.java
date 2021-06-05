/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2017, 2019, 2021  AO Industries, Inc.
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

import com.aoapps.lang.NullArgumentException;
import com.aoapps.lang.validation.ValidationException;
import com.aoapps.net.DomainName;
import com.aoapps.net.Path;
import com.aoapps.net.URIResolver;
import com.aoapps.servlet.http.Dispatcher;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.ResourceRef;
import com.semanticcms.core.pages.local.PageContext;
import java.net.MalformedURLException;
import java.util.NoSuchElementException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

/**
 * Helper utilities for resolving {@link ResourceRef ResourceRefs}.
 *
 * TODO: Parts of this go to pages-local?
 */
public class ResourceRefResolver {

	/**
	 * Resolves a {@link ResourceRef}.
	 * <p>
	 * When domain is provided, book is required.  When domain is not provided,
	 * defaults to the domain of the current page.
	 * </p>
	 * <p>
	 * When book is not provided, defaults to the book of the current page.
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
	 * @see  #getResourceRef(com.aoapps.net.DomainName, com.aoapps.net.Path, java.lang.String)
	 * @see  PageRefResolver#getPageRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, com.aoapps.net.DomainName, com.aoapps.net.Path, java.lang.String)
	 */
	public static ResourceRef getResourceRef(
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
				try {
					return new ResourceRef(
						currentBookRef,
						Path.valueOf(
							URIResolver.getAbsolutePath(
								currentPagePath.substring(bookPrefix.length()),
								path
							)
						)
					);
				} catch(ValidationException e) {
					throw new ServletException(e);
				}
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
					return new ResourceRef(
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
	 * Gets a {@link ResourceRef} in the current {@link PageContext page context}.
	 *
	 * @see  #getResourceRef(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, com.aoapps.net.DomainName, com.aoapps.net.Path, java.lang.String)
	 * @see  PageContext
	 * @see  PageRefResolver#getPageRef(com.aoapps.net.DomainName, com.aoapps.net.Path, java.lang.String)
	 */
	public static ResourceRef getResourceRef(DomainName domain, Path book, String path) throws ServletException, MalformedURLException {
		return getResourceRef(
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
	private ResourceRefResolver() {
	}
}
