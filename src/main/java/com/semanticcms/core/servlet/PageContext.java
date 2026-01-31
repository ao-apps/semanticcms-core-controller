/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.lang.concurrent.CallableE;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * HttpServletRequest, HttpServletResponse, and the response PrintWriter are frequently accessed
 * while also frequently updated by elements for capture.  It is tedious to pass these objects around
 * all over.  Also, with the lambda scoping in Java 8, it is easy to use the wrong object from a
 * different scope (and possibly capturing context).
 *
 * <p>Each context is intended to be established and used within a single servlet implementation.
 * Do not assume correct management of PageContext between different pages, servlets, or files.
 * If in doubt, skip this shortcut and pass explicit object references around.</p>
 *
 * @see  PageContextWriter
 */
public final class PageContext {

  /** Make no instances, all is done through thread locals. */
  private PageContext() {
    throw new AssertionError();
  }

  static final ThreadLocal<ServletContext> servletContext = new ThreadLocal<>();

  static final ThreadLocal<HttpServletRequest> request = new ThreadLocal<>();

  static final ThreadLocal<HttpServletResponse> response = new ThreadLocal<>();

  static final ThreadLocal<PrintWriter> out = new ThreadLocal<>();

  @FunctionalInterface
  public static interface PageContextRunnable {
    void run() throws ServletException, IOException;
  }

  @FunctionalInterface
  public static interface PageContextCallable<V> extends Callable<V> {
    @Override
    V call() throws ServletException, IOException;
  }

  public static void newPageContext(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextRunnable target
  ) throws ServletException, IOException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      target.run();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  public static <V> V newPageContext(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextCallable<V> target
  ) throws ServletException, IOException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      return target.call();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  @FunctionalInterface
  public static interface PageContextRunnableSkip {
    void run() throws ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextCallableSkip<V> extends Callable<V> {
    @Override
    V call() throws ServletException, IOException, SkipPageException;
  }

  /**
   * Establishes a new page context.
   * This usually does not need to be done directly as creating a page will
   * establish the starting page context.
   */
  public static void newPageContextSkip(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextRunnableSkip target
  ) throws ServletException, IOException, SkipPageException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      target.run();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  /**
   * Establishes a new page context.
   * This usually does not need to be done directly as creating a page will
   * establish the starting page context.
   */
  public static <V> V newPageContextSkip(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextCallableSkip<V> target
  ) throws ServletException, IOException, SkipPageException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      return target.call();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @FunctionalInterface
  public static interface PageContextRunnableSkipE<Ex extends Throwable> {
    void run() throws Ex, ServletException, IOException, SkipPageException;
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @FunctionalInterface
  public static interface PageContextCallableSkipE<V, Ex extends Throwable> extends CallableE<V, Throwable> {
    @Override
    V call() throws Ex, ServletException, IOException, SkipPageException;
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  public static <Ex extends Throwable> void newPageContextSkipE(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextRunnableSkipE<Ex> target
  ) throws Ex, ServletException, IOException, SkipPageException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      target.run();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  public static <V, Ex extends Throwable> V newPageContextSkipE(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextCallableSkipE<V, Ex> target
  ) throws Ex, ServletException, IOException, SkipPageException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      return target.call();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  @FunctionalInterface
  public static interface PageContextRunnableSkipEE<Ex1 extends Throwable, Ex2 extends Throwable> {
    void run() throws Ex1, Ex2, ServletException, IOException, SkipPageException;
  }

  @FunctionalInterface
  public static interface PageContextCallableSkipEE<V, Ex1 extends Throwable, Ex2 extends Throwable> extends CallableE<V, Throwable> {
    @Override
    V call() throws Ex1, Ex2, ServletException, IOException, SkipPageException;
  }

  public static <Ex1 extends Throwable, Ex2 extends Throwable> void newPageContextSkipEE(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextRunnableSkipEE<Ex1, Ex2> target
  ) throws Ex1, Ex2, ServletException, IOException, SkipPageException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      target.run();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  public static <V, Ex1 extends Throwable, Ex2 extends Throwable> V newPageContextSkipEE(
      ServletContext newServletContext,
      HttpServletRequest newRequest,
      HttpServletResponse newResponse,
      PageContextCallableSkipEE<V, Ex1, Ex2> target
  ) throws Ex1, Ex2, ServletException, IOException, SkipPageException {
    final ServletContext oldServletContext = servletContext.get();
    final HttpServletRequest oldRequest = request.get();
    final HttpServletResponse oldResponse = response.get();
    final PrintWriter oldOut = out.get();
    PrintWriter newOut = null;
    try {
      if (newServletContext != oldServletContext) {
        servletContext.set(newServletContext);
      }
      if (newRequest != oldRequest) {
        request.set(newRequest);
      }
      if (newResponse != oldResponse) {
        response.set(newResponse);
        out.remove();
      } else {
        newOut = oldOut;
      }
      return target.call();
    } finally {
      if (newServletContext != oldServletContext) {
        if (oldServletContext == null) {
          servletContext.remove();
        } else {
          servletContext.set(oldServletContext);
        }
      }
      if (newRequest != oldRequest) {
        if (oldRequest == null) {
          request.remove();
        } else {
          request.set(oldRequest);
        }
      }
      if (newResponse != oldResponse) {
        if (oldResponse == null) {
          response.remove();
        } else {
          response.set(oldResponse);
        }
      }
      if (newOut != oldOut) {
        if (oldOut == null) {
          out.remove();
        } else {
          out.set(oldOut);
        }
      }
    }
  }

  /**
   * Gets the current servlet context.
   *
   * @throws  IllegalStateException if no context set
   */
  public static ServletContext getServletContext() throws IllegalStateException {
    ServletContext s = servletContext.get();
    if (s == null) {
      throw new IllegalStateException("No page context");
    }
    return s;
  }

  /**
   * Gets the current request.
   *
   * @throws  IllegalStateException if no context set
   */
  public static HttpServletRequest getRequest() throws IllegalStateException {
    HttpServletRequest r = request.get();
    if (r == null) {
      throw new IllegalStateException("No page context");
    }
    return r;
  }

  /**
   * Gets the current response.
   *
   * @throws  IllegalStateException if no context set
   */
  public static HttpServletResponse getResponse() throws IllegalStateException {
    HttpServletResponse r = response.get();
    if (r == null) {
      throw new IllegalStateException("No page context");
    }
    return r;
  }

  /**
   * Gets the current response writer.
   *
   * @throws  IllegalStateException if no context set
   */
  public static PrintWriter getOut() throws IllegalStateException, IOException {
    PrintWriter o = out.get();
    if (o == null) {
      o = getResponse().getWriter();
      out.set(o);
    }
    return o;
  }
}
