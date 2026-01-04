/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.html.servlet.DocumentEE;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.servlet.impl.NavigationTreeImpl;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class NavigationTree {

  private final ServletContext servletContext;
  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final Page root;

  private boolean skipRoot;
  private boolean yuiConfig;
  private boolean includeElements;
  private String target;
  private String thisBook;
  private String thisPage;
  private String linksToBook;
  private String linksToPage;
  private int maxDepth;

  public NavigationTree(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page root
  ) {
    this.servletContext = servletContext;
    this.request = request;
    this.response = response;
    this.root = root;
  }

  /**
   * Creates a new navigation tree in the current page context.
   *
   * @see  PageContext
   */
  public NavigationTree(Page root) {
    this(
        PageContext.getServletContext(),
        PageContext.getRequest(),
        PageContext.getResponse(),
        root
    );
  }

  public NavigationTree skipRoot(boolean skipRoot) {
    this.skipRoot = skipRoot;
    return this;
  }

  public NavigationTree yuiConfig(boolean yuiConfig) {
    this.yuiConfig = yuiConfig;
    return this;
  }

  public NavigationTree includeElements(boolean includeElements) {
    this.includeElements = includeElements;
    return this;
  }

  public NavigationTree target(String target) {
    this.target = target;
    return this;
  }

  public NavigationTree thisBook(String thisBook) {
    this.thisBook = thisBook;
    return this;
  }

  public NavigationTree thisPage(String thisPage) {
    this.thisPage = thisPage;
    return this;
  }

  public NavigationTree linksToBook(String linksToBook) {
    this.linksToBook = linksToBook;
    return this;
  }

  public NavigationTree linksToPage(String linksToPage) {
    this.linksToPage = linksToPage;
    return this;
  }

  public NavigationTree maxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
    return this;
  }

  public void invoke() throws ServletException, IOException {
    NavigationTreeImpl.writeNavigationTreeImpl(
        servletContext,
        request,
        response,
        new DocumentEE(servletContext, request, response),
        root,
        skipRoot,
        yuiConfig,
        includeElements,
        target,
        thisBook,
        thisPage,
        linksToBook,
        linksToPage,
        maxDepth
    );
  }
}
