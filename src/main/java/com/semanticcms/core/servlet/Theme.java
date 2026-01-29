/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2019, 2020, 2021, 2022, 2023, 2024, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.encoding.Doctype;
import com.aoapps.encoding.Serialization;
import com.aoapps.encoding.servlet.DoctypeEE;
import com.aoapps.encoding.servlet.SerializationEE;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.web.resources.registry.Registry;
import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;

/**
 * A theme is responsible for the overall view of the site.
 */
public abstract class Theme {

  /**
   * The request-scope attribute that will store the currently active theme.
   */
  private static final ScopeEE.Request.Attribute<Theme> REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(Theme.class.getName());

  /**
   * Gets the current theme on the given request or {@code null} when none active.
   */
  public static Theme getTheme(ServletRequest request) {
    return REQUEST_ATTRIBUTE.context(request).get();
  }

  /**
   * Sets the current theme on the given request or {@code null} for none active.
   */
  public static void setTheme(ServletRequest request, Theme theme) {
    REQUEST_ATTRIBUTE.context(request).set(theme);
  }

  /**
   * Two themes with the same name are considered equal.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Theme)) {
      return false;
    }
    Theme o = (Theme) obj;
    return getName().equals(o.getName());
  }

  /**
   * Consistent with equals, hashCode based on name.
   */
  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  /**
   * {@inheritDoc}
   *
   * @see  Theme#getDisplay()
   */
  @Override
  public String toString() {
    return getDisplay();
  }

  /**
   * Gets the display name for this theme.
   */
  public abstract String getDisplay();

  /**
   * Gets the unique name of this theme.
   */
  public abstract String getName();

  /**
   * Checks if this is the default theme.
   */
  public final boolean isDefault() {
    return SemanticCMS.DEFAULT_THEME_NAME.equals(getName());
  }

  /**
   * Configures the {@linkplain com.aoapps.web.resources.servlet.RegistryEE.Request request-scope web resources} that this theme uses.
   *
   * <p>Implementers should call <code>super.configureResources(…)</code> as a matter of convention, despite this default implementation doing nothing.</p>
   */
  @SuppressWarnings("NoopMethodInAbstractClass")
  public void configureResources(ServletContext servletContext, HttpServletRequest req, HttpServletResponse resp, View view, Page page, Registry requestRegistry) {
    // Do nothing
  }

  /**
   * Renders the theme.
   *
   * <p>Both the {@link Serialization} and {@link Doctype} may have been set
   * on the request, and these must be considered in the HTML generation.</p>
   *
   * <p>When {@link ServletResponse#isCommitted() response is committed}, this theme method is never called and
   * {@link SkipPageException} is thrown instead.</p>
   *
   * @see SerializationEE#get(jakarta.servlet.ServletContext, jakarta.servlet.http.HttpServletRequest)
   * @see DoctypeEE#get(jakarta.servlet.ServletContext, jakarta.servlet.ServletRequest)
   */
  public abstract void doTheme(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      View view,
      Page page
  ) throws ServletException, IOException, SkipPageException;
}
