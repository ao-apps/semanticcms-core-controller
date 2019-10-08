/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2019  AO Industries, Inc.
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

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Resolves the cache to use for the current request.
 *
 * @see  CountConcurrencyListener
 */
public class CacheFilter implements Filter {

	private static final String CAPTURE_CACHE_REQUEST_ATTRIBUTE_NAME = CacheFilter.class.getName();

	private static final String EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME = CacheFilter.class.getName()+".exportCache";

	private static class ExportCacheLock {}
	private static final ExportCacheLock exportCacheLock = new ExportCacheLock();

	/**
	 * The number of milliseconds after the export cache is no longer considered valid.
	 */
	private static final long EXPORT_CAPTURE_PAGE_CACHE_TTL = 60 * 1000; // one minute

	/**
	 * Gets the cache to use for the current request.
	 * All uses of this cache must synchronize on the map itself.
	 *
	 * @throws IllegalStateException if the filter is not active on the current request
	 */
	public static Cache getCache(ServletRequest request) throws IllegalStateException {
		Cache cache = (Cache)request.getAttribute(CAPTURE_CACHE_REQUEST_ATTRIBUTE_NAME);
		if(cache == null) throw new IllegalStateException("cache not active on the current request");
		return cache;
	}

	/**
	 * To speed up an export, the elements are cached between requests.
	 * The first non-exporting request will clear this cache, and it will also
	 * be removed after a given number of seconds.
	 *
	 * TODO: Consider consequences of caching once we have a security model applied
	 */
	private static class ExportPageCache {
	
		private final SemanticCMS semanticCMS;

		/**
		 * When concurrent subrequests are enabled, use concurrent implementation.
		 * When export mode without subrequests, use synchronized since exports are typically
		 * done one request at a time.
		 */
		private final boolean concurrentSubrequests;

		private ExportPageCache(SemanticCMS semanticCMS, boolean concurrentSubrequests) {
			this.semanticCMS = semanticCMS;
			this.concurrentSubrequests = concurrentSubrequests;
		}

		/**
		 * The time the cache started, used for expiration.
		 */
		private long cacheStart;
		
		/**
		 * The currently active cache.
		 */
		private Cache cache;

		/**
		 * Invalidates the page cache if it has exceeded its TTL.
		 *
		 * @return true when the cache is now invalid
		 */
		boolean invalidateCache(long currentTime) {
			assert Thread.holdsLock(exportCacheLock);
			if(cache == null) {
				return true;
			} else if(
				currentTime >= (cacheStart + EXPORT_CAPTURE_PAGE_CACHE_TTL)
				// Handle system time changes
				|| currentTime <= (cacheStart - EXPORT_CAPTURE_PAGE_CACHE_TTL)
			) {
				cache = null;
				return true;
			} else {
				return false;
			}
		}

		/**
		 * Invalidates the cache, if needed, then gets the resulting cache.
		 */
		Cache getCache(long currentTime) {
			assert Thread.holdsLock(exportCacheLock);
			invalidateCache(currentTime);
			if(cache == null) {
				cacheStart = currentTime;
				cache = concurrentSubrequests ? new ConcurrentCache(semanticCMS) : new SynchronizedCache(semanticCMS);
			}
			return cache;
		}
	}

	private ServletContext servletContext;
	private boolean concurrentSubrequests;

	@Override
	public void init(FilterConfig config) throws ServletException {
		servletContext = config.getServletContext();
		concurrentSubrequests = SemanticCMS.getInstance(servletContext).getConcurrentSubrequests();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		@SuppressWarnings("unchecked")
		Cache cache = (Cache)request.getAttribute(CAPTURE_CACHE_REQUEST_ATTRIBUTE_NAME);
		if(cache == null) {
			boolean isExporting;
			if(request instanceof HttpServletRequest) {
				// TODO: Do we get rid of this export caching mode?  Or have Renderer affect caching mode?
				isExporting = false; // TODO: Headers.isExporting((HttpServletRequest)request);
			} else {
				isExporting = false;
			}
			synchronized(exportCacheLock) {
				ExportPageCache exportCache = (ExportPageCache)servletContext.getAttribute(EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME);
				if(isExporting) {
					if(exportCache == null) {
						exportCache = new ExportPageCache(SemanticCMS.getInstance(servletContext), concurrentSubrequests);
						servletContext.setAttribute(EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME, exportCache);
					}
					cache = exportCache.getCache(System.currentTimeMillis());
				} else {
					// Clean-up stale export cache
					if(exportCache != null) {
						if(exportCache.invalidateCache(System.currentTimeMillis())) {
							servletContext.removeAttribute(EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME);
						}
					}
				}
			}
			if(cache == null) {
				// Request-level cache when not exporting
				SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
				if(CountConcurrencyListener.useConcurrentSubrequests(request)) {
					cache = new ConcurrentCache(semanticCMS);
				} else {
					cache = new SingleThreadCache(semanticCMS);
				}
			}
			try {
				request.setAttribute(CAPTURE_CACHE_REQUEST_ATTRIBUTE_NAME, cache);
				chain.doFilter(request, response);
			} finally {
				request.removeAttribute(CAPTURE_CACHE_REQUEST_ATTRIBUTE_NAME);
			}
		} else {
			// Cache already set
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy() {
		servletContext = null;
	}
}
