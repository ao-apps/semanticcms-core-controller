/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026  AO Industries, Inc.
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
 * along with semanticcms-core-controller.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.controller;

import com.aoapps.collections.AoCollections;
import com.aoapps.lang.Coercion;
import com.aoapps.lang.io.function.IOPredicateE;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.PageReferrer;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.pages.CaptureLevel;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for working with pages.
 */
public final class PageUtils {

  /** Make no instances. */
  private PageUtils() {
    throw new AssertionError();
  }

  public static boolean hasChild(ServletContext servletContext, Page page) {
    Set<ChildRef> childRefs = page.getChildRefs();
    if (!childRefs.isEmpty()) {
      SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
      for (ChildRef childRef : childRefs) {
        if (semanticCms.getBook(childRef.getPageRef().getBookRef()).isAccessible()) {
          return true;
        }
      }
    }
    return false;
  }

  public static <E extends Element> boolean hasElement(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page page,
      Class<E> elementType,
      boolean recursive,
      IOPredicateE<? super E, ? extends ServletException> filter
  ) throws ServletException, IOException {
    if (recursive) {
      SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
      return CapturePage.traversePagesAnyOrder(
          servletContext,
          request,
          response,
          page,
          CaptureLevel.META,
          page1 -> {
            for (Element element : page1.getElements()) {
              if (elementType.isAssignableFrom(element.getClass()) && filter.test(elementType.cast(element))) {
                return true;
              }
            }
            return null;
          },
          Page::getChildRefs,
          // Child is in an accessible book
          childPage -> semanticCms.getBook(childPage.getBookRef()).isAccessible()
      ) != null;
    } else {
      for (Element element : page.getElements()) {
        if (elementType.isAssignableFrom(element.getClass()) && filter.test(elementType.cast(element))) {
          return true;
        }
      }
      return false;
    }
  }

  // TODO: Cache result per class per page?
  public static boolean hasElement(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page page,
      Class<? extends Element> elementType,
      boolean recursive
  ) throws ServletException, IOException {
    return hasElement(servletContext, request, response, page, elementType, recursive, element -> true);
  }

  /**
   * Finds the allowRobots setting for the given page.
   *
   * <p>When no allowRobots provided, will use the allowRobots(s) of any parent
   * page that is within the same book.  If there are no parent pages
   * in this same book, uses the book's allowRobots.</p>
   *
   * <p>When inheriting allowRobots from multiple parent pages, the allowRobots must
   * be in exact agreement.  This means exactly the same order and all
   * values matching precisely.  Any mismatch in allowRobots will result in
   * an exception.</p>
   */
  public static boolean findAllowRobots(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      com.semanticcms.core.model.Page page
  ) throws ServletException, IOException {
    // TODO: Traversal
    return findAllowRobotsRecursive(
        servletContext,
        request,
        response,
        SemanticCMS.getInstance(servletContext),
        page,
        new HashMap<>()
    );
  }

  private static boolean findAllowRobotsRecursive(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      SemanticCMS semanticCms,
      com.semanticcms.core.model.Page page,
      Map<PageRef, Boolean> finished
  ) throws ServletException, IOException {
    PageRef pageRef = page.getPageRef();
    assert !finished.containsKey(pageRef);
    // Use directly set allowRobots first
    Boolean pageAllowRobots = page.getAllowRobots();
    if (pageAllowRobots == null) {
      // Use the allowRobots of all parents in the same book
      BookRef bookRef = pageRef.getBookRef();
      for (ParentRef parentRef : page.getParentRefs()) {
        PageRef parentPageRef = parentRef.getPageRef();
        if (bookRef.equals(parentPageRef.getBookRef())) {
          // Check finished already
          Boolean parentAllowRobots = finished.get(parentPageRef);
          if (parentAllowRobots == null) {
            // Capture parent and find its allowRobots
            parentAllowRobots = findAllowRobotsRecursive(
                servletContext,
                request,
                response,
                semanticCms,
                CapturePage.capturePage(servletContext, request, response, parentPageRef, CaptureLevel.PAGE),
                finished
            );
          }
          if (pageAllowRobots == null) {
            pageAllowRobots = parentAllowRobots;
          } else {
            // Must precisely match when have multiple parents
            if (!pageAllowRobots.equals(parentAllowRobots)) {
              throw new ServletException("Mismatched allowRobots inherited from different parents: " + pageAllowRobots + " does not match " + parentAllowRobots);
            }
          }
        }
      }
      // No parents in the same book, use book allowRobots
      if (pageAllowRobots == null) {
        pageAllowRobots = semanticCms.getBook(bookRef).getAllowRobots();
      }
    }
    // Store in finished
    finished.put(pageRef, pageAllowRobots);
    return pageAllowRobots;
  }

  /**
   * Filters for all pageRefs that are present (not missing books).
   */
  public static <R extends PageReferrer> Set<R> filterNotMissingBook(ServletContext servletContext, Set<R> pageReferrers) {
    int size = pageReferrers.size();
    if (size == 0) {
      return Collections.emptySet();
    } else {
      SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
      if (size == 1) {
        R pageReferrer = pageReferrers.iterator().next();
        if (semanticCms.getBook(pageReferrer.getPageRef().getBookRef()).isAccessible()) {
          return Collections.singleton(pageReferrer);
        } else {
          return Collections.emptySet();
        }
      } else {
        Set<R> notMissingBooks = AoCollections.newLinkedHashSet(size);
        for (R pageReferrer : pageReferrers) {
          if (semanticCms.getBook(pageReferrer.getPageRef().getBookRef()).isAccessible()) {
            if (!notMissingBooks.add(pageReferrer)) {
              throw new AssertionError();
            }
          }
        }
        return Collections.unmodifiableSet(notMissingBooks);
      }
    }
  }

  /**
   * Determines the short title for a page and one of its parents.
   */
  public static String getShortTitle(PageRef parentPageRef, Page page) {
    // Check for per-parent shortTitle first for the given parent
    if (parentPageRef != null) {
      for (ParentRef parentRef : page.getParentRefs()) {
        if (parentRef.getPageRef().equals(parentPageRef)) {
          String shortTitle = parentRef.getShortTitle();
          if (shortTitle != null) {
            return shortTitle;
          }
          break;
        }
      }
    }
    // Use the overall page shortTitle
    return page.getShortTitle();
  }

  // TODO: This should go where?  Picking up dependency on controller for this alone is too much
  public static ZonedDateTime toDateTime(Object o) {
    if (o instanceof ZonedDateTime) {
      return (ZonedDateTime) o;
    }
    if (o instanceof Instant) {
      return ZonedDateTime.ofInstant((Instant) o, ZoneId.systemDefault());
    }
    if (o instanceof Long) {
      long l = (Long) o;
      return l == -1 || l == 0 ? null : ZonedDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault());
    }
    if (Coercion.isEmpty(o)) {
      return null;
    }
    return ZonedDateTime.parse(Coercion.toString(o));
  }

  /**
   * Verified one child-parent relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyChildToParent(ChildRef childRef, PageRef parentPageRef, Set<ChildRef> childRefs) throws ServletException {
    if (!childRefs.contains(childRef)) {
      throw new ServletException(
          "The parent page does not have this as a child.  this="
              + childRef
              + ", parent="
              + parentPageRef
              + ", parent.children="
              + childRefs
      );
    }
  }

  /**
   * Verified one child-parent relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyChildToParent(PageRef childPageRef, PageRef parentPageRef, Set<ChildRef> childRefs) throws ServletException {
    verifyChildToParent(new ChildRef(childPageRef), parentPageRef, childRefs);
  }

  /**
   * Verified one parent-child relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyParentToChild(ParentRef parentRef, PageRef childPageRef, Set<ParentRef> parentRefs) throws ServletException {
    if (!parentRefs.contains(parentRef)) {
      throw new ServletException(
          "The child page does not have this as a parent.  this="
              + parentRef
              + ", child="
              + childPageRef
              + ", child.parents="
              + parentRefs
      );
    }
  }

  /**
   * Verified one parent-child relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyParentToChild(PageRef parentPageRef, PageRef childPageRef, Set<ParentRef> parentRefs) throws ServletException {
    verifyParentToChild(new ParentRef(parentPageRef, null), childPageRef, parentRefs);
  }

  /**
   * Performs full parent/child verifications of the provided page.  This is normally
   * not needed for pages that have been added to the cache (PAGE/META level), as verification
   * is done within the cache.  This is used for BODY level captures which are not put in the
   * cache and desire full verification.
   *
   * @throws  ServletException  if verification failed
   */
  public static void fullVerifyParentChild(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page page
  ) throws ServletException, IOException {
    // Verify parents
    if (!page.getAllowParentMismatch()) {
      Map<PageRef, Page> notMissingParents = CapturePage.capturePages(
          servletContext,
          request,
          response,
          PageUtils.filterNotMissingBook(servletContext, page.getParentRefs()),
          CaptureLevel.PAGE
      );
      PageRef pageRef = page.getPageRef();
      for (Map.Entry<PageRef, Page> entry : notMissingParents.entrySet()) {
        verifyChildToParent(pageRef, entry.getKey(), entry.getValue().getChildRefs());
      }
    }
    // Verify children
    if (!page.getAllowChildMismatch()) {
      Map<PageRef, Page> notMissingChildren = CapturePage.capturePages(
          servletContext,
          request,
          response,
          PageUtils.filterNotMissingBook(servletContext, page.getChildRefs()),
          CaptureLevel.PAGE
      );
      PageRef pageRef = page.getPageRef();
      for (Map.Entry<PageRef, Page> entry : notMissingChildren.entrySet()) {
        verifyParentToChild(pageRef, entry.getKey(), entry.getValue().getParentRefs());
      }
    }
  }
}
