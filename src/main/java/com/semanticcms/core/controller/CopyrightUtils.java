/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2019, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Copyright;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.pages.CaptureLevel;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright processing utilities.
 */
public final class CopyrightUtils {

  /** Make no instances. */
  private CopyrightUtils() {
    throw new AssertionError();
  }

  /**
   * Finds the effective copyright for the given page or null if none.
   * If a copyright is returned, all fields in the resulting copyright will be
   * non-null since all inheriting has been completed.
   *
   * <p>Any field not set (or set to null) will be inherited from the parent(s) in the same book.
   * Any field set to "" will have no value and not inherit from parents.</p>
   *
   * <p>If there are no parent pages in this same book, uses the fields from the book's copyright.</p>
   *
   * <p>When inheriting a field from multiple parent pages, the field must
   * have exactly the same value in all parents.  Any mismatch in value
   * will result in an exception.</p>
   */
  public static Copyright findCopyright(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      com.semanticcms.core.model.Page page
  ) throws ServletException, IOException {
    // TODO: Implemented as depth-first traversel from page up through parents
    Copyright copyright = findCopyrightRecursive(
        servletContext,
        request,
        response,
        SemanticCMS.getInstance(servletContext),
        page,
        new HashMap<>()
    );
    assert copyright == null || !copyright.isEmpty();
    return copyright;
  }

  private static Copyright findCopyrightRecursive(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      SemanticCMS semanticCms,
      com.semanticcms.core.model.Page page,
      Map<PageRef, Copyright> finished
  ) throws ServletException, IOException {
    PageRef pageRef = page.getPageRef();
    assert !finished.containsKey(pageRef);
    // Use directly set authors first
    Copyright pageCopyright = page.getCopyright();
    if (pageCopyright == null || !pageCopyright.hasAllFields()) {
      // Find the fields that do not need inherited
      String pageRightsHolder;
      String pageRights;
      String pageDateCopyrighted;
      if (pageCopyright == null) {
        pageRightsHolder = null;
        pageRights = null;
        pageDateCopyrighted = null;
      } else {
        pageRightsHolder = pageCopyright.getRightsHolder();
        pageRights = pageCopyright.getRights();
        pageDateCopyrighted = pageCopyright.getDateCopyrighted();
      }
      // Use the copyright fields of all parents in the same book
      String parentsRightsHolder = null;
      String parentsRights = null;
      String parentsDateCopyrighted = null;
      BookRef bookRef = pageRef.getBookRef();
      for (ParentRef parentRef : page.getParentRefs()) {
        PageRef parentPageRef = parentRef.getPageRef();
        if (bookRef.equals(parentPageRef.getBookRef())) {
          // Check finished already
          Copyright parentCopyright = finished.get(parentPageRef);
          if (!finished.containsKey(parentPageRef)) {
            // Capture parent and find its authors
            parentCopyright = findCopyrightRecursive(
                servletContext,
                request,
                response,
                semanticCms,
                CapturePage.capturePage(servletContext, request, response, parentPageRef, CaptureLevel.PAGE),
                finished
            );
          }
          if (pageRightsHolder == null) {
            String newRightsHolder = parentCopyright == null ? "" : parentCopyright.getRightsHolder();
            if (parentsRightsHolder == null) {
              parentsRightsHolder = newRightsHolder;
            } else {
              // Must precisely match when have multiple parents
              if (!parentsRightsHolder.equals(newRightsHolder)) {
                throw new ServletException("Mismatched rightsHolder inherited from different parents: " + parentsRightsHolder + " does not match " + newRightsHolder);
              }
            }
          }
          if (pageRights == null) {
            String newRights = parentCopyright == null ? "" : parentCopyright.getRights();
            if (parentsRights == null) {
              parentsRights = newRights;
            } else {
              // Must precisely match when have multiple parents
              if (!parentsRights.equals(newRights)) {
                throw new ServletException("Mismatched rights inherited from different parents: " + parentsRights + " does not match " + newRights);
              }
            }
          }
          if (pageDateCopyrighted == null) {
            String newDateCopyrighted = parentCopyright == null ? "" : parentCopyright.getDateCopyrighted();
            if (parentsDateCopyrighted == null) {
              parentsDateCopyrighted = newDateCopyrighted;
            } else {
              // Must precisely match when have multiple parents
              if (!parentsDateCopyrighted.equals(newDateCopyrighted)) {
                throw new ServletException("Mismatched dateCopyrighted inherited from different parents: " + parentsDateCopyrighted + " does not match " + newDateCopyrighted);
              }
            }
          }
        }
      }
      // No parents in the same book, use book copyright fields
      Copyright bookCopyright = semanticCms.getBook(bookRef).getCopyright();
      if (pageRightsHolder == null) {
        if (parentsRightsHolder == null) {
          parentsRightsHolder = bookCopyright == null ? "" : bookCopyright.getRightsHolder();
        }
        pageRightsHolder = parentsRightsHolder;
      }
      if (pageRights == null) {
        if (parentsRights == null) {
          parentsRights = bookCopyright == null ? "" : bookCopyright.getRights();
        }
        pageRights = parentsRights;
      }
      if (pageDateCopyrighted == null) {
        if (parentsDateCopyrighted == null) {
          parentsDateCopyrighted = bookCopyright == null ? "" : bookCopyright.getDateCopyrighted();
        }
        pageDateCopyrighted = parentsDateCopyrighted;
      }
      pageCopyright = new Copyright(pageRightsHolder, pageRights, pageDateCopyrighted);
    }
    if (pageCopyright.isEmpty()) {
      pageCopyright = null;
    }
    // Store in finished
    finished.put(pageRef, pageCopyright);
    return pageCopyright;
  }
}
