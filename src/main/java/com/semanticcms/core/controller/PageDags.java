/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019  AO Industries, Inc.
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

import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utilities for working with directed acyclic graphs (DAGs) of pages.
 */
final public class PageDags {

	public static List<Page> convertPageDagToList(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page rootPage,
		CaptureLevel level
	) throws ServletException, IOException {
		final List<Page> list = new ArrayList<>();
		final SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		CapturePage.traversePagesDepthFirst(
			servletContext,
			request,
			response,
			rootPage,
			level,
			(Page page, int depth) -> {
				list.add(page);
				return null;
			},
			Page::getChildRefs,
			// Child is in accessible book
			(PageRef childPage) -> semanticCMS.getBook(childPage.getBookRef()).isAccessible(),
			null
		);
		return Collections.unmodifiableList(list);
	}

	/**
	 * Make no instances.
	 */
	private PageDags() {
	}
}
