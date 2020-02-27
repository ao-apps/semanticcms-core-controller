/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2019, 2020  AO Industries, Inc.
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

import com.aoindustries.servlet.filter.CountConcurrencyListener;
import com.aoindustries.util.concurrent.Executor;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.annotation.WebListener;

/**
 * Determines if concurrent processing is recommended for the current request.
 *
 * @see  CountConcurrencyListener
 */
@WebListener("Decides whether to use concurrent or sequential implementations.")
public class ConcurrencyCoordinator implements ServletContextListener, ServletRequestAttributeListener {

	private static final String CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE = ConcurrencyCoordinator.class.getName() + ".concurrentProcessingRecommended";
	private static final String CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE = ConcurrencyCoordinator.class.getName() + ".concurrentSubrequestsRecommended";

	private boolean concurrentSubrequests;
	private int preferredConcurrency;

	@Override
	public void contextInitialized(ServletContextEvent event) {
		SemanticCMS semanticCMS = SemanticCMS.getInstance(event.getServletContext());
		concurrentSubrequests = semanticCMS.getConcurrentSubrequests();
		preferredConcurrency = semanticCMS.getExecutors().getPreferredConcurrency();
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		// Do nothing
	}

	@Override
	public void attributeAdded(ServletRequestAttributeEvent event) {
		if(CountConcurrencyListener.REQUEST_ATTRIBUTE.equals(event.getName())) {
			ServletRequest request = event.getServletRequest();
			int newConcurrency = (Integer)event.getValue();

			assert request.getAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE) == null;
			assert request.getAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE) == null;

			// One single-CPU system, preferredConcurrency is 1 and concurrency will never be done
			boolean concurrentProcessingRecommended = (newConcurrency < preferredConcurrency);
			boolean concurrentSubrequestsRecommended = concurrentProcessingRecommended && concurrentSubrequests;

			request.setAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE, concurrentProcessingRecommended);
			request.setAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE, concurrentSubrequestsRecommended);
		}
	}

	@Override
	public void attributeRemoved(ServletRequestAttributeEvent event) {
		if(CountConcurrencyListener.REQUEST_ATTRIBUTE.equals(event.getName())) {
			ServletRequest request = event.getServletRequest();
			request.removeAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE);
			request.removeAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE);
		}
	}

	@Override
	public void attributeReplaced(ServletRequestAttributeEvent event) {
		if(CountConcurrencyListener.REQUEST_ATTRIBUTE.equals(event.getName())) {
			throw new IllegalStateException(
				"The attribute is only expected to e added or removed, never replaced: "
				+ CountConcurrencyListener.REQUEST_ATTRIBUTE
			);
		}
	}

	/**
	 * Checks if concurrent processing is recommended.
	 * Recommended when the overall request concurrency is less than the preferred concurrency.
	 * This value will remain consistent throughout the processing of a request.
	 * 
	 * @see  Executors#getPreferredConcurrency()
	 */
	public static boolean isConcurrentProcessingRecommended(ServletRequest request) {
		Boolean concurrentProcessingRecommended = (Boolean)request.getAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE);
		if(concurrentProcessingRecommended == null) throw new IllegalStateException(ConcurrencyCoordinator.class.getName() + " listener not active on request");
		return concurrentProcessingRecommended;
	}

	/**
	 * Determines if concurrent subrequests are currently allowed and advised for the given request.
	 * <ol>
	 * <li>Concurrent subrequests must be enabled: {@link SemanticCMS#getConcurrentSubrequests()}</li>
	 * <li>Request concurrency must be less than the executor per-processor thread limit: {@link #isConcurrentProcessingRecommended(javax.servlet.ServletRequest)}</li>
	 * </ol>
	 */
	public static boolean useConcurrentSubrequests(ServletRequest request) {
		Boolean concurrentSubrequestsRecommended = (Boolean)request.getAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE);
		if(concurrentSubrequestsRecommended == null) throw new IllegalStateException(ConcurrencyCoordinator.class.getName() + " listener not active on request");
		return concurrentSubrequestsRecommended;
	}

	/**
	 * Gets the executor to use for per-processor tasks.
	 * If {@link #isConcurrentProcessingRecommended(javax.servlet.ServletRequest)}, is {@link Executors#getPerProcessor()},
	 * otherwise is {@link Executors#getSequential()}
	 */
	public static Executor getRecommendedExecutor(ServletContext servletContext, ServletRequest request) {
		Executors executors = SemanticCMS.getInstance(servletContext).getExecutors();
		return
			isConcurrentProcessingRecommended(request)
			? executors.getPerProcessor()
			: executors.getSequential();
	}
}
