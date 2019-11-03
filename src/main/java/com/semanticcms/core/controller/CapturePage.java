/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019  AO Industries, Inc.
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

import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.servlet.subrequest.HttpServletSubRequest;
import com.aoindustries.servlet.subrequest.HttpServletSubRequestWrapper;
import com.aoindustries.servlet.subrequest.HttpServletSubResponse;
import com.aoindustries.servlet.subrequest.HttpServletSubResponseWrapper;
import com.aoindustries.servlet.subrequest.IHttpServletSubRequest;
import com.aoindustries.servlet.subrequest.IHttpServletSubResponse;
import com.aoindustries.servlet.subrequest.UnmodifiableCopyHttpServletRequest;
import com.aoindustries.servlet.subrequest.UnmodifiableCopyHttpServletResponse;
import com.aoindustries.tempfiles.TempFileContext;
import com.aoindustries.tempfiles.servlet.ServletTempFileContext;
import com.aoindustries.util.concurrent.Executor;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.PageReferrer;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.pages.PageRepository;
import com.semanticcms.core.pages.local.PageContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CapturePage {

	private static final boolean CONCURRENT_TRAVERSALS_ENABLED = true;

	private static final boolean DEBUG = false;
	private static final boolean DEBUG_NOW = false;

	/**
	 * Captures a page.
	 * The capture is always done with a request method of "GET", even when the enclosing request is a different method.
	 * Also validates parent-child and child-parent relationships if the other related pages happened to already be captured and cached.
	 *
	 * TODO: Within the scope of one request and cache, avoid capturing the same page at the same time (CurrencyLimiter applied to sub requests), is there a reasonable way to catch deadlock conditions?
	 *
	 * @param level  The minimum page capture level, note that a higher level might be substituted, such as a META capture in place of a PAGE request.
	 *
	 * @return  The captured page or {@code null} if page does not exist.
	 */
	public static Page capturePage(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageReferrer pageReferrer,
		CaptureLevel level
	) throws ServletException, IOException {
		return capturePage(
			servletContext,
			request,
			response,
			pageReferrer,
			level,
			CacheFilter.getCache(request)
		);
	}

	/**
	 * TODO: Support null level for non-capture fetches
	 * TODO: Rename this class to "PageCache"?
	 *
	 * @param cache  See {@link CacheFilter#getCache(javax.servlet.ServletRequest)}
	 *
	 * @return  The captured page or {@code null} if page does not exist.
	 */
	public static Page capturePage(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageReferrer pageReferrer,
		CaptureLevel level,
		Cache cache
	) throws ServletException, IOException {
		return capturePage(
			servletContext,
			request,
			response,
			new HttpServletSubRequestWrapper(request),
			new HttpServletSubResponseWrapper(response, ServletTempFileContext.getTempFileContext(request)),
			pageReferrer,
			level,
			cache
		);
	}

	private static Page capturePage(
		final ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		final IHttpServletSubRequest subRequest,
		final IHttpServletSubResponse subResponse,
		PageReferrer pageReferrer,
		final CaptureLevel level,
		Cache cache
	) throws ServletException, IOException {
		NullArgumentException.checkNotNull(level, "level");
		final PageRef pageRef = pageReferrer.getPageRef();

		// Don't use cache for full body captures
		boolean useCache = level != CaptureLevel.BODY;

		// cacheKey will be null when this capture is not to be cached
		final Cache.CaptureKey cacheKey;
		Page capturedPage;
		if(useCache) {
			// Check the cache
			cacheKey = new Cache.CaptureKey(pageRef, level);
			Cache.CaptureResult capturedResult = cache.get(cacheKey);
			if(capturedResult != null) {
				capturedPage = capturedResult.page;
				if(capturedPage == null) return null; // Cached page not found
				// Set useCache = false to not put back into the cache unnecessarily below
				useCache = false;
			} else {
				capturedPage = null;
			}
		} else {
			cacheKey = null;
			capturedPage = null;
		}

		if(capturedPage == null) {
			// Find the book
			SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			final BookRef bookRef = pageRef.getBookRef();
			Book book = semanticCMS.getBook(bookRef);
			if(!book.isAccessible()) throw new ServletException("Book is inaccessible: " + bookRef);
			final PageRepository repository = book.getPages();
			if(!repository.isAvailable()) throw new ServletException("Page repository is unavailable: " + repository);
			// TODO: A way to do this without a hard dependency on LocalPageRepository?
			capturedPage = PageContext.newPageContext(
				servletContext,
				subRequest,
				subResponse,
				() -> repository.getPage(pageRef.getPath(), level)
			);
			if(capturedPage != null) {
				PageRef capturedPageRef = capturedPage.getPageRef();
				if(!capturedPageRef.equals(pageRef)) throw new ServletException(
					"Captured page has unexpected pageRef.  Expected ("
						+ pageRef.getBookRef()+ ", " + pageRef.getPath()
						+ ") but got ("
						+ capturedPageRef.getBookRef() + ", " + capturedPageRef.getPath()
						+ ')'
				);
			}
		}
		if(useCache) {
			// Add to cache
			cache.put(cacheKey, capturedPage);
		} else {
			if(
				(
					// Body capture, performance is not the main objective, perform full child and parent verifications,
					// this will mean a "View All" will perform thorough verifications.
					level == CaptureLevel.BODY
					// Perform full verification now since not interacting with the page cache
					|| level == null
				) && capturedPage != null
			) {
				PageUtils.fullVerifyParentChild(servletContext, request, response, capturedPage);
			}
		}
		return capturedPage;
	}

	/**
	 * Captures a page in the current page context.
	 *
	 * @return  The captured page or {@code null} if page does not exist.
	 *
	 * @see  #capturePage(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.PageRef, com.semanticcms.core.servlet.CaptureLevel)
	 * @see  PageContext
	 */
	public static Page capturePage(
		PageReferrer pageReferrer,
		CaptureLevel level
	) throws ServletException, IOException {
		return capturePage(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			pageReferrer,
			level
		);
	}

	/**
	 * Captures multiple pages.
	 *
	 * @param  pageRefs  The pages that should be captured.  This set will be iterated only once during this operation.
	 *
	 * @return  map from pageRef to page, with iteration order equal to the provided pageRefs parameter.
	 *          the map will contain {@code null} values for pages not found.
	 *
	 * @see  #capturePage(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.PageRef, com.semanticcms.core.servlet.CaptureLevel)
	 */
	public static Map<PageRef,Page> capturePages(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		Set<? extends PageReferrer> pageReferrers,
		final CaptureLevel level
	) throws ServletException, IOException {
		int size = pageReferrers.size();
		if(size == 0) {
			return Collections.emptyMap();
		} else if(size == 1) {
			PageRef pageRef = pageReferrers.iterator().next().getPageRef();
			return Collections.singletonMap(
				pageRef,
				capturePage(servletContext, request, response, pageRef, level)
			);
		} else {
			final Cache cache = CacheFilter.getCache(request);
			Map<PageRef,Page> results = new LinkedHashMap<>(size * 4/3 + 1);
			List<PageReferrer> notCachedList = new ArrayList<>(size);
			if(level != CaptureLevel.BODY) {
				// Check cache before queuing on different threads, building list of those not in cache
				for(PageReferrer pageReferrer : pageReferrers) {
					PageRef pageRef = pageReferrer.getPageRef();
					Cache.CaptureResult captureResult = cache.get(pageRef, level);
					if(captureResult != null) {
						// Use cached value
						results.put(pageRef, captureResult.page);
					} else {
						// Will capture below
						notCachedList.add(pageRef);
					}
				}
			} else {
				notCachedList.addAll(pageReferrers);
			}

			int notCachedSize = notCachedList.size();
			if(
				notCachedSize > 1
				&& CountConcurrencyListener.useConcurrentSubrequests(request)
			) {
				// Concurrent implementation
				final TempFileContext tempFileContext = ServletTempFileContext.getTempFileContext(request);
				final HttpServletRequest threadSafeReq = new UnmodifiableCopyHttpServletRequest(request);
				final HttpServletResponse threadSafeResp = new UnmodifiableCopyHttpServletResponse(response);
				// Create the tasks
				List<Callable<Page>> tasks = new ArrayList<>(notCachedSize);
				for(int i=0; i<notCachedSize; i++) {
					final PageRef pageRef = notCachedList.get(i).getPageRef();
					tasks.add(
						() -> capturePage(
							servletContext,
							threadSafeReq,
							threadSafeResp,
							new HttpServletSubRequest(threadSafeReq),
							new HttpServletSubResponse(threadSafeResp, tempFileContext),
							pageRef,
							level,
							cache
						)
					);
				}
				List<Page> notCachedResults;
				try {
					notCachedResults = SemanticCMS.getInstance(servletContext).getExecutors().getPerProcessor().callAll(tasks);
				} catch(InterruptedException e) {
					throw new ServletException(e);
				} catch(ExecutionException e) {
					Throwable cause = e.getCause();
					if(cause instanceof RuntimeException) throw (RuntimeException)cause;
					if(cause instanceof ServletException) throw (ServletException)cause;
					if(cause instanceof IOException) throw (IOException)cause;
					throw new ServletException(cause);
				}
				for(int i=0; i<notCachedSize; i++) {
					results.put(
						notCachedList.get(i).getPageRef(),
						notCachedResults.get(i)
					);
				}
			} else {
				// Sequential implementation
				for(PageReferrer pageReferrer : notCachedList) {
					PageRef pageRef = pageReferrer.getPageRef();
					results.put(
						pageRef,
						capturePage(servletContext, request, response, pageRef, level, cache)
					);
				}
			}
			return Collections.unmodifiableMap(results);
		}
	}

	/**
	 * Captures multiple pages in the current page context.
	 *
	 * @see  #capturePages(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.util.Set, com.semanticcms.core.servlet.CaptureLevel)
	 * @see  PageContext
	 */
	public static Map<PageRef,Page> capturePages(
		Set<? extends PageReferrer> pageReferrers,
		CaptureLevel level
	) throws ServletException, IOException {
		return capturePages(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			pageReferrers,
			level
		);
	}

	@FunctionalInterface
	public static interface TraversalEdges {
		/**
		 * Gets the child pages to consider for the given page during a traversal.
		 * This may be called more than once per page per traversal and must give consistent results each call.
		 * The returned collection may be iterated more than once and must give consistent results each iteration.
		 * TODO: Make this Iterable?
		 */
		Collection<? extends PageReferrer> getEdges(Page page);
	}

	@FunctionalInterface
	public static interface EdgeFilter {
		/**
		 * Each edge returned is filtered through this, must return true for the
		 * edge to be considered.  This filter is not called when the edge has
		 * already been visited, however it might be called more than once during
		 * some concurrent implementations.  This filter must give consistent results
		 * when called more than once.
		 */
		boolean applyEdge(PageRef edge);
	}

	@FunctionalInterface
	public static interface PageHandler<T> {
		/**
		 * Called after page captured but before or after children captured.
		 *
		 * @return non-null value to terminate the traversal and return this value
		 */
		T handlePage(Page page) throws ServletException, IOException;
	}

	@FunctionalInterface
	public static interface PageDepthHandler<T> {
		/**
		 * Called after page captured but before or after children captured.
		 * Provided the current depth in the page tree, where 0 is the root node.
		 *
		 * @return non-null value to terminate the traversal and return this value
		 */
		T handlePage(Page page, int depth) throws ServletException, IOException;
	}

	/**
	 * @see  #traversePagesAnyOrder(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page, com.semanticcms.core.servlet.CaptureLevel, com.semanticcms.core.servlet.CapturePage.PageHandler, com.semanticcms.core.servlet.CapturePage.TraversalEdges, com.semanticcms.core.servlet.CapturePage.EdgeFilter)
	 */
	public static <T> T traversePagesAnyOrder(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageReferrer rootReferrer,
		CaptureLevel level,
		PageHandler<? extends T> pageHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter
	) throws ServletException, IOException {
		return traversePagesAnyOrder(
			servletContext,
			request,
			response,
			CapturePage.capturePage(
				servletContext,
				request,
				response,
				rootReferrer,
				level
			),
			level,
			pageHandler,
			edges,
			edgeFilter
		);
	}

	/**
	 * <p>
	 * Performs potentially concurrent traversal of the pages in any order.
	 * Each page is only visited once.
	 * </p>
	 * <p>
	 * This may at times appear to give results in a predictable order, but this must not be relied upon.
	 * For example, with all items already in cache it might end up giving results in a breadth-first order,
	 * whereas the same situation on a single-CPU system might end up in a depth-first order.  The ordering
	 * is not guaranteed in any way and should not be relied upon.
	 * </p>
	 * <p>
	 * pageHandler, edges, and edgeFilter are all called on the main thread (the thread invoking this method).
	 * <p>
	 * Returns when the first pageHandler returns a non-null object.
	 * Once a pageHandler returns non-null, no other pageHandler,
	 * edges, or edgeFilter will be called.
	 * </p>
	 * <p>
	 * Due to pageHandlers, edges, and edgeFilter all being called on the main thread, slow implementations
	 * of these methods may limit effective concurrency.  A future improvement might be to allow for concurrent
	 * execution of handlers.
	 * </p>
	 * <p>
	 * If a page is already in the cache, it is fetched directly instead of passed-off to a separate
	 * thread for capture.  Thus, if all is cached, this method will not perform with any concurrency.
	 * </p>
	 *
	 * @param level        The captureLevel.  A higher captureLevel may be returned when it is available, such
	 *                     as a META capture in place of a PAGE request.
	 *
	 * @param pageHandler  Optional, null when not needed, called before a page visits it's edges.
	 *                     If returns a non-null object, the traversal is terminated and the provided object
	 *                     is returned.
	 *
	 * @param edges        Provides the set of pages to looked from the given page.  Any edge provided that
	 *                     has already been visited will not be visited again.
	 *
	 * @param edgeFilter   Optional, null when not needed and will match all edges.
	 */
	public static <T> T traversePagesAnyOrder(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page root,
		CaptureLevel level,
		final PageHandler<? extends T> pageHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter
	) throws ServletException, IOException {
		Cache cache = level == CaptureLevel.BODY ? null : CacheFilter.getCache(request);
		if(
			CONCURRENT_TRAVERSALS_ENABLED
			&& CountConcurrencyListener.useConcurrentSubrequests(request)
		) {
			return traversePagesAnyOrderConcurrent(
				servletContext,
				request,
				response,
				root,
				level,
				pageHandler,
				edges,
				edgeFilter,
				cache,
				null
			);
		} else {
			return traversePagesDepthFirstRecurseSequential(
				servletContext,
				request,
				response,
				root,
				0,
				level,
				(Page page, int depth) -> pageHandler.handlePage(page),
				edges,
				edgeFilter,
				null,
				ServletTempFileContext.getTempFileContext(request),
				cache,
				new HashSet<>()
			);
		}
	}

	private static PageRef getNext(PageRef[] nextHint) {
		return nextHint == null ? null : nextHint[0];
	}

	/**
	 * @param nextHint  an optional one-element array containing what is needed next.
	 *                  if non-null and contains non-null element, any future task for that page
	 *                  that is not yet scheduled will be moved to the front of the list.
	 *                  TODO: Do max concurrency - 1, except nextHint?  Then can always schedule at least nextHint immediately.
	 *                  TODO: Once we get a result matching nextHint, move its children to the top of the stack so we get them first, let first child of nextHint occupy last slot.
	 */
	private static <T> T traversePagesAnyOrderConcurrent(
		final ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		final CaptureLevel level,
		PageHandler<? extends T> pageHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		final Cache cache,
		PageRef[] nextHint
	) throws ServletException, IOException {
		// Created when first needed to avoid the overhead when fully operating from cache
		HttpServletRequest threadSafeReq = null;
		HttpServletResponse threadSafeResp = null;
		// Find the executor
		final Executor concurrentSubrequestExecutor;
		final int preferredConcurrency;
		{ // Scoping block
			final Executors executors = SemanticCMS.getInstance(servletContext).getExecutors();
			concurrentSubrequestExecutor = executors.getPerProcessor();
			preferredConcurrency = executors.getPreferredConcurrency();
			assert preferredConcurrency > 1 : "Single-CPU systems should never make it to this concurrent implementation";
		}
		final TempFileContext tempFileContext = ServletTempFileContext.getTempFileContext(request);

		int maxSize = 0;

		// The which pages have been visited
		final Set<PageRef> visited = new HashSet<>();
		// The pages that are currently ready for processing
		final List<Page> readyPages = new ArrayList<>();
		// New ready pages, used to add in the correct order to readyPages based on traversal direction hints
		final List<Page> newReadyPages = new ArrayList<>();
		// Track which futures have been completed (callable put itself here once done)
		final BlockingQueue<PageRef> finishedFutures = new ArrayBlockingQueue<>(preferredConcurrency);
		// Does not immediately submit to the executor, waits until the readyPages are exhausted
		final List<PageRef> edgesToAdd = new ArrayList<>();
		// New edges to add, used to add in the correct order to edgesToAdd based on traversal direction hints
		final List<PageRef> newEdgesToAdd = new ArrayList<>();
		// The futures are queued, active, or finished but not yet processed by main thread
		final Map<PageRef,Future<Page>> futures = new HashMap<>(preferredConcurrency * 4/3+1);
		try {
			// Kick it off
			visited.add(page.getPageRef());
			readyPages.add(page);
			// The most recently seen nextHint
			PageRef next = getNext(nextHint);
			do {
				// Handle all the ready pages (using stack-ordering to achieve depth-first ordering from cache)
				while(!readyPages.isEmpty()) {
					Page readyPage = null;
					if(next != null) {
						// Search readyPages for "next", searching backwards assuming depth-first
						// TODO: This is sequential search
						for(int i=readyPages.size()-1; i >= 0; i--) {
							Page rp = readyPages.get(i);
							if(rp.getPageRef().equals(next)) {
								if(DEBUG_NOW && i != (readyPages.size()-1)) System.err.println("Found next in readyPages at index " + i + ", size = " + readyPages.size());
								readyPage = rp;
								readyPages.remove(i);
								break;
							}
						}
					}
					if(readyPage == null) {
						// Pop off stack
						readyPage = readyPages.remove(readyPages.size() - 1);
					}
					if(pageHandler != null) {
						T result = pageHandler.handlePage(readyPage);
						if(result != null) {
							return result;
						}
					}
					// Update next from any hint
					next = getNext(nextHint);
					// Add any children not yet visited
					for(PageReferrer edgeRef : edges.getEdges(readyPage)) {
						PageRef edge = edgeRef.getPageRef();
						if(
							!visited.contains(edge)
							&& (
								edgeFilter == null
								|| edgeFilter.applyEdge(edge)
							)
						) {
							visited.add(edge);
							// Check cache before going to concurrency
							Cache.CaptureResult cached;
							if(level == CaptureLevel.BODY) {
								cached = null;
							} else {
								cached = cache.get(edge, level);
							}
							if(cached != null) {
								newReadyPages.add(cached.page); // TODO: What to do with null pages here?  Error when traversal gets page not found?
							} else {
								newEdgesToAdd.add(edge);
							}
						}
					}
					// Add to readyPages in backwards order, so they pop off the top in correct traversal order
					while(!newReadyPages.isEmpty()) {
						readyPages.add(newReadyPages.remove(newReadyPages.size()-1));
					}
				}
				// Add to edgesToAdd in backwards order, so they pop off the top in correct traversal order
				while(!newEdgesToAdd.isEmpty()) {
					edgesToAdd.add(newEdgesToAdd.remove(newEdgesToAdd.size()-1));
				}

				// Run on this thread if there is only one
				if(futures.isEmpty() && edgesToAdd.size() == 1) {
					if(DEBUG) System.err.println("There is only one, running on current thread");
					readyPages.add(
						// TODO: What to do when null?
						capturePage(
							servletContext,
							request,
							response,
							edgesToAdd.remove(0),
							level,
							cache
						)
					);
				} else {
					if(!edgesToAdd.isEmpty()) {
						if(threadSafeReq == null) {
							threadSafeReq = new UnmodifiableCopyHttpServletRequest(request);
							threadSafeResp = new UnmodifiableCopyHttpServletResponse(response);
						}
						final HttpServletRequest finalThreadSafeReq = threadSafeReq;
						final HttpServletResponse finalThreadSafeResp = threadSafeResp;
						// Use hint, make sure it is end of edgesToAdd if in the list
						if(next != null) {
							// TODO: This is sequential search
							int i = edgesToAdd.lastIndexOf(next);
							if(i != -1) {
								if(DEBUG_NOW && i != (edgesToAdd.size()-1)) System.err.println("Found next in edgesToAdd at index " + i + ", size = " + edgesToAdd.size());
								edgesToAdd.add(edgesToAdd.remove(i));
							}
						}
						// Submit to the futures, but only up to preferredConcurrency
						while(
							futures.size() < preferredConcurrency
							&& !edgesToAdd.isEmpty()
						) {
							final PageRef edge = edgesToAdd.remove(edgesToAdd.size() - 1);
							futures.put(
								edge,
								concurrentSubrequestExecutor.submit(() -> {
									try {
										// TODO: What to do when returns null?
										return capturePage(
											servletContext,
											finalThreadSafeReq,
											finalThreadSafeResp,
											new HttpServletSubRequest(finalThreadSafeReq),
											new HttpServletSubResponse(finalThreadSafeResp, tempFileContext),
											edge,
											level,
											cache
										);
									} finally {
										// This one is ready now
										// There should always be enough room in the queue since the futures are limited going in
										finishedFutures.add(edge);
									}
								})
							);
						}
						if(DEBUG) {
							int futuresSize = futures.size();
							int edgesToAddSize = edgesToAdd.size();
							int size = futuresSize + edgesToAddSize;
							if(size > maxSize) {
								if(DEBUG) System.err.println("futures.size()=" + futuresSize + ", edgesToAdd.size()=" + edgesToAddSize);
								maxSize = size;
							}
						}
					}
					// Continue until no more futures
					if(!futures.isEmpty()) {
						Future<Page> future = null;
						// Favor nextHint on which future to consume first
						if(next != null) {
							Future<Page> nextsFuture = futures.get(next);
							if(nextsFuture.isDone()) {
								if(DEBUG_NOW) {
									PageRef nextFinished = finishedFutures.peek();
									if(!nextFinished.equals(next)) {
										System.err.println("Found nextHint done early in futures: " + next +", nextFinished = " + nextFinished);
									}
								}
								if(!finishedFutures.remove(next)) throw new AssertionError("done future not removed from finishedFutures");
								futures.remove(next);
								future = nextsFuture;
							}
						}
						if(future == null) {
							// wait until a result is available
							future = futures.remove(finishedFutures.take());
						}
						readyPages.add(future.get());
					}
				}
			} while(!readyPages.isEmpty());
			// Traversal over, not found
			return null;
		} catch(InterruptedException e) {
			throw new ServletException(e);
		} catch(ExecutionException e) {
			Throwable cause = e.getCause();
			if(cause instanceof RuntimeException) throw (RuntimeException)cause;
			if(cause instanceof ServletException) throw (ServletException)cause;
			if(cause instanceof IOException) throw (IOException)cause;
			throw new ServletException(cause);
		} finally {
			// Always cancel unfinished futures on the way out, but do not delay for any in progress
			if(!futures.isEmpty()) {
				if(DEBUG) System.err.println("Canceling " + futures.size() + " futures");
				for(Future<Page> future : futures.values()) {
					future.cancel(false);
				}
			}
		}
	}

	/**
	 * @see  #traversePagesDepthFirst(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page, com.semanticcms.core.servlet.CaptureLevel, com.semanticcms.core.servlet.CapturePage.PageHandler, com.semanticcms.core.servlet.CapturePage.TraversalEdges, com.semanticcms.core.servlet.CapturePage.PageHandler)
	 */
	public static <T> T traversePagesDepthFirst(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageReferrer rootReferrer,
		CaptureLevel level,
		PageDepthHandler<? extends T> preHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		PageDepthHandler<? extends T> postHandler
	) throws ServletException, IOException {
		return traversePagesDepthFirst(
			servletContext,
			request,
			response,
			CapturePage.capturePage(
				servletContext,
				request,
				response,
				rootReferrer,
				level
			),
			level,
			preHandler,
			edges,
			edgeFilter,
			postHandler
		);
	}

	/**
	 * <p>
	 * Performs a consistent-ordered, potentially concurrent, depth-first traversal of the pages.
	 * Each page is only visited once.
	 * </p>
	 * <p>
	 * preHandler, edges, edgeFilter, and postHandler are all called on the main thread (the thread invoking this method).
	 * <p>
	 * Returns when the first preHandler or postHandler returns a non-null object.
	 * Once a preHandler or postHandler returns non-null, no other preHandler,
	 * edges, edgeFilter, or postHandler will be called.
	 * </p>
	 * <p>
	 * Due to preHandlers, edges, edgeFilter, and postHandler all being called on the main thread, slow implementations
	 * of these methods may limit effective concurrency.  A future improvement might be to allow for concurrent
	 * execution of handlers.
	 * </p>
	 * <p>
	 * If a page is already in the cache, it is fetched directly instead of passed-off to a separate
	 * thread for capture.  Thus, if all is cached, this method will not perform with any concurrency.
	 * </p>
	 *
	 * @param level        The captureLevel.  A higher captureLevel may be returned when it is available, such
	 *                     as a META capture in place of a PAGE request.
	 *
	 * @param preHandler   Optional, null when not needed, called before a page visits it's edges.
	 *                     If returns a non-null object, the traversal is terminated and the provided object
	 *                     is returned.
	 *
	 * @param edges        Provides the set of pages to looked from the given page.  Any edge provided that
	 *                     has already been visited will not be visited again.
	 *
	 * @param edgeFilter   Optional, null when not needed and will match all edges.
	 *
	 * @param postHandler  Optional, null when not needed, called before a page visits it's edges.
	 *                     If returns a non-null object, the traversal is terminated and the provided object
	 *                     is returned.
	 */
	public static <T> T traversePagesDepthFirst(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page root,
		CaptureLevel level,
		PageDepthHandler<? extends T> preHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		PageDepthHandler<? extends T> postHandler
	) throws ServletException, IOException {
		Cache cache = level == CaptureLevel.BODY ? null : CacheFilter.getCache(request);
		if(
			CONCURRENT_TRAVERSALS_ENABLED
			&& CountConcurrencyListener.useConcurrentSubrequests(request)
		) {
			return traversePagesDepthFirstConcurrent(
				servletContext,
				request,
				response,
				root,
				level,
				preHandler,
				edges,
				edgeFilter,
				postHandler,
				cache
			);
		} else {
			return traversePagesDepthFirstRecurseSequential(
				servletContext,
				request,
				response,
				root,
				0,
				level,
				preHandler,
				edges,
				edgeFilter,
				postHandler,
				ServletTempFileContext.getTempFileContext(request),
				cache,
				new HashSet<>()
			);
		}
	}

	/**
	 * Simple sequential implementation.
	 */
	private static <T> T traversePagesDepthFirstRecurseSequential(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		int depth,
		CaptureLevel level,
		PageDepthHandler<? extends T> preHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		PageDepthHandler<? extends T> postHandler,
		TempFileContext tempFileContext,
		Cache cache,
		Set<PageRef> visited
	) throws ServletException, IOException {
		if(!visited.add(page.getPageRef())) throw new AssertionError();
		if(preHandler != null) {
			T result = preHandler.handlePage(page, depth);
			if(result != null) return result;
		}
		for(PageReferrer edgeRef : edges.getEdges(page)) {
			PageRef edge = edgeRef.getPageRef();
			if(
				!visited.contains(edge)
				&& (
					edgeFilter == null
					|| edgeFilter.applyEdge(edge)
				)
			) {
				T result = traversePagesDepthFirstRecurseSequential(
					servletContext,
					request,
					response,
					// TODO: What to do when returns null?
					CapturePage.capturePage(
						servletContext,
						request,
						response,
						edge,
						level,
						cache
					),
					depth + 1,
					level,
					preHandler,
					edges,
					edgeFilter,
					postHandler,
					tempFileContext,
					cache,
					visited
				);
				if(result != null) return result;
			}
		}
		if(postHandler != null) {
			T result = postHandler.handlePage(page, depth);
			if(result != null) return result;
		}
		return null;
	}

	private static <T> T traversePagesDepthFirstConcurrent(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		final Page page,
		CaptureLevel level,
		final PageDepthHandler<? extends T> preHandler,
		final TraversalEdges edges,
		final EdgeFilter edgeFilter,
		final PageDepthHandler<? extends T> postHandler,
		Cache cache
	) throws ServletException, IOException {
		// Caches the results of edges call, to fit within specification that it will only be called once per page.
		// This also prevents the chance that caller can give different results or change the collection during traversal.
		// The next item is desired is shared with the underlying traversal
		final PageRef[] nextHint = new PageRef[] {page.getPageRef()};
		T result = traversePagesAnyOrderConcurrent(
			servletContext,
			request,
			response,
			page,
			level,
			new PageHandler<T>() {
				// All of the edges visited or already set as a next
				final Set<PageRef> visited = new HashSet<>();
				// The already resolved parents, used for postHandler
				final List<Page> parents = new ArrayList<>();
				// The next node that is to be processed, highest on list is active
				final List<PageRef> nexts = new ArrayList<>();
				// Those that are to be done after what is next
				final List<Iterator<? extends PageReferrer>> afters = new ArrayList<>();
				// The set of nodes we've received but are not yet ready to process
				Map<PageRef,Page> received = null;

				// Kick it off
				{
					PageRef pageRef = page.getPageRef();
					visited.add(pageRef);
					nexts.add(pageRef);
					Iterator<? extends PageReferrer> empty = Collections.emptyIterator();
					afters.add(empty);
				}

				private PageRef findNext(Iterator<? extends PageReferrer> after) {
					while(after.hasNext()) {
						PageRef possNext = after.next().getPageRef();
						if(
							!visited.contains(possNext)
							&& (
								edgeFilter == null
								|| edgeFilter.applyEdge(possNext)
							)
						) {
							return possNext;
						}
					}
					return null;
				}

				@Override
				public T handlePage(Page page) throws ServletException, IOException {
					PageRef pageRef = page.getPageRef();
					// page and pageRef match, but sometimes we have a pageRef with a null page (indicating unknown)
					int index = nexts.size() - 1;
					if(DEBUG_NOW) {
						if(pageRef.equals(nextHint[0])) {
							System.err.println("Got nextHint from underlying traversal: " + pageRef);
						}
					}
					if(pageRef.equals(nexts.get(index))) {
						do {
							if(DEBUG) System.err.println("pre.: " + pageRef);
							if(preHandler != null) {
								T preResult = preHandler.handlePage(page, parents.size());
								if(preResult != null) return preResult;
							}
							// Find the first edge that we still need, if any
							Iterator<? extends PageReferrer> after = edges.getEdges(page).iterator();
							PageRef next = findNext(after);
							if(next != null) {
								if(DEBUG) System.err.println("next: " + next);
								// Have at least one child, not ready for our postHandler yet
								// Make sure we only look for a given edge once
								visited.add(next);
								// Push child
								parents.add(page);
								nexts.add(next);
								afters.add(after);
								nextHint[0] = next;
								index++;
								page = null;
								pageRef = next;
							} else {
								// No children to wait for, run postHandlers and move to next
								while(true) {
									if(DEBUG) System.err.println("post: " + pageRef);
									if(postHandler != null) {
										T postResult = postHandler.handlePage(page, parents.size());
										if(postResult != null) return postResult;
									}
									next = findNext(afters.get(index));
									if(next != null) {
										if(DEBUG) System.err.println("next: " + next);
										// Make sure we only look for a given edge once
										visited.add(next);
										nexts.set(index, next);
										nextHint[0] = next;
										page = null;
										pageRef = next;
										break;
									} else {
										// Pop parent
										afters.remove(index);
										nexts.remove(index);
										index--;
										if(index < 0) {
											// Nothing left to check, all postHandlers done
											nextHint[0] = null;
											return null;
										} else {
											page = parents.remove(index);
											pageRef = page.getPageRef();
										}
									}
								}
							}
						} while(
							page != null
							|| (
								received != null
								&& (page = received.remove(pageRef)) != null
							)
						);
						if(DEBUG_NOW) System.err.println("nextHint now: " + nextHint[0]);
					} else {
						if(received == null) received = new HashMap<>();
						received.put(pageRef, page);
						if(DEBUG_NOW) {
							System.err.println("Received " + pageRef + ", size = " + received.size() + ", next = " + nextHint[0]);
						}
					}
					return null;
				}
			},
			edges,
			edgeFilter,
			cache,
			nextHint
		);
		/* TODO
		assert result != null || parents.isEmpty();
		assert result != null || nexts.isEmpty();
		assert result != null || afters.isEmpty();
		assert result != null || received.isEmpty();
		 */
		return result;
	}

	// Make no instances
	private CapturePage() {}
}
