/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.hodgepodge.util.Tuple2;
import com.aoapps.lang.validation.ValidationException;
import com.aoapps.net.Path;
import com.aoapps.servlet.http.Dispatcher;
import com.aoapps.servlet.http.HttpServletUtil;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.renderer.Renderer;
import com.semanticcms.core.resources.Resource;
import com.semanticcms.core.resources.ResourceConnection;
import com.semanticcms.core.resources.ResourceStore;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The main SemanticCMS controller.  This handles the request, serves resources,
 * resolves renderers, handles initial page captures, resolves views, and dispatches
 * to renderer -&gt; theme -&gt; view -&gt; element renderer.
 * <p>
 * See <a href="https://semanticcms.com/core/controller/request-flow.dia">request-flow.dia</a>.
 * </p>
 * <p>
 * This must follow {@link CacheFilter} in the filter chain.  TODO: Why?
 * </p>
 * <p>
 * This must be on the <code>REQUEST</code> dispatcher only.
 * TODO: Required on <code>ERROR</code>, too?
 * </p>
 * <p>
 * TODO: Support "*" for OPTIONS method?
 * </p>
 * TODO: Redirect "/" to root page of root book
 * TODO: Redirect /book/path not match book, must have following slash
 * TODO: Redirect /book/path/ to root page of book, when page "/" doesn't exist in the book, in the same renderer suffix
 */
public class Controller implements Filter {

	private static final String NON_HTTP_PASS_THROUGH_INIT_PARAM = "com.semanticcms.core.controller.Controller.nonHttpPassThrough";
	private static final String NO_BOOK_PASS_THROUGH_INIT_PARAM = "com.semanticcms.core.controller.Controller.noBookPassThrough";

	private ServletContext servletContext;

	/**
	 * Gets the servlet context used for this filter.
	 */
	protected final ServletContext getServletContext() {
		return servletContext;
	}

	@Override
	public void init(FilterConfig fc) {
		servletContext = fc.getServletContext();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if(
			!(request instanceof HttpServletRequest)
			|| !(response instanceof HttpServletResponse)
		) {
			// Is not HTTP
			doNotHttp(request, response, chain);
		} else {
			// Is HTTP
			doHttp((HttpServletRequest)request, (HttpServletResponse)response, chain);
		}
	}

	/**
	 * Passes the request, unaltered, for direct processing by the local servlet container.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation simply calls {@link FilterChain#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}
	 * </p>
	 */
	protected void doPassThrough(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		chain.doFilter(request, response);
	}

	/**
	 * Called for non-HTTP requests where request is not an {@link HttpServletRequest}
	 * or response is not an {@link HttpServletResponse}.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation will call {@link #doPassThrough(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)}
	 * when the servlet context init parameter named {@link #NON_HTTP_PASS_THROUGH_INIT_PARAM}
	 * exists and equals {@code true}.  Otherwise, throws {@link ServletException} indicating
	 * the request is not HTTP.
	 * </p>
	 */
	protected void doNotHttp(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		// TODO: Move to a /WEB-INF/semanticcms-core-controller.xml or books.xml?
		if(Boolean.parseBoolean(servletContext.getInitParameter(NON_HTTP_PASS_THROUGH_INIT_PARAM))) {
			doPassThrough(request, response, chain);
		} else {
			throw new ServletException("Request is not HTTP");
		}
	}

	/**
	 * Called when a resource is not found, not published, or protected.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * For GET, HEAD, or OPTIONS requests, sends error {@link HttpServletResponse#SC_NOT_FOUND}.
	 * For all other requests, sends {@link HttpServletResponse#SC_METHOD_NOT_ALLOWED}.
	 * </p>
	 *
	 * @see  HttpServletRequest#getMethod()
	 * @see  HttpServletResponse#sendError(int)
	 * @see  HttpServletResponse#SC_NOT_FOUND
	 * @see  HttpServletResponse#SC_METHOD_NOT_ALLOWED
	 */
	protected void doMethodCheckNotFound(
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException, ServletException {
		String method = request.getMethod();
		if(
			HttpServletUtil.METHOD_GET.equalsIgnoreCase(method)
			|| HttpServletUtil.METHOD_HEAD.equalsIgnoreCase(method)
			|| HttpServletUtil.METHOD_OPTIONS.equalsIgnoreCase(method)
		) {
			doNotFound(request, response);
		} else {
			doMethodNotAllowed(request, response);
		}
	}

	/**
	 * Sends error {@link HttpServletResponse#SC_NOT_FOUND}.
	 *
	 * @see  HttpServletResponse#sendError(int)
	 * @see  HttpServletResponse#SC_NOT_FOUND
	 */
	protected void doNotFound(
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException, ServletException {
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}

	/**
	 * Sends error {@link HttpServletResponse#SC_METHOD_NOT_ALLOWED}.
	 *
	 * @see  HttpServletResponse#sendError(int)
	 * @see  HttpServletResponse#SC_METHOD_NOT_ALLOWED
	 */
	protected void doMethodNotAllowed(
		HttpServletRequest request,
		HttpServletResponse response
	) throws IOException, ServletException {
		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}

	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader(
			"Allow",
			HttpServletUtil.METHOD_GET + ", " + HttpServletUtil.METHOD_HEAD + ", " + HttpServletUtil.METHOD_OPTIONS
		);
	}

	/**
	 * Finds the published book for the given request, if any.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link SemanticCMS#getPublishedBook(java.lang.String)}
	 * </p>
	 */
	protected Book getPublishedBook(SemanticCMS semanticCMS, String servletPath) {
		return semanticCMS.getPublishedBook(servletPath);
	}

	/**
	 * Called for HTTP requests.
	 *
	 * @see  #getPublishedBook(com.semanticcms.core.controller.SemanticCMS, java.lang.String)
	 * @see  #doNotPublishedBook(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, javax.servlet.FilterChain, com.semanticcms.core.controller.SemanticCMS, java.lang.String)
	 * @see  #doPublishedBook(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, javax.servlet.FilterChain, com.semanticcms.core.controller.SemanticCMS, java.lang.String, com.semanticcms.core.controller.Book, com.aoapps.net.Path)
	 */
	protected void doHttp(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		String servletPath = Dispatcher.getCurrentPagePath(request);
		Book publishedBook = getPublishedBook(semanticCMS, servletPath);
		if(publishedBook == null) {
			doNotPublishedBook(request, response, chain, semanticCMS, servletPath);
		} else {
			try {
				doPublishedBook(
					request,
					response,
					chain,
					semanticCMS,
					servletPath,
					publishedBook,
					Path.valueOf(servletPath.substring(publishedBook.bookRef.getPrefix().length()))
				);
			} catch(ValidationException e) {
				throw new ServletException(e);
			}
		}
	}

	/**
	 * Finds the local book for the given request, if any.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link SemanticCMS#getLocalBook(java.lang.String)}
	 * </p>
	 */
	protected Book getLocalBook(SemanticCMS semanticCMS, String servletPath) {
		return semanticCMS.getLocalBook(servletPath);
	}

	/**
	 * Called for HTTP requests that do not correspond to a published book.
	 */
	protected void doNotPublishedBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath
	) throws IOException, ServletException {
		Book localBook = getLocalBook(semanticCMS, servletPath);
		if(localBook == null) {
			doNotPublishedBookAndNotLocalBook(request, response, chain, semanticCMS, servletPath);
		} else {
			try {
				doNotPublishedBookAndLocalBook(
					request,
					response,
					chain,
					semanticCMS,
					servletPath,
					localBook,
					Path.valueOf(servletPath.substring(localBook.bookRef.getPrefix().length()))
				);
			} catch(ValidationException e) {
				throw new ServletException(e);
			}
		}
	}

	/**
	 * Called for HTTP requests that do not correspond to a published book and have no local book.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation will call {@link #doPassThrough(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)}
	 * when the servlet context init parameter named {@link #NO_BOOK_PASS_THROUGH_INIT_PARAM}
	 * does not exist or does not equal {@code false}.  Otherwise, calls {@link #doNotFound(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}.
	 * </p>
	 *
	 * @see  #NO_BOOK_PASS_THROUGH_INIT_PARAM
	 * @see  #doPassThrough(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 * @see  #doMethodCheckNotFound(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doNotPublishedBookAndNotLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath
	) throws IOException, ServletException {
		// TODO: Move to a /WEB-INF/semanticcms-core-controller.xml or books.xml?
		if(!"false".equalsIgnoreCase(servletContext.getInitParameter(NO_BOOK_PASS_THROUGH_INIT_PARAM))) {
			doPassThrough(request, response, chain);
		} else {
			doMethodCheckNotFound(request, response);
		}
	}

	/**
	 * Checks if a local book is protected.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link Book#isProtected()}
	 * </p>
	 */
	protected boolean isLocalBookProtected(Book localBook, Path localPath, HttpServletRequest request) {
		return localBook.isProtected();
	}

	/**
	 * Checks if a local book has pass-through enabled.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link Book#isPassThroughEnabled()}
	 * </p>
	 */
	protected boolean isLocalBookPassThroughEnabled(Book localBook, Path localPath, HttpServletRequest request) {
		return localBook.isPassThroughEnabled();
	}

	/**
	 * Called for HTTP requests that do not correspond to a published book but have a local book.
	 *
	 * @see  #isLocalBookProtected(com.semanticcms.core.controller.Book, com.aoapps.net.Path, javax.servlet.http.HttpServletRequest)
	 * @see  #isLocalBookPassThroughEnabled(com.semanticcms.core.controller.Book, com.aoapps.net.Path, javax.servlet.http.HttpServletRequest)
	 * @see  #doPassThrough(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 * @see  #doMethodCheckNotFound(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doNotPublishedBookAndLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book localBook,
		Path localPath
	) throws IOException, ServletException {
		if(
			!isLocalBookProtected(localBook, localPath, request)
			&& isLocalBookPassThroughEnabled(localBook, localPath, request)
		) {
			doPassThrough(request, response, chain);
		} else {
			doMethodCheckNotFound(request, response);
		}
	}

	/**
	 * Called for HTTP requests that map onto a published book.
	 */
	protected void doPublishedBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		String method = request.getMethod();
		if(
			HttpServletUtil.METHOD_GET.equalsIgnoreCase(method)
			|| HttpServletUtil.METHOD_HEAD.equalsIgnoreCase(method)
			|| HttpServletUtil.METHOD_OPTIONS.equalsIgnoreCase(method)
		) {
			doPublishedBookExpectedMethods(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
		} else {
			doPublishedBookOtherMethod(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
		}
	}

	/**
	 * Checks if a published book is protected.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link Book#isProtected()}
	 * </p>
	 */
	protected boolean isPublishedBookProtected(Book publishedBook, Path publishedPath, HttpServletRequest request) {
		return publishedBook.isProtected();
	}

	/**
	 * Checks if a published book has pass-through enabled.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link Book#isPassThroughEnabled()}
	 * </p>
	 */
	protected boolean isPublishedBookPassThroughEnabled(Book publishedBook, Path publishedPath, HttpServletRequest request) {
		return publishedBook.isPassThroughEnabled();
	}

	/**
	 * Checks if the published book is also a local book.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link Book#isLocal()}
	 * </p>
	 */
	protected boolean isPublishedBookLocal(Book publishedBook) {
		return publishedBook.isLocal();
	}

	/**
	 * Called for HTTP requests that map onto a published book and are not GET, HEAD, or OPTIONS method.
	 *
	 * @see  #isPublishedBookProtected(com.semanticcms.core.controller.Book, com.aoapps.net.Path, javax.servlet.http.HttpServletRequest)
	 * @see  #isPublishedBookPassThroughEnabled(com.semanticcms.core.controller.Book, com.aoapps.net.Path, javax.servlet.http.HttpServletRequest)
	 */
	protected void doPublishedBookOtherMethod(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		if(
			!isPublishedBookProtected(publishedBook, publishedPath, request)
			&& isPublishedBookPassThroughEnabled(publishedBook, publishedPath, request)
		) {
			if(isPublishedBookLocal(publishedBook)) {
				doPassThrough(request, response, chain);
			} else {
				Book localBook = getLocalBook(semanticCMS, servletPath);
				if(localBook == null) {
					doPublishedBookOtherMethodNoLocalBook(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
				} else {
					try {
						doPublishedBookOtherMethodLocalBook(
							request,
							response,
							chain,
							semanticCMS,
							servletPath,
							publishedBook,
							publishedPath,
							localBook,
							Path.valueOf(servletPath.substring(localBook.bookRef.getPrefix().length()))
						);
					} catch(ValidationException e) {
						throw new ServletException(e);
					}
				}
			}
		} else {
			doMethodNotAllowed(request, response);
		}
	}

	protected void doPublishedBookOtherMethodNoLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		doPassThrough(request, response, chain);
	}

	protected void doPublishedBookOtherMethodLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Book localBook,
		Path localPath
	) throws IOException, ServletException {
		if(
			!isLocalBookProtected(localBook, localPath, request)
			&& isLocalBookPassThroughEnabled(localBook, localPath, request)
		) {
			doPassThrough(request, response, chain);
		} else {
			assert
				!HttpServletUtil.METHOD_GET.equalsIgnoreCase(request.getMethod())
				&& !HttpServletUtil.METHOD_HEAD.equalsIgnoreCase(request.getMethod())
				&& !HttpServletUtil.METHOD_OPTIONS.equalsIgnoreCase(request.getMethod())
			;
			doMethodNotAllowed(request, response);
		}
	}

	/**
	 * Called for HTTP requests that map onto a published book and are GET, HEAD, or OPTIONS method.
	 */
	protected void doPublishedBookExpectedMethods(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		Tuple2<Renderer, Path> rendererAndPath = semanticCMS.getRendererAndPath(publishedPath);
		if(rendererAndPath == null) {
			doPublishedBookNoRenderer(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
		} else {
			Renderer renderer = rendererAndPath.getElement1();
			Path pagePath = rendererAndPath.getElement2();
			if(HttpServletUtil.METHOD_OPTIONS.equalsIgnoreCase(request.getMethod())) {
				doPublishedBookOptions(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, renderer, pagePath);
			} else {
				doPublishedBookGetHead(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, renderer, pagePath);
			}
		}
	}

	/**
	 * Called for HTTP requests that map onto a published book and are GET, HEAD, or OPTIONS method
	 * but have no matching renderer.
	 */
	protected void doPublishedBookNoRenderer(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		doPublishedBookNoPageFound(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
	}

	/**
	 * Called for HTTP requests that map onto a published book and the OPTIONS method.
	 */
	protected void doPublishedBookOptions(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Renderer renderer,
		Path pagePath
	) throws IOException, ServletException {
		Page page = CapturePage.capturePage(
			servletContext,
			request,
			response,
			new PageRef(publishedBook.bookRef, pagePath),
			CaptureLevel.PAGE
		);
		if(page == null) {
			doPublishedBookOptionsNoPageFound(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, renderer, pagePath);
		} else {
			doPublishedBookOptionsPageFound(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, renderer, page);
		}
	}

	/**
	 * Called for HTTP requests that map onto a published book and the OPTIONS method with no page found.
	 */
	protected void doPublishedBookOptionsNoPageFound(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Renderer renderer,
		Path pagePath
	) throws IOException, ServletException {
		doPublishedBookNoPageFound(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
	}

	/**
	 * Called for HTTP requests that map onto a published book and the OPTIONS method with page found.
	 */
	protected void doPublishedBookOptionsPageFound(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Renderer renderer,
		Page page
	) throws IOException, ServletException {
		doOptions(request, response);
	}

	/**
	 * Called for HTTP requests that map onto a published book and the GET or HEAD methods.
	 */
	protected void doPublishedBookGetHead(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Renderer renderer,
		Path pagePath
	) throws IOException, ServletException {
		Page page = CapturePage.capturePage(
			servletContext,
			request,
			response,
			new PageRef(publishedBook.bookRef, pagePath),
			renderer.getCaptureLevel()
		);
		if(page == null) {
			doPublishedBookNoPageFound(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
		} else {
			doRenderer(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, renderer, page);
		}
	}

	/**
	 * Called for HTTP requests that map onto a published book and are GET or HEAD methods
	 * and has page found.
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link RendererServlet#dispatch(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.renderer.Renderer, com.semanticcms.core.model.Page)}
	 * </p>
	 */
	protected void doRenderer(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Renderer renderer,
		Page page
	) throws IOException, ServletException {
		RendererServlet.dispatch(servletContext, request, response, renderer, page);
	}

	/**
	 * Called for HTTP requests that map onto a published book and are GET, HEAD, or OPTIONS method
	 * but have no page found.
	 */
	protected void doPublishedBookNoPageFound(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		if(isPublishedBookProtected(publishedBook, publishedPath, request)) {
			doNotFound(request, response);
		} else {
			ResourceStore store = publishedBook.getResources();
			Resource resource = store.getResource(publishedPath);
			ResourceConnection resourceConn = resource.open();
			try {
				if(!resourceConn.exists()) {
					resourceConn.close();
					resourceConn = null;
					doPublishedBookResourceNotExists(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, resource);
				} else {
					if(HttpServletUtil.METHOD_OPTIONS.equalsIgnoreCase(request.getMethod())) {
						resourceConn.close();
						resourceConn = null;
						doPublishedBookResourceExistsOptions(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, resource);
					} else {
						doPublishedBookResourceExistsGetHead(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, resourceConn);
					}
				}
			} finally {
				if(resourceConn != null) resourceConn.close();
			}
		}
	}

	protected void doPublishedBookResourceNotExists(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Resource resource
	) throws IOException, ServletException {
		if(!isPublishedBookPassThroughEnabled(publishedBook, publishedPath, request)) {
			doNotFound(request, response);
		} else {
			if(isPublishedBookLocal(publishedBook)) {
				doPublishedBookResourceNotExistsIsLocalBook(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath);
			} else {
				Book localBook = getLocalBook(semanticCMS, servletPath);
				if(localBook != null) {
					try {
						doPublishedBookResourceNotExistsLocalBook(
							request,
							response,
							chain,
							semanticCMS,
							servletPath,
							publishedBook,
							publishedPath,
							localBook,
							Path.valueOf(servletPath.substring(localBook.bookRef.getPrefix().length()))
						);
					} catch(ValidationException e) {
						throw new ServletException(e);
					}
				} else {
					doPublishedBookResourceNotExistsNoLocalBook(request, response, chain, semanticCMS, servletPath, publishedBook, publishedPath, resource);
				}
			}
		}
	}

	protected void doPublishedBookResourceNotExistsIsLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath
	) throws IOException, ServletException {
		doPassThrough(request, response, chain);
	}

	protected void doPublishedBookResourceNotExistsLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Book localBook,
		Path localPath
	) throws IOException, ServletException {
		if(
			!isLocalBookProtected(localBook, localPath, request)
			&& isLocalBookPassThroughEnabled(localBook, localPath, request)
		) {
			doPassThrough(request, response, chain);
		} else {
			assert
				HttpServletUtil.METHOD_GET.equalsIgnoreCase(request.getMethod())
				|| HttpServletUtil.METHOD_HEAD.equalsIgnoreCase(request.getMethod())
				|| HttpServletUtil.METHOD_OPTIONS.equalsIgnoreCase(request.getMethod())
			;
			doNotFound(request, response);
		}
	}

	protected void doPublishedBookResourceNotExistsNoLocalBook(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Resource resource
	) throws IOException, ServletException {
		doPassThrough(request, response, chain);
	}

	protected void doPublishedBookResourceExistsOptions(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		Resource resource
	) throws IOException, ServletException {
		doOptions(request, response);
	}

	/**
	 * <p>
	 * <b>Implementation Note:</b><br>
	 * This default implementation calls {@link ResourceServlet#dispatch(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.resources.ResourceConnection)}
	 * </p>
	 */
	protected void doPublishedBookResourceExistsGetHead(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain chain,
		SemanticCMS semanticCMS,
		String servletPath,
		Book publishedBook,
		Path publishedPath,
		ResourceConnection resourceConn
	) throws IOException, ServletException {
		ResourceServlet.dispatch(servletContext, request, response, resourceConn);
	}

	@Override
	public void destroy() {
		servletContext = null;
	}
}
