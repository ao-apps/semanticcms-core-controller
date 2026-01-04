/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2019, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.semanticcms.core.model.Author;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Author processing utilities.
 */
public final class AuthorUtils {

  /** Make no instances. */
  private AuthorUtils() {
    throw new AssertionError();
  }

  /**
   * Finds all the authors for the given page.
   *
   * <p>When no authors provided, will use the author(s) of any parent
   * page that is within the same book.  If there are no parent pages
   * in this same book, uses the book's authors.</p>
   *
   * <p>When inheriting authorship from multiple parent pages, the authors must
   * be in exact agreement.  This means exactly the same order and all
   * values matching precisely.  Any mismatch in authors will result in
   * an exception.</p>
   */
  public static Set<Author> findAuthors(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      com.semanticcms.core.model.Page page
  ) throws ServletException, IOException {
    // TODO: traversal
    return findAuthorsRecursive(
        servletContext,
        request,
        response,
        page,
        new HashMap<>()
    );
  }

  /**
   * Checks that both the iterables have equal objects in iteration order.
   */
  private static boolean exactMatch(Iterable<?> iterable1, Iterable<?> iterable2) {
    if (iterable1 == iterable2) {
      return true;
    }
    Iterator<?> iter1 = iterable1.iterator();
    Iterator<?> iter2 = iterable2.iterator();
    while (iter1.hasNext() && iter2.hasNext()) {
      Object o1 = iter1.next();
      Object o2 = iter2.next();
      if (!o1.equals(o2)) {
        return false;
      }
    }
    return !iter1.hasNext() && !iter2.hasNext();
  }

  private static Set<Author> findAuthorsRecursive(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      com.semanticcms.core.model.Page page,
      Map<PageRef, Set<Author>> finished
  ) throws ServletException, IOException {
    PageRef pageRef = page.getPageRef();
    assert !finished.containsKey(pageRef);
    // Use directly set authors first
    Set<Author> pageAuthors = page.getAuthors();
    if (pageAuthors.isEmpty()) {
      // Use the authors of all parents in the same book
      pageAuthors = null;
      Book book = pageRef.getBook();
      for (ParentRef parentRef : page.getParentRefs()) {
        PageRef parentPageRef = parentRef.getPageRef();
        if (book.equals(parentPageRef.getBook())) {
          // Check finished already
          Set<Author> parentAuthors = finished.get(parentPageRef);
          if (parentAuthors == null) {
            // Capture parent and find its authors
            parentAuthors = findAuthorsRecursive(
                servletContext,
                request,
                response,
                CapturePage.capturePage(servletContext, request, response, parentPageRef, CaptureLevel.PAGE),
                finished
            );
          }
          if (pageAuthors == null) {
            pageAuthors = parentAuthors;
          } else {
            // Must precisely match when have multiple parents
            if (!exactMatch(pageAuthors, parentAuthors)) {
              throw new ServletException("Mismatched authors inherited from different parents: " + pageAuthors + " does not match " + parentAuthors);
            }
          }
        }
      }
      // No parents in the same book, use book authors
      if (pageAuthors == null) {
        pageAuthors = book.getAuthors();
      }
    }
    // Store in finished
    finished.put(pageRef, pageAuthors);
    return pageAuthors;
  }
}
