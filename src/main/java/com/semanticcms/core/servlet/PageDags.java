/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for working with directed acyclic graphs (DAGs) of pages.
 */
public final class PageDags {

  /** Make no instances. */
  private PageDags() {
    throw new AssertionError();
  }

  public static List<Page> convertPageDagToList(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page rootPage,
      CaptureLevel level
  ) throws ServletException, IOException {
    final List<Page> list = new ArrayList<>();
    CapturePage.traversePagesDepthFirst(
        servletContext,
        request,
        response,
        rootPage,
        level,
        (Page page, int depth) -> {
          list.add(page);
          return null;
        },
        Page::getChildRefs,
        // Child not in missing book
        childPage -> childPage.getBook() != null,
        null
    );
    return Collections.unmodifiableList(list);
  }
}
