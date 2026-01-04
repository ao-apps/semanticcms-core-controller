/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.html.servlet.DocumentEE;
import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Utilities for working with components.
 */
public final class ComponentUtils {

  /** Make no instances. */
  private ComponentUtils() {
    throw new AssertionError();
  }

  public static void doComponents(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      DocumentEE document,
      View view,
      Page page,
      ComponentPosition position,
      boolean reverse
  ) throws ServletException, IOException {
    List<Component> components = SemanticCMS.getInstance(servletContext).getComponents();
    if (reverse) {
      for (int i = components.size() - 1; i >= 0; i--) {
        components.get(i).doComponent(
            servletContext,
            request,
            response,
            document,
            view,
            page,
            position
        );
      }
    } else {
      for (Component component : components) {
        component.doComponent(
            servletContext,
            request,
            response,
            document,
            view,
            page,
            position
        );
      }
    }
  }
}
