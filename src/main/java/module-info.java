/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2021, 2022, 2025, 2026  AO Industries, Inc.
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
module com.semanticcms.core.servlet {
  exports com.semanticcms.core.servlet;
  exports com.semanticcms.core.servlet.impl;
  // Direct
  requires com.aoapps.collections; // <groupId>com.aoapps</groupId><artifactId>ao-collections</artifactId>
  requires com.aoapps.concurrent; // <groupId>com.aoapps</groupId><artifactId>ao-concurrent</artifactId>
  requires com.aoapps.encoding; // <groupId>com.aoapps</groupId><artifactId>ao-encoding</artifactId>
  requires com.aoapps.encoding.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-encoding-servlet</artifactId>
  requires com.aoapps.encoding.taglib; // <groupId>com.aoapps</groupId><artifactId>ao-encoding-taglib</artifactId>
  requires com.aoapps.html.any; // <groupId>com.aoapps</groupId><artifactId>ao-fluent-html-any</artifactId>
  requires com.aoapps.html.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-fluent-html-servlet</artifactId>
  requires static com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.io.buffer; // <groupId>com.aoapps</groupId><artifactId>ao-io-buffer</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.servlet.filter; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-filter</artifactId>
  requires com.aoapps.servlet.subrequest; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-subrequest</artifactId>
  requires com.aoapps.servlet.util; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-util</artifactId>
  requires com.aoapps.taglib; // <groupId>com.aoapps</groupId><artifactId>ao-taglib</artifactId>
  requires com.aoapps.tempfiles; // <groupId>com.aoapps</groupId><artifactId>ao-tempfiles</artifactId>
  requires com.aoapps.tempfiles.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-tempfiles-servlet</artifactId>
  requires com.aoapps.web.resources.registry; // <groupId>com.aoapps</groupId><artifactId>ao-web-resources-registry</artifactId>
  requires com.aoapps.web.resources.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-web-resources-servlet</artifactId>
  requires org.apache.commons.lang3; // <groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId>
  requires jakarta.el; // <groupId>jakarta.el</groupId><artifactId>jakarta.el-api</artifactId>
  requires jakarta.servlet; // <groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId>
  requires jakarta.servlet.jsp; // <groupId>jakarta.servlet.jsp</groupId><artifactId>jakarta.servlet.jsp-api</artifactId>
  requires com.semanticcms.core.model; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-model</artifactId>
  // Java SE
  requires java.logging;
  requires java.xml;
}
