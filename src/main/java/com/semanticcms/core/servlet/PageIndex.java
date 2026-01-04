/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.collections.AoCollections;
import com.aoapps.lang.NullArgumentException;
import com.aoapps.servlet.attribute.ScopeEE;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Captures all pages recursively and builds an index of pages
 * for fast page number lookups.
 * The index may be created on the entire site or single subtree.
 * The index is used when presenting multiple pages in a combined view.
 */
public class PageIndex {

  /**
   * The request scope variable containing any active page index.
   */
  public static final ScopeEE.Request.Attribute<PageIndex> REQUEST_ATTRIBUTE = ScopeEE.REQUEST.attribute("pageIndex");

  /**
   * Gets the current page index setup by a combined view or <code>null</code>
   * if not doing a combined view.
   */
  public static PageIndex getCurrentPageIndex(ServletRequest request) {
    NullArgumentException.checkNotNull(request, "request");
    return REQUEST_ATTRIBUTE.context(request).get();
  }

  /**
   * Captures the root with META capture level and all children as PAGE.
   */
  public static PageIndex getPageIndex(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      PageRef rootPageRef
  ) throws ServletException, IOException {
    return new PageIndex(
        servletContext,
        request,
        response,
        CapturePage.capturePage(
            servletContext,
            request,
            response,
            rootPageRef,
            CaptureLevel.META
        )
    );
  }

  /**
   * Gets an id for use in referencing the page at the given index.
   * If the index is non-null, as in a combined view, will be "page#-id".
   * Otherwise, the id is unchanged.
   *
   * @param  id  optional, id not added when null or empty
   *
   * @see  #appendIdInPage(java.lang.Integer, java.lang.String, java.lang.Appendable)
   */
  public static String getRefId(Integer index, String id) {
    if (index != null) {
      String indexPlusOne = Integer.toString(index + 1);
      StringBuilder out = new StringBuilder(
          4 // "page"
              + indexPlusOne.length()
              + (
              id == null || id.isEmpty()
                  ? 0
                  : (
                  1 // '-'
                      + id.length()
              )
          )
      );
      out.append("page");
      out.append(indexPlusOne);
      if (id != null && !id.isEmpty()) {
        out.append('-');
        out.append(id);
      }
      return out.toString();
    } else {
      return id;
    }
  }

  /**
   * Gets an id for use in the current page.  If the page is part of a page index,
   * as in a combined view, will be "page#-id".  Otherwise, the id is unchanged.
   *
   * @param  id  optional, id not added when null or empty
   */
  public static String getRefId(
      ServletContext servletContext,
      HttpServletRequest request,
      PageIndex pageIndex,
      String id
  ) throws ServletException {
    // No page index
    if (pageIndex == null) {
      return id;
    }
    Integer index = pageIndex.getPageIndex(PageRefResolver.getCurrentPageRef(servletContext, request));
    // Page not in index
    if (index == null) {
      return id;
    }
    if (id == null || id.isEmpty()) {
      // Page in index
      return "page" + (index + 1);
    } else {
      // Page in index with id
      return "page" + (index + 1) + '-' + id;
    }
  }

  /**
   * Gets an id for use in the current page.  If the page is part of a page index,
   * as in a combined view, will be "page#-id".  Otherwise, the id is unchanged.
   *
   * @param  id  optional, id not added when null or empty
   */
  public static String getRefId(
      ServletContext servletContext,
      HttpServletRequest request,
      String id
  ) throws ServletException {
    return getRefId(
        servletContext,
        request,
        getCurrentPageIndex(request),
        id
    );
  }

  /**
   * Gets an id for use in referencing the given page.  If the page is part of a page index,
   * as in a combined view, will be "page#-id".  Otherwise, the id is unchanged.
   *
   * @param  id  optional, id not added when null or empty
   *
   * @see  #appendIdInPage(com.semanticcms.core.servlet.PageIndex, com.semanticcms.core.model.Page, java.lang.String, java.lang.Appendable)
   */
  public static String getRefIdInPage(
      PageIndex pageIndex,
      Page page,
      String id
  ) {
    // No page index
    if (pageIndex == null) {
      return id;
    }
    Integer index = pageIndex.getPageIndex(page.getPageRef());
    // Page not in index
    if (index == null) {
      return id;
    }
    if (id == null || id.isEmpty()) {
      // Page in index
      return "page" + (index + 1);
    } else {
      // Page in index with id
      return "page" + (index + 1) + '-' + id;
    }
  }

  /**
   * Gets an id for use in referencing the given page.  If the page is part of a page index,
   * as in a combined view, will be "page#-id".  Otherwise, the id is unchanged.
   *
   * @param  id  optional, id not added when null or empty
   *
   * @see  #appendIdInPage(com.semanticcms.core.servlet.PageIndex, com.semanticcms.core.model.Page, java.lang.String, java.lang.Appendable)
   */
  public static String getRefIdInPage(
      HttpServletRequest request,
      Page page,
      String id
  ) {
    return getRefIdInPage(getCurrentPageIndex(request), page, id);
  }

  /**
   * Appends an id for use in referencing the page at the given index.
   * If the index is non-null, as in a combined view, will be "page#-id".
   * Otherwise, the id is unchanged.
   *
   * @param  id  optional, id not added when null or empty
   */
  // TODO: Encoder variants
  public static void appendIdInPage(Integer index, String id, Appendable out) throws IOException {
    if (index != null) {
      out.append("page");
      out.append(Integer.toString(index + 1));
      if (id != null && !id.isEmpty()) {
        out.append('-');
      }
    }
    if (id != null && !id.isEmpty()) {
      out.append(id);
    }
  }

  /**
   * Appends an id for use in referencing the given page.
   *
   * @param  id  optional, id not added when null or empty
   *
   * @see  #getRefIdInPage(jakarta.servlet.http.HttpServletRequest, com.semanticcms.core.model.Page, java.lang.String)
   */
  public static void appendIdInPage(PageIndex pageIndex, Page page, String id, Appendable out) throws IOException {
    if (pageIndex != null && page != null) {
      appendIdInPage(
          pageIndex.getPageIndex(page.getPageRef()),
          id,
          out
      );
    } else {
      if (id != null && !id.isEmpty()) {
        out.append(id);
      }
    }
  }

  private final Page rootPage;
  private final List<Page> pageList;
  private final Map<PageRef, Integer> pageIndexes;

  private PageIndex(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page rootPage
  ) throws ServletException, IOException {
    this.rootPage = rootPage;
    this.pageList = PageDags.convertPageDagToList(
        servletContext,
        request,
        response,
        rootPage,
        CaptureLevel.PAGE
    );
    int size = pageList.size();
    // Index pages
    Map<PageRef, Integer> newPageIndexes = AoCollections.newHashMap(size);
    for (int i = 0; i < size; i++) {
      newPageIndexes.put(pageList.get(i).getPageRef(), i);
    }
    this.pageIndexes = Collections.unmodifiableMap(newPageIndexes);
  }

  /**
   * The root page, captured in META level.
   */
  public Page getRootPage() {
    return rootPage;
  }

  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public List<Page> getPageList() {
    return pageList;
  }

  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Map<PageRef, Integer> getPageIndexes() {
    return pageIndexes;
  }

  public Integer getPageIndex(PageRef pagePath) {
    return pageIndexes.get(pagePath);
  }
}
