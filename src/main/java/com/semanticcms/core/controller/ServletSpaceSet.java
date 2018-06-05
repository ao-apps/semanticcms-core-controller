/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2018  AO Industries, Inc.
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

import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.net.Path;

/**
 * Manages a set of servlet spaces, identifying conflicts during configuration and
 * locating them efficiently during runtime.
 *
 * See <a href="../servlet-space">Servlet Space</a>.
 *
 * TODO: Move to own semanticcms-servlet-space package? (or even more specific semanticcms-servlet-space-actions for Action?)
 * TODO: Allow configuration via /META-INF/semanticcms-servlet-space.xml files?
 *
 * TODO: Create unit tests.
 */
public class ServletSpaceSet {

	// TODO

	public ServletSpaceSet() {
	}

	public void addServletSpace(ServletSpace space) {
		throw new NotImplementedException();
	}

	public ServletSpace findServletSpace(Path servletPath) {
		throw new NotImplementedException();
	}
}
