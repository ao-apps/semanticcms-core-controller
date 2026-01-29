/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import static com.semanticcms.core.servlet.Resources.PACKAGE_RESOURCES;

import com.aoapps.encoding.Doctype;
import com.aoapps.encoding.Serialization;
import com.aoapps.encoding.taglib.EncodingBufferedTag;
import com.aoapps.io.buffer.BufferWriter;
import com.aoapps.io.buffer.EmptyResult;
import com.aoapps.lang.LocalizedIllegalStateException;
import com.aoapps.servlet.http.NullHttpServletResponseWrapper;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.impl.PageImpl;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;

public class Page {

  private final ServletContext servletContext;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  private PageRef pageRef;

  private ZonedDateTime dateCreated;
  private ZonedDateTime datePublished;
  private ZonedDateTime dateModified;
  private ZonedDateTime dateReviewed;

  private Serialization serialization;
  private Doctype doctype;
  private Boolean autonli;
  private Boolean indent;

  private final String title;

  private String shortTitle;
  private String description;
  private String keywords;
  private Boolean allowRobots;
  private Boolean toc;
  private int tocLevels = com.semanticcms.core.model.Page.DEFAULT_TOC_LEVELS;
  private boolean allowParentMismatch;
  private boolean allowChildMismatch;
  private Map<String, Object> properties;

  public Page(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      String title
  ) {
    this.servletContext = servletContext;
    this.request = request;
    this.response = response;
    this.title = title;
  }

  /**
   * Creates a new page in the current page context.
   *
   * @see  PageContext
   */
  public Page(String title) {
    this(
        PageContext.getServletContext(),
        PageContext.getRequest(),
        PageContext.getResponse(),
        title
    );
  }

  public Page pageRef(PageRef pageRef) {
    this.pageRef = pageRef;
    return this;
  }

  public Page dateCreated(ZonedDateTime dateCreated) {
    this.dateCreated = dateCreated;
    return this;
  }

  public Page datePublished(ZonedDateTime datePublished) {
    this.datePublished = datePublished;
    return this;
  }

  public Page dateModified(ZonedDateTime dateModified) {
    this.dateModified = dateModified;
    return this;
  }

  public Page dateReviewed(ZonedDateTime dateReviewed) {
    this.dateReviewed = dateReviewed;
    return this;
  }

  public Page serialization(Serialization serialization) {
    this.serialization = serialization;
    return this;
  }

  public Page doctype(Doctype doctype) {
    this.doctype = doctype;
    return this;
  }

  public Page autonli(Boolean autonli) {
    this.autonli = autonli;
    return this;
  }

  public Page indent(Boolean indent) {
    this.indent = indent;
    return this;
  }

  public Page shortTitle(String shortTitle) {
    this.shortTitle = shortTitle;
    return this;
  }

  public Page description(String description) {
    this.description = description;
    return this;
  }

  public Page keywords(String keywords) {
    this.keywords = keywords;
    return this;
  }

  public Page allowRobots(Boolean allowRobots) {
    this.allowRobots = allowRobots;
    return this;
  }

  public Page toc(Boolean toc) {
    this.toc = toc;
    return this;
  }

  public Page tocLevels(int tocLevels) {
    this.tocLevels = tocLevels;
    return this;
  }

  public Page allowParentMismatch(boolean allowParentMismatch) {
    this.allowParentMismatch = allowParentMismatch;
    return this;
  }

  public Page allowChildMismatch(boolean allowChildMismatch) {
    this.allowChildMismatch = allowChildMismatch;
    return this;
  }

  /**
   * Adds a property to the page.
   *
   * @throws  IllegalStateException  if the property with the given name has already been set
   */
  public Page property(String name, Object value) throws IllegalStateException {
    if (properties == null) {
      properties = new LinkedHashMap<>();
    } else if (properties.containsKey(name)) {
      throw new LocalizedIllegalStateException(
          PACKAGE_RESOURCES,
          "error.duplicatePageProperty",
          name
      );
    }
    properties.put(name, value);
    return this;
  }

  @FunctionalInterface
  public static interface Body {
    void doBody(HttpServletRequest req, HttpServletResponse resp, com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextBody {
    void doBody(com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextNoPageBody {
    void doBody() throws ServletException, IOException, SkipPageException;
  }

  /**
   * Sets request attribute "page" to the current page, and restores the previous "page"
   * attribute once completed.
   *
   * <p>Also establishes a new {@link PageContext}.</p>
   *
   * @see  PageContext
   * @see  CurrentPage#REQUEST_ATTRIBUTE
   */
  public void invoke(final Body body) throws ServletException, IOException, SkipPageException {
    PageRef pr = pageRef;
    if (pr == null) {
      pr = PageRefResolver.getCurrentPageRef(servletContext, request);
    }
    PageImpl.doPageImpl(
        servletContext,
        request,
        response,
        pr,
        dateCreated,
        datePublished,
        dateModified,
        dateReviewed,
        serialization,
        doctype,
        autonli,
        indent,
        title,
        shortTitle,
        description,
        keywords,
        allowRobots,
        toc,
        tocLevels,
        allowParentMismatch,
        allowChildMismatch,
        properties,
        body == null
            ? null
            : (discard, page) -> {
          if (discard) {
            final HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
            // Set PageContext
            PageContext.newPageContextSkip(
                servletContext,
                request,
                newResponse,
                () -> body.doBody(request, newResponse, page)
            );
            return EmptyResult.getInstance();
          } else {
            BufferWriter capturedOut = EncodingBufferedTag.newBufferWriter(request);
            try {
              try (PrintWriter capturedPW = new PrintWriter(capturedOut)) {
                final HttpServletResponse newResponse = new HttpServletResponseWrapper(response) {
                  @Override
                  public PrintWriter getWriter() throws IOException {
                    return capturedPW;
                  }

                  @Override
                  public ServletOutputStream getOutputStream() {
                    throw new NotImplementedException("getOutputStream not expected");
                  }
                };
                // Set PageContext
                PageContext.newPageContextSkip(
                    servletContext,
                    request,
                    newResponse,
                    () -> body.doBody(request, newResponse, page)
                );
                if (capturedPW.checkError()) {
                  throw new IOException("Error on capturing PrintWriter");
                }
              }
            } finally {
              capturedOut.close();
            }
            return capturedOut.getResult();
          }
        }
    );
  }

  /**
   * @see  Page#invoke(com.semanticcms.core.servlet.Page.Body)
   */
  public void invoke() throws ServletException, IOException, SkipPageException {
    invoke((Body) null);
  }

  /**
   * @see  Page#invoke(com.semanticcms.core.servlet.Page.Body)
   */
  public void invoke(final PageContextBody body) throws ServletException, IOException, SkipPageException {
    invoke(
        body == null
            ? null
            : (HttpServletRequest req, HttpServletResponse resp, com.semanticcms.core.model.Page page) -> body.doBody(page)
    );
  }

  /**
   * @see  Page#invoke(com.semanticcms.core.servlet.Page.Body)
   */
  public void invoke(final PageContextNoPageBody body) throws ServletException, IOException, SkipPageException {
    invoke(
        body == null
            ? null
            : (HttpServletRequest req, HttpServletResponse resp, com.semanticcms.core.model.Page page) -> body.doBody()
    );
  }
}
