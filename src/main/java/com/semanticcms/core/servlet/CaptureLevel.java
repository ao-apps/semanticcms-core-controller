/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.servlet.attribute.ScopeEE;
import jakarta.servlet.ServletRequest;

/**
 * The capture modes.
 */
public enum CaptureLevel {

  /**
   * Captures page meta data only, such as title, copyright, authors, parents, and children.
   */
  PAGE,

  /**
   * Captures both page and content meta data.
   */
  META,

  /**
   * Captures everything: page meta data, content meta data, and all body content.
   */
  BODY;

  private static final ScopeEE.Request.Attribute<CaptureLevel> CAPTURE_LEVEL_REQUEST_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(CaptureLevel.class.getName() + ".captureLevel");

  /**
   * Gets the capture level or <code>BODY</code> if none occurring.
   */
  public static CaptureLevel getCaptureLevel(ServletRequest request) {
    return CAPTURE_LEVEL_REQUEST_ATTRIBUTE.context(request).getOrDefault(CaptureLevel.BODY);
  }

  static void setCaptureLevel(ServletRequest request, CaptureLevel level) {
    CAPTURE_LEVEL_REQUEST_ATTRIBUTE.context(request).set(level);
  }
}
