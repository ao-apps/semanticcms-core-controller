/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2019, 2020, 2021, 2022, 2026  AO Industries, Inc.
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

import com.aoapps.concurrent.Executor;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.servlet.filter.CountConcurrencyListener;
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

  private static final ScopeEE.Request.Attribute<Boolean> CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(ConcurrencyCoordinator.class.getName() + ".concurrentProcessingRecommended");
  private static final ScopeEE.Request.Attribute<Boolean> CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(ConcurrencyCoordinator.class.getName() + ".concurrentSubrequestsRecommended");

  private boolean concurrentSubrequests;
  private int preferredConcurrency;

  @Override
  public void contextInitialized(ServletContextEvent event) {
    SemanticCMS semanticCms = SemanticCMS.getInstance(event.getServletContext());
    concurrentSubrequests = semanticCms.getConcurrentSubrequests();
    preferredConcurrency = semanticCms.getExecutors().getPreferredConcurrency();
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    // Do nothing
  }

  @Override
  public void attributeAdded(ServletRequestAttributeEvent event) {
    if (CountConcurrencyListener.REQUEST_ATTRIBUTE.getName().equals(event.getName())) {
      ServletRequest request = event.getServletRequest();
      int newConcurrency = (Integer) event.getValue();

      assert CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).get() == null;
      assert CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).get() == null;

      // On single-CPU systems, preferredConcurrency is 1 and concurrency will never be done
      boolean concurrentProcessingRecommended = newConcurrency < preferredConcurrency;
      boolean concurrentSubrequestsRecommended = concurrentProcessingRecommended && concurrentSubrequests;

      CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).set(concurrentProcessingRecommended);
      CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).set(concurrentSubrequestsRecommended);
    }
  }

  @Override
  public void attributeRemoved(ServletRequestAttributeEvent event) {
    if (CountConcurrencyListener.REQUEST_ATTRIBUTE.getName().equals(event.getName())) {
      ServletRequest request = event.getServletRequest();
      CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).remove();
      CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).remove();
    }
  }

  @Override
  public void attributeReplaced(ServletRequestAttributeEvent event) {
    if (CountConcurrencyListener.REQUEST_ATTRIBUTE.getName().equals(event.getName())) {
      throw new IllegalStateException(
          "The attribute is only expected to e added or removed, never replaced: "
              + CountConcurrencyListener.REQUEST_ATTRIBUTE.getName()
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
    Boolean concurrentProcessingRecommended = CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).get();
    if (concurrentProcessingRecommended == null) {
      throw new IllegalStateException(ConcurrencyCoordinator.class.getName() + " listener not active on request");
    }
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
    Boolean concurrentSubrequestsRecommended = CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE.context(request).get();
    if (concurrentSubrequestsRecommended == null) {
      throw new IllegalStateException(ConcurrencyCoordinator.class.getName() + " listener not active on request");
    }
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
