/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2018, 2019, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.attribute.Attribute;
import com.aoapps.servlet.attribute.ScopeEE;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.renderer.PageRenderer;
import com.semanticcms.core.renderer.Renderer;
import com.semanticcms.core.renderer.servlet.ServletPageRenderer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Calls {@link Renderer} via {@link Renderer#newPageRenderer(com.semanticcms.core.model.Page, java.util.Map)}
 * and {@link PageRenderer#doRenderer(java.io.Writer)}.
 * Also sets the attributes required by {@link ServletPageRenderer}.
 */
@WebServlet(name = RendererServlet.NAME)
public class RendererServlet extends HttpServlet {

  protected static final String NAME = "com.semanticcms.core.controller.RendererServlet";

  protected static final ScopeEE.Request.Attribute<Renderer> RENDERER_REQUEST_PARAMETER =
    ScopeEE.REQUEST.attribute(RendererServlet.class.getName() + ".renderer");
  protected static final ScopeEE.Request.Attribute<Page> PAGE_REQUEST_PARAMETER =
    ScopeEE.REQUEST.attribute(RendererServlet.class.getName() + ".page");
  protected static final ScopeEE.Request.Attribute<PageRenderer> PAGE_RENDERER_REQUEST_PARAMETER =
    ScopeEE.REQUEST.attribute(RendererServlet.class.getName() + ".pageRenderer");

  private static final long serialVersionUID = 1L;

  public static void dispatch(
    ServletContext servletContext,
    HttpServletRequest request,
    HttpServletResponse response,
    Renderer renderer,
    Page page
  ) throws IOException, ServletException {
    RequestDispatcher dispatcher = servletContext.getNamedDispatcher(NAME);
    if (dispatcher == null) {
      throw new ServletException("RequestDispatcher not found: " + NAME);
    }
    try (
      Attribute.OldValue oldRenderer = RENDERER_REQUEST_PARAMETER.context(request).init(renderer);
      Attribute.OldValue oldPage     = PAGE_REQUEST_PARAMETER    .context(request).init(page)
    ) {
      dispatcher.forward(request, response);
    }
  }

  protected static PageRenderer getPageRenderer(ServletRequest request) throws ServletException {
    PageRenderer pageRenderer = PAGE_RENDERER_REQUEST_PARAMETER.context(request).get();
    if (pageRenderer == null) {
      throw new ServletException("Request parameter not set: " + PAGE_RENDERER_REQUEST_PARAMETER.getName());
    }
    return pageRenderer;
  }

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Renderer renderer = RENDERER_REQUEST_PARAMETER.context(request).get();
    if (renderer == null) {
      throw new ServletException("Request parameter not set: " + RENDERER_REQUEST_PARAMETER.getName());
    }
    Page page = PAGE_REQUEST_PARAMETER.context(request).get();
    if (page == null) {
      throw new ServletException("Request parameter not set: " + PAGE_REQUEST_PARAMETER.getName());
    }
    Map<String, Object> pageRendererAttributes = new HashMap<>();
    pageRendererAttributes.put(ServletPageRenderer.REQUEST_RENDERER_ATTRIBUTE, request);
    pageRendererAttributes.put(ServletPageRenderer.RESPONSE_RENDERER_ATTRIBUTE, response);
    try (PageRenderer pageRenderer = renderer.newPageRenderer(page, pageRendererAttributes)) {
      try (Attribute.OldValue oldPageRenderer = PAGE_RENDERER_REQUEST_PARAMETER.context(request).init(pageRenderer)) {
        super.service(request, response);
      }
    }
  }

  @Override
  protected long getLastModified(HttpServletRequest request) {
    try {
      long lastModified = getPageRenderer(request).getLastModified();
      return lastModified == 0 ? -1 : lastModified;
    } catch (IOException | ServletException e) {
      log(null, e);
      return -1;
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    PageRenderer pageRenderer = getPageRenderer(request);
    // TODO: Doctype and Serialization stuff here, or somewhere appropriate before Theme.doTheme is called (like in 1.x branch PageImpl.java)
    response.setContentType(pageRenderer.getContentType());
    long length = pageRenderer.getLength();
    if (length != -1) {
      if (length < 0) {
        throw new AssertionError();
      }
      response.setContentLengthLong(length);
    }
    pageRenderer.doRenderer(response.getWriter());
  }
}
