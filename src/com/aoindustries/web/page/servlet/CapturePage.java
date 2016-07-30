/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-web-page-servlet.
 *
 * ao-web-page-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-web-page-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-web-page-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.web.page.servlet;

import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.Page;
import com.aoindustries.web.page.PageRef;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class CapturePage {

	private static final String CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME = CapturePage.class.getName()+".captureContext";
	
	private static final String CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME = CapturePage.class.getName()+".capturePageCache";

	/**
	 * Gets the capture context or <code>null</code> if none occurring.
	 */
	public static CapturePage getCaptureContext(ServletRequest request) {
		return (CapturePage)request.getAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME);
	}

	/**
	 * Caches pages that have been captured within the scope of a single request.
	 *
	 * IDEA: Could possibly substitute pages with higher level capture, such as META capture in place of PAGE capture request.
	 * IDEA: Could also cache over time, since there is currently no concept of a "user" (except whether request is trusted
	 *       127.0.0.1 or not).
	 */
	static class CapturePageCacheKey {

		final PageRef pageRef;
		final CaptureLevel level;

		CapturePageCacheKey(
			PageRef pageRef,
			CaptureLevel level
		) {
			this.pageRef = pageRef;
			assert level != CaptureLevel.BODY : "Body captures are not cached";
			this.level = level;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof CapturePageCacheKey)) return false;
			CapturePageCacheKey other = (CapturePageCacheKey)o;
			return
				level==other.level
				&& pageRef.equals(other.pageRef)
			;
		}

		@Override
		public int hashCode() {
			int hash = level.hashCode();
			hash = hash * 31 + pageRef.hashCode();
			return hash;
		}

		@Override
		public String toString() {
			return '(' + level.toString() + ", " + pageRef.toString() + ')';
		}
	}

	/**
	 * Captures a page.
	 * The capture is always done with a request method of "GET", even when the enclosing request is a different method.
	 * Also validates parent-child and child-parent relationships if the other related pages happened to already be captured and cached.
	 */
	public static Page capturePage(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef pageRef,
		CaptureLevel level
	) throws ServletException, IOException {
		NullArgumentException.checkNotNull(level, "level");

		// Don't use cache for full body captures
		final boolean useCache = level != CaptureLevel.BODY;

		// Get the cache
		@SuppressWarnings("unchecked")
		Map<CapturePageCacheKey,Page> cache = (Map<CapturePageCacheKey,Page>)request.getAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME);
		if(cache == null) {
			cache = new HashMap<>();
			request.setAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME, cache);
		}

		// cacheKey will be null when this capture is not to be cached
		final CapturePageCacheKey cacheKey;
		Page capturedPage;
		if(useCache) {
			// Check the cache
			cacheKey = new CapturePageCacheKey(pageRef, level);
			capturedPage = cache.get(cacheKey);
		} else {
			cacheKey = null;
			capturedPage = null;
		}

		if(capturedPage == null) {
			// Perform new capture
			Node oldNode = CurrentNode.getCurrentNode(request);
			Page oldPage = CurrentPage.getCurrentPage(request);
			try {
				// Clear request values that break captures
				if(oldNode != null) CurrentNode.setCurrentNode(request, null);
				if(oldPage != null) CurrentPage.setCurrentPage(request, null);
				CaptureLevel oldCaptureLevel = CaptureLevel.getCaptureLevel(request);
				CapturePage oldCaptureContext = CapturePage.getCaptureContext(request);
				try {
					// Set new capture context
					CaptureLevel.setCaptureLevel(request, level);
					CapturePage captureContext = new CapturePage();
					request.setAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME, captureContext);
					// Include the page resource, discarding any direct output
					String capturePath = pageRef.getServletPath();
					try {
						// Clear PageContext on include
						PageContext.newPageContextSkip(
							null,
							null,
							null,
							() -> Dispatcher.include(
								servletContext,
								capturePath,
								// Always capture as "GET" request
								ServletUtil.METHOD_GET.equals(request.getMethod())
									// Is already "GET"
									? request
									// Wrap to make "GET"
									: new HttpServletRequestWrapper(request) {
										@Override
										public String getMethod() {
											return ServletUtil.METHOD_GET;
										}
									},
								new NullHttpServletResponseWrapper(response)
							)
						);
					} catch(SkipPageException e) {
						// An individual page may throw SkipPageException which only terminates
						// the capture, not the request overall
					}
					capturedPage = captureContext.getCapturedPage();
					if(capturedPage==null) throw new ServletException("No page captured, page=" + capturePath);
					PageRef capturedPageRef = capturedPage.getPageRef();
					if(!capturedPageRef.equals(pageRef)) throw new ServletException(
						"Captured page has unexpected pageRef.  Expected "
							+ pageRef
							+ " but got "
							+ capturedPageRef
					);
				} finally {
					// Restore previous capture context
					CaptureLevel.setCaptureLevel(request, oldCaptureLevel);
					request.setAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME, oldCaptureContext);
				}
			} finally {
				if(oldNode != null) CurrentNode.setCurrentNode(request, oldNode);
				if(oldPage != null) CurrentPage.setCurrentPage(request, oldPage);
			}
		}
		assert capturedPage != null;
		if(useCache) {
			// Add to cache
			cache.put(cacheKey, capturedPage);
		}
		// Verify parents that happened to already be cached
		if(!capturedPage.getAllowParentMismatch()) {
			for(PageRef parentRef : capturedPage.getParentPages()) {
				// Can't verify parent reference to missing book
				if(parentRef.getBook() != null) {
					// Check if parent in cache
					Page parentPage = cache.get(new CapturePageCacheKey(parentRef, CaptureLevel.PAGE));
					if(parentPage == null) parentPage = cache.get(new CapturePageCacheKey(parentRef, CaptureLevel.META));
					if(parentPage != null) {
						if(!parentPage.getChildPages().contains(pageRef)) {
							throw new ServletException(
								"The parent page does not have this as a child.  this="
									+ pageRef
									+ ", parent="
									+ parentRef
							);
						}
						// Verify parent's children that happened to already be cached since captures can happen in any order?
						// I can't create any scenario where this catches something not already caught by the one-level check
						/*
						if(!parentPage.getAllowChildMismatch()) {
							for(PageRef childRef : parentPage.getChildPages()) {
								// Can't verify child reference to missing book
								if(childRef.getBook() != null) {
									// Check if child in cache
									Page childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.PAGE));
									if(childPage == null) childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.META));
									if(childPage != null) {
										if(!childPage.getParentPages().contains(parentRef)) {
											throw new ServletException(
												"The child page does not have this as a parent.  this="
													+ parentRef
													+ ", child="
													+ childRef
											);
										}
									}
								}
							}
						}*/
					}
				}
			}
		}
		// Verify children that happened to already be cached
		if(!capturedPage.getAllowChildMismatch()) {
			for(PageRef childRef : capturedPage.getChildPages()) {
				// Can't verify child reference to missing book
				if(childRef.getBook() != null) {
					// Check if child in cache
					Page childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.PAGE));
					if(childPage == null) childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.META));
					if(childPage != null) {
						if(!childPage.getParentPages().contains(pageRef)) {
							throw new ServletException(
								"The child page does not have this as a parent.  this="
									+ pageRef
									+ ", child="
									+ childRef
							);
						}
						// Verify children's parents that happened to already be cached since captures can happen in any order?
						// I can't create any scenario where this catches something not already caught by the one-level check
					}
				}
			}
		}
		return capturedPage;
	}

	/**
	 * Captures a page in the current page context.
	 *
	 * @see  #capturePage(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.aoindustries.web.page.PageRef, com.aoindustries.web.page.servlet.CaptureLevel)
	 * @see  PageContext
	 */
	public static Page capturePage(PageRef pageRef, CaptureLevel level) throws ServletException, IOException {
		return capturePage(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			pageRef,
			level
		);
	}

	private CapturePage() {
	}

	private Page capturedPage;
	public void setCapturedPage(Page capturedPage) {
		NullArgumentException.checkNotNull(capturedPage, "page");
		if(this.capturedPage != null) {
			throw new IllegalStateException(
				"Cannot capture more than one page: first page="
				+ this.capturedPage.getPageRef()
				+ ", second page=" + capturedPage.getPageRef()
			);
		}
		this.capturedPage = capturedPage;
	}

	private Page getCapturedPage() {
		return capturedPage;
	}
}
