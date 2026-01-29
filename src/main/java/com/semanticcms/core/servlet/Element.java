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

import com.aoapps.encoding.taglib.EncodingBufferedTag;
import com.aoapps.io.buffer.BufferWriter;
import com.aoapps.lang.LocalizedIllegalStateException;
import com.aoapps.servlet.http.NullHttpServletResponseWrapper;
import com.semanticcms.core.model.ElementWriter;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.NodeBodyWriter;
import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.commons.lang3.NotImplementedException;

/**
 * The base for capturing elements.
 */
public abstract class Element<E extends com.semanticcms.core.model.Element> implements ElementWriter {

  protected final ServletContext servletContext;
  protected final HttpServletRequest request;
  protected final HttpServletResponse response;
  protected final E element;

  protected Element(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      E element
  ) {
    this.servletContext = servletContext;
    this.request = request;
    this.response = response;
    this.element = element;
  }

  public Element<E> id(String id) {
    this.element.setId(id);
    return this;
  }

  /**
   * Adds a property to the element.
   *
   * @throws  IllegalStateException  if the property with the given name has already been set
   */
  public Element<E> property(String name, Object value) throws IllegalStateException {
    if (!this.element.setProperty(name, value)) {
      throw new LocalizedIllegalStateException(
          PACKAGE_RESOURCES,
          "error.duplicateElementProperty",
          name
      );
    }
    return this;
  }

  @FunctionalInterface
  public static interface Body<E extends com.semanticcms.core.model.Element> {
    void doBody(HttpServletRequest req, HttpServletResponse resp, E element) throws ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextBody<E extends com.semanticcms.core.model.Element> {
    void doBody(E element) throws ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextNoElementBody {
    void doBody() throws ServletException, IOException, SkipPageException;
  }

  /**
   * Adds this element to the current page, if part of a page.
   * Sets this element as the current element.
   * Then, if not capturing or capturing META or higher, calls {@link Element#doBody}
   *
   * <p>Also establishes a new {@link PageContext}.</p>
   *
   * @see  PageContext
   */
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public void invoke(Body<? super E> body) throws ServletException, IOException, SkipPageException {
    // Get the current capture state
    CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
    if (captureLevel.compareTo(CaptureLevel.META) >= 0) {
      // Set currentNode
      Node parentNode = CurrentNode.getCurrentNode(request);
      CurrentNode.setCurrentNode(request, element);
      try {
        // Find the optional parent page
        Page currentPage = CurrentPage.getCurrentPage(request);
        if (currentPage != null) {
          currentPage.addElement(element);
        }

        Long elementKey;
        if (parentNode != null) {
          elementKey = parentNode.addChildElement(element, this);
        } else {
          elementKey = null;
        }
        // Freeze element once body done
        try {
          doBody(captureLevel, body);
        } finally {
          // Note: Page freezes all of its elements
          if (currentPage == null) {
            element.freeze();
          }
        }
        // Write now
        if (captureLevel == CaptureLevel.BODY) {
          PrintWriter out = response.getWriter();
          if (elementKey == null) {
            try {
              writeTo(out, new ServletElementContext(servletContext, request, response));
            } catch (Error | RuntimeException | ServletException | IOException | SkipPageException e) {
              throw e;
            } catch (Throwable t) {
              throw new ServletException(t);
            }
          } else {
            // Write an element marker instead
            // TODO: Do not write element marker for empty elements, such as passwordTable at http://localhost:8080/docs/ao/infrastructure/ao/regions/mobile-al/workstations/francis.aoindustries.com/
            NodeBodyWriter.writeElementMarker(elementKey, out);
          }
        }
      } finally {
        // Restore previous currentNode
        CurrentNode.setCurrentNode(request, parentNode);
      }
    }
  }

  /**
   * @see  Element#invoke(com.semanticcms.core.servlet.Element.Body)
   */
  public void invoke() throws ServletException, IOException, SkipPageException {
    invoke((Body<? super E>) null);
  }

  /**
   * @see  Element#invoke(com.semanticcms.core.servlet.Element.Body)
   */
  public void invoke(final PageContextBody<? super E> body) throws ServletException, IOException, SkipPageException {
    invoke(
        (body == null) ? null : (HttpServletRequest req, HttpServletResponse resp, E e) -> body.doBody(e)
    );
  }

  /**
   * @see  Element#invoke(com.semanticcms.core.servlet.Element.Body)
   */
  public void invoke(final PageContextNoElementBody body) throws ServletException, IOException, SkipPageException {
    invoke(
        (body == null) ? null : (HttpServletRequest req, HttpServletResponse resp, E e) -> body.doBody()
    );
  }

  /**
   * Only called at capture level of META and higher.
   */
  protected void doBody(CaptureLevel captureLevel, final Body<? super E> body) throws ServletException, IOException, SkipPageException {
    if (body != null) {
      if (captureLevel == CaptureLevel.BODY) {
        // Invoke tag body, capturing output
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
                () -> body.doBody(request, newResponse, element)
            );
            if (capturedPW.checkError()) {
              throw new IOException("Error on capturing PrintWriter");
            }
          }
        } finally {
          capturedOut.close();
        }
        element.setBody(capturedOut.getResult().trim());
      } else if (captureLevel == CaptureLevel.META) {
        // Invoke body for any meta data, but discard any output
        final HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
        // Set PageContext
        PageContext.newPageContextSkip(
            servletContext,
            request,
            newResponse,
            () -> body.doBody(request, newResponse, element)
        );
      } else {
        throw new AssertionError();
      }
    }
  }
}
