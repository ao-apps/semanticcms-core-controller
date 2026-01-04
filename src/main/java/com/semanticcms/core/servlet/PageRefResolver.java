/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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
 * along with semanticcms-core-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.servlet;

import static com.aoapps.lang.Strings.nullIfEmpty;

import com.aoapps.lang.NullArgumentException;
import com.aoapps.net.URIResolver;
import com.aoapps.servlet.http.Dispatcher;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;

/**
 * Helper utilities for resolving PageRefs.
 */
public final class PageRefResolver {

  /** Make no instances. */
  private PageRefResolver() {
    throw new AssertionError();
  }

  /**
   * Finds the path to the current page.
   * The current page must be in a Book.
   *
   * @see  #getCurrentPageRef(jakarta.servlet.ServletContext, jakarta.servlet.http.HttpServletRequest, boolean)
   */
  public static PageRef getCurrentPageRef(ServletContext servletContext, HttpServletRequest request) throws ServletException {
    return getCurrentPageRef(servletContext, request, true);
  }

  /**
   * Finds the path to the current page, optionally returning {@code null} when the
   * current page is not in a book.
   *
   * @param requireBook affects the behavior when the current page is not in a book.
   *                    When {@code true}, a {@link ServletException} is thrown.
   *                    When {@code false}, {@code null} is returned.
   *
   * @see  #getCurrentPageRef(jakarta.servlet.ServletContext, jakarta.servlet.http.HttpServletRequest)
   */
  public static PageRef getCurrentPageRef(ServletContext servletContext, HttpServletRequest request, boolean requireBook) throws ServletException {
    String pagePath = Dispatcher.getCurrentPagePath(request);
    Book book = SemanticCMS.getInstance(servletContext).getBook(pagePath);
    if (book == null) {
      if (requireBook) {
        throw new ServletException("Book not found for pagePath: " + pagePath);
      } else {
        return null;
      }
    }
    String bookPrefix = book.getPathPrefix();
    if (!pagePath.startsWith(bookPrefix)) {
      throw new AssertionError();
    }
    return new PageRef(book, pagePath.substring(bookPrefix.length()));
  }

  /**
   * Resolves a PageRef.  When book is not provided, path may be book-relative path, which will be interpreted relative
   * to the current page.
   *
   * <p>Resolves the bookName to use.  If the provided book is null, uses the book
   * of the current page.  Otherwise, uses the provided book name.</p>
   *
   * @param  book  empty string is treated same as null
   * @param  path  required non-empty
   *
   * @see  PageRef#getBookName()
   *
   * @throws ServletException If no book provided and the current page is not within a book's content.
   */
  public static PageRef getPageRef(ServletContext servletContext, HttpServletRequest request, String book, String path) throws ServletException, MalformedURLException {
    book = nullIfEmpty(book);
    NullArgumentException.checkNotNull(path, "path");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("path is empty");
    }
    SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
    if (book == null) {
      // When book not provided, path is relative to current page
      String currentPagePath = Dispatcher.getCurrentPagePath(request);
      Book currentBook = semanticCms.getBook(currentPagePath);
      if (currentBook == null) {
        throw new ServletException("book attribute required when not in a book's content");
      }
      return new PageRef(
          currentBook,
          URIResolver.getAbsolutePath(
              currentPagePath.substring(currentBook.getPathPrefix().length()),
              path
          )
      );
    } else {
      if (!path.startsWith("/")) {
        throw new ServletException("When book provided, path must begin with a slash (/): " + path);
      }
      Book foundBook = semanticCms.getBooks().get(book);
      if (foundBook != null) {
        return new PageRef(foundBook, path);
      } else {
        // Missing book
        // TODO: missingBooks intered, too?  Then can take it's interned copy for "book"
        if (!semanticCms.getMissingBooks().contains(book)) {
          throw new ServletException("Reference to missing book not allowed: " + book);
        }
        return new PageRef(book, path);
      }
    }
  }

  /**
   * Gets a PageRef in the current page context.
   *
   * @see  #getPageRef(jakarta.servlet.ServletContext, jakarta.servlet.http.HttpServletRequest, java.lang.String, java.lang.String)
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
}
