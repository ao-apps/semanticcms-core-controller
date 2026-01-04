/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.servlet.attribute.ScopeEE;
import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletRequest;

/**
 * Tracking of the current page during request processing and capturing.
 */
public final class CurrentPage {

  /** Make no instances. */
  private CurrentPage() {
    throw new AssertionError();
  }

  /**
   * Cleared and restored on request in CapturePage.
   */
  public static final ScopeEE.Request.Attribute<Page> REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(/*PageTag.class.getName()+".*/ "currentPage");

  /**
   * Gets the currentPage or <code>null</code> if not inside a <code>PageTag</code>.
   */
  public static Page getCurrentPage(ServletRequest request) {
    return REQUEST_ATTRIBUTE.context(request).get();
  }

  public static void setCurrentPage(ServletRequest request, Page page) {
    REQUEST_ATTRIBUTE.context(request).set(page);
  }
}
