/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.servlet.http.Dispatcher;
import com.semanticcms.core.model.ElementContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;

/**
 * An ElementContext that is a ServletContext.
 */
public class ServletElementContext implements ElementContext {

  private final ServletContext servletContext;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  public ServletElementContext(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    this.servletContext = servletContext;
    this.request = request;
    this.response = response;
  }

  @Override
  public void include(final String resource, Writer out, final Map<String, ?> args) throws IOException, ServletException, SkipPageException {
    final PrintWriter pw;
    if (out instanceof PrintWriter) {
      pw = (PrintWriter) out;
    } else {
      pw = new PrintWriter(out);
    }
    // Clear PageContext on include
    PageContext.newPageContextSkip(
        null,
        null,
        null,
        () -> Dispatcher.include(
            servletContext,
            resource,
            request,
            new HttpServletResponseWrapper(response) {
              @Override
              public PrintWriter getWriter() {
                return pw;
              }

              @Override
              public ServletOutputStream getOutputStream() {
                throw new NotImplementedException("getOutputStream not expected");
              }
            },
            args
        )
    );
    if (pw.checkError()) {
      throw new IOException("Error on include PrintWriter");
    }
  }
}
