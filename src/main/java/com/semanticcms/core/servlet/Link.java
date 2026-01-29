/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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
import com.aoapps.net.URIParameters;
import com.aoapps.servlet.http.NullHttpServletResponseWrapper;
import com.semanticcms.core.servlet.impl.LinkImpl;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;

public class Link {

  private final ServletContext servletContext;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  private String book;
  private String page;
  private String element;
  private boolean allowGeneratedElement;
  private String anchor;
  private String view = SemanticCMS.DEFAULT_VIEW_NAME;
  private boolean small;
  private URIParameters params;
  private boolean absolute;
  private boolean canonical;
  private Object clazz;

  public Link(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    this.servletContext = servletContext;
    this.request = request;
    this.response = response;
  }

  public Link(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      String page
  ) {
    this(servletContext, request, response);
    this.page = page;
  }

  public Link(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      String book,
      String page
  ) {
    this(servletContext, request, response, page);
    this.book = book;
  }

  public Link(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      String book,
      String page,
      String element
  ) {
    this(servletContext, request, response, book, page);
    this.element = element;
  }

  /**
   * Creates a new link in the current page context.
   *
   * @see  PageContext
   */
  public Link() {
    this(
        PageContext.getServletContext(),
        PageContext.getRequest(),
        PageContext.getResponse()
    );
  }

  /**
   * Creates a new link in the current page context.
   *
   * @see  PageContext
   */
  public Link(String page) {
    this();
    this.page = page;
  }

  /**
   * Creates a new link in the current page context.
   *
   * @see  PageContext
   */
  public Link(String book, String page) {
    this(page);
    this.book = book;
  }

  /**
   * Creates a new link in the current page context.
   *
   * @see  PageContext
   */
  public Link(String book, String page, String element) {
    this(book, page);
    this.element = element;
  }

  public Link book(String book) {
    this.book = book;
    return this;
  }

  public Link page(String page) {
    this.page = page;
    return this;
  }

  public Link element(String element) {
    this.element = element;
    return this;
  }

  public Link allowGeneratedElement(boolean allowGeneratedElement) {
    this.allowGeneratedElement = allowGeneratedElement;
    return this;
  }

  public Link anchor(String anchor) {
    this.anchor = anchor;
    return this;
  }

  public Link view(String view) {
    this.view = view;
    return this;
  }

  /**
   * When false, the default, will generate a &lt;a&gt; tag around the entire body.
   * Otherwise, will generate a &lt;span&gt; instead, with a small link added to
   * the end of the body.
   *
   * <p>Use of a small link can be helpful for usability, such as when the body is
   * a piece of information intended for quick copy/paste by the user.</p>
   */
  public Link small(boolean small) {
    this.small = small;
    return this;
  }

  public Link params(URIParameters params) {
    this.params = params;
    return this;
  }

  public Link absolute(boolean absolute) {
    this.absolute = absolute;
    return this;
  }

  public Link canonical(boolean canonical) {
    this.canonical = canonical;
    return this;
  }

  public Link clazz(Object clazz) {
    this.clazz = clazz;
    return this;
  }

  @FunctionalInterface
  public static interface Body {
    void doBody(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextBody {
    void doBody() throws ServletException, IOException, SkipPageException;
  }

  /**
   * Also establishes a new {@link PageContext}.
   *
   * @see  PageContext
   */
  public void invoke(final Body body) throws ServletException, IOException, SkipPageException {
    LinkImpl.writeLinkImpl(servletContext,
        request,
        response,
        new DocumentEE(servletContext, request, response),
        book,
        page,
        element,
        allowGeneratedElement,
        anchor,
        view,
        small,
        params,
        absolute,
        canonical,
        clazz,
        body == null
            ? null
            // Lamdba version not working with generic exceptions:
            : discard -> {
          if (discard) {
            final HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
            // Set PageContext
            PageContext.newPageContextSkip(
                servletContext,
                request,
                newResponse,
                () -> body.doBody(request, newResponse)
            );
          } else {
            body.doBody(request, response);
          }
        }
    );
  }

  /**
   * @see  Link#invoke(com.semanticcms.core.servlet.Link.Body)
   */
  public void invoke() throws ServletException, IOException, SkipPageException {
    invoke((Body) null);
  }

  /**
   * @see  Link#invoke(com.semanticcms.core.servlet.Link.Body)
   */
  public void invoke(PageContextBody body) throws ServletException, IOException, SkipPageException {
    invoke(
        body == null
            ? null
            : (HttpServletRequest req, HttpServletResponse resp) -> body.doBody()
    );
  }
}
