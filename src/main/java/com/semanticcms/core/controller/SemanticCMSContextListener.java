/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2014, 2015, 2016, 2017  AO Industries, Inc.
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

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener("Exposes the application context as an application-scope SemanticCMS instance named \"" + SemanticCMS.ATTRIBUTE_NAME + "\".")
public class SemanticCMSContextListener implements ServletContextListener {

	private SemanticCMS semanticCMS;

	@Override
	public void contextInitialized(ServletContextEvent event) {
		// Kick-off the application
		semanticCMS = SemanticCMS.getInstance(event.getServletContext());
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		if(semanticCMS != null) {
			semanticCMS.destroy();
			semanticCMS = null;
		}
	}
}
