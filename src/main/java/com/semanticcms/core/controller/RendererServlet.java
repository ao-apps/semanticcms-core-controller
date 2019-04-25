/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2018, 2019  AO Industries, Inc.
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
 * along with semanticcms-core-controller.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.controller;

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

	protected static final String RENDERER_REQUEST_PARAMETER = RendererServlet.class.getName() + ".renderer";
	protected static final String PAGE_REQUEST_PARAMETER = RendererServlet.class.getName() + ".page";
	protected static final String PAGE_RENDERER_REQUEST_PARAMETER = RendererServlet.class.getName() + ".pageRenderer";

	private static final long serialVersionUID = 1L;

	public static void dispatch(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Renderer renderer,
		Page page
	) throws IOException, ServletException {
		RequestDispatcher dispatcher = servletContext.getNamedDispatcher(NAME);
		if(dispatcher == null) throw new ServletException("RequestDispatcher not found: " + NAME);
		Object oldRenderer = request.getAttribute(RENDERER_REQUEST_PARAMETER);
		try {
			request.setAttribute(RENDERER_REQUEST_PARAMETER, renderer);
			Object oldPage = request.getAttribute(PAGE_REQUEST_PARAMETER);
			try {
				request.setAttribute(PAGE_REQUEST_PARAMETER, page);
				dispatcher.forward(request, response);
			} finally {
				request.setAttribute(PAGE_REQUEST_PARAMETER, oldPage);
			}
		} finally {
			request.setAttribute(RENDERER_REQUEST_PARAMETER, oldRenderer);
		}
	}

	protected static PageRenderer getPageRenderer(ServletRequest request) throws ServletException {
		PageRenderer pageRenderer = (PageRenderer)request.getAttribute(PAGE_RENDERER_REQUEST_PARAMETER);
		if(pageRenderer == null) throw new ServletException("Request parameter not set: " + PAGE_RENDERER_REQUEST_PARAMETER);
		return pageRenderer;
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Renderer renderer = (Renderer)request.getAttribute(RENDERER_REQUEST_PARAMETER);
		if(renderer == null) throw new ServletException("Request parameter not set: " + RENDERER_REQUEST_PARAMETER);
		Page page = (Page)request.getAttribute(PAGE_REQUEST_PARAMETER);
		if(page == null) throw new ServletException("Request parameter not set: " + PAGE_REQUEST_PARAMETER);
		Map<String,Object> pageRendererAttributes = new HashMap<>();
		pageRendererAttributes.put(ServletPageRenderer.REQUEST_RENDERER_ATTRIBUTE, request);
		pageRendererAttributes.put(ServletPageRenderer.RESPONSE_RENDERER_ATTRIBUTE, response);
		try (PageRenderer pageRenderer = renderer.newPageRenderer(page, pageRendererAttributes)) {
			Object oldPageRenderer = request.getAttribute(PAGE_RENDERER_REQUEST_PARAMETER);
			try {
				request.setAttribute(PAGE_RENDERER_REQUEST_PARAMETER, pageRenderer);
				super.service(request, response);
			} finally {
				request.setAttribute(PAGE_RENDERER_REQUEST_PARAMETER, oldPageRenderer);
			}
		}
	}

	@Override
	protected long getLastModified(HttpServletRequest request) {
		try {
			long lastModified = getPageRenderer(request).getLastModified();
			return lastModified == 0 ? -1 : lastModified;
		} catch(IOException | ServletException e) {
			log(null, e);
			return -1;
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		PageRenderer pageRenderer = getPageRenderer(request);
		response.setContentType(pageRenderer.getContentType());
		long length = pageRenderer.getLength();
		if(length != -1) {
			if(length < 0) throw new AssertionError();
			if(length <= Integer.MAX_VALUE) {
				response.setContentLength((int)length);
			} else {
				// TODO: Servlet 3.1: response.setContentLengthLong(length);
			}
		}
		pageRenderer.doRenderer(response.getWriter());
	}
}
