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

import com.aoapps.html.any.AnyDocument;
import com.aoapps.lang.io.ContentType;
import com.aoapps.servlet.ServletUtil;
import com.aoapps.servlet.http.HttpServletUtil;
import com.aoapps.servlet.http.Includer;
import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Automatically sets up the Page and the PageContext.
 *
 * @see  com.semanticcms.core.model.Page
 * @see  PageContext
 */
public abstract class PageServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  public static final Charset ENCODING = AnyDocument.ENCODING;

  /**
   * @see  com.semanticcms.core.model.Page#getTitle()
   */
  public abstract String getTitle();

  /**
   * @see  com.semanticcms.core.model.Page#getShortTitle()
   */
  public String getShortTitle() {
    return null;
  }

  /**
   * @see  com.semanticcms.core.model.Page#getDescription()
   */
  public String getDescription() {
    return null;
  }

  /**
   * @see  com.semanticcms.core.model.Page#getKeywords()
   */
  public String getKeywords() {
    return null;
  }

  /**
   * Defaults to null for "auto".
   *
   * @see  com.semanticcms.core.model.Page#getToc()
   */
  public Boolean getToc() {
    return null;
  }

  /**
   * @see  com.semanticcms.core.model.Page#getTocLevels()
   * @see  com.semanticcms.core.model.Page#DEFAULT_TOC_LEVELS
   */
  public int getTocLevels() {
    return Page.DEFAULT_TOC_LEVELS;
  }

  private static interface DoMethodCallable {
    void doMethod(Page page) throws ServletException, IOException, SkipPageException;
  }

  private void callInPage(HttpServletRequest req, HttpServletResponse resp, final DoMethodCallable method) throws ServletException, IOException {
    try {
      new com.semanticcms.core.servlet.Page(getServletContext(), req, resp, getTitle())
          .shortTitle(getShortTitle())
          .description(getDescription())
          .keywords(getKeywords())
          .toc(getToc())
          .tocLevels(getTocLevels())
          .invoke((HttpServletRequest req1, HttpServletResponse resp1, Page page) -> {
            resp1.setContentType(ContentType.XHTML);
            resp1.setCharacterEncoding(ENCODING.name());
            method.doMethod(page);
          });
    } catch (SkipPageException e) {
      Includer.setPageSkipped(req);
    }
  }

  @Override
  protected final void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    callInPage(req, resp, this::doGet);
  }

  /**
   * Page and the PageContext are already setup.
   * The response content type has been set to application/xhtml+xml.
   * The response character encoding has been set to {@link PageServlet#ENCODING}.
   */
  protected void doGet(Page page) throws ServletException, IOException, SkipPageException {
    Includer.sendError(PageContext.getRequest(), PageContext.getResponse(), HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    throw ServletUtil.SKIP_PAGE_EXCEPTION;
  }

  @Override
  protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    callInPage(req, resp, this::doPost);
  }

  /**
   * Page and the PageContext are already setup.
   * The response content type has been set to application/xhtml+xml.
   * The response character encoding has been set to {@link PageServlet#ENCODING}.
   */
  protected void doPost(Page page) throws ServletException, IOException, SkipPageException {
    Includer.sendError(PageContext.getRequest(), PageContext.getResponse(), HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    throw ServletUtil.SKIP_PAGE_EXCEPTION;
  }

  @Override
  protected final void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    callInPage(req, resp, this::doPut);
  }

  /**
   * Page and the PageContext are already setup.
   * The response content type has been set to application/xhtml+xml.
   * The response character encoding has been set to {@link PageServlet#ENCODING}.
   */
  protected void doPut(Page page) throws ServletException, IOException, SkipPageException {
    Includer.sendError(PageContext.getRequest(), PageContext.getResponse(), HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    throw ServletUtil.SKIP_PAGE_EXCEPTION;
  }

  @Override
  protected final void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    callInPage(req, resp, this::doDelete);
  }

  /**
   * Page and the PageContext are already setup.
   * The response content type has been set to application/xhtml+xml.
   * The response character encoding has been set to {@link PageServlet#ENCODING}.
   */
  protected void doDelete(Page page) throws ServletException, IOException, SkipPageException {
    Includer.sendError(PageContext.getRequest(), PageContext.getResponse(), HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    throw ServletUtil.SKIP_PAGE_EXCEPTION;
  }

  @Override
  protected final void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    callInPage(req, resp, this::doOptions);
  }

  /**
   * Page and the PageContext are already setup.
   * The response content type has been set to application/xhtml+xml.
   * The response character encoding has been set to {@link PageServlet#ENCODING}.
   */
  protected void doOptions(Page page) throws ServletException, IOException, SkipPageException {
    HttpServletUtil.doOptions(
        PageContext.getResponse(),
        PageServlet.class,
        this.getClass(),
        "doGet",
        "doPost",
        "doPut",
        "doDelete",
        new Class<?>[]{
            Page.class
        }
    );
  }
}
