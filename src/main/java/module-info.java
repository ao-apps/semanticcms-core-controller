/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
 * along with semanticcms-core-controller.  If not, see <https://www.gnu.org/licenses/>.
 */
module com.semanticcms.core.controller {
  exports com.semanticcms.core.controller;
  // Direct
  requires com.aoapps.collections; // <groupId>com.aoapps</groupId><artifactId>ao-collections</artifactId>
  requires com.aoapps.concurrent; // <groupId>com.aoapps</groupId><artifactId>ao-concurrent</artifactId>
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.servlet.filter; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-filter</artifactId>
  requires com.aoapps.servlet.subrequest; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-subrequest</artifactId>
  requires com.aoapps.servlet.util; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-util</artifactId>
  requires com.aoapps.tempfiles; // <groupId>com.aoapps</groupId><artifactId>ao-tempfiles</artifactId>
  requires com.aoapps.tempfiles.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-tempfiles-servlet</artifactId>
  requires org.apache.commons.lang3; // <groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId>
  requires javax.servlet.api; // <groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId>
  requires org.joda.time; // <groupId>joda-time</groupId><artifactId>joda-time</artifactId>
  requires com.semanticcms.core.model; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-model</artifactId>
  requires com.semanticcms.core.pages; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages</artifactId>
  requires static com.semanticcms.core.pages.jsp; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages-jsp</artifactId>
  requires static com.semanticcms.core.pages.jspx; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages-jspx</artifactId>
  requires com.semanticcms.core.pages.local; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages-local</artifactId>
  requires static com.semanticcms.core.pages.servlet; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages-servlet</artifactId>
  requires static com.semanticcms.core.pages.union; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-pages-union</artifactId>
  requires com.semanticcms.core.renderer; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-renderer</artifactId>
  requires com.semanticcms.core.renderer.servlet; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-renderer-servlet</artifactId>
  requires com.semanticcms.core.resources; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-resources</artifactId>
  requires static com.semanticcms.core.resources.servlet; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-resources-servlet</artifactId>
  requires static com.semanticcms.resources.filesystem; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-resources-filesystem</artifactId>
  requires static com.semanticcms.resources.union; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-resources-union</artifactId>
  // Java SE
  requires java.logging;
  requires java.xml;
}
