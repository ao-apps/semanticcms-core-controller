/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2018  AO Industries, Inc.
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
package com.semanticcms.core.controller.subrequest;

import com.aoindustries.util.AoCollections;
import com.aoindustries.util.MinimalMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * {@inheritDoc}
 */
public class UnmodifiableCopyHttpServletResponse extends UnmodifiableCopyServletResponse implements HttpServletResponse {

	private final HttpServletResponse resp;

	private final Map<String,List<String>> headers;
	private final int status;

	public UnmodifiableCopyHttpServletResponse(HttpServletResponse resp) {
		super(resp);
		this.resp = resp;

		Map<String,List<String>> newHeaders = MinimalMap.emptyMap();
		for(String name : resp.getHeaderNames()) {
			newHeaders = MinimalMap.put(
				newHeaders,
				name,
				AoCollections.unmodifiableCopyList(resp.getHeaders(name))
			);
		}
		headers = MinimalMap.unmodifiable(newHeaders);
		status = resp.getStatus();
	}

	@Override
	public void addCookie(Cookie cookie) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsHeader(String name) {
		return headers.containsKey(name);
	}

	@Override
	public String encodeURL(String url) {
		synchronized(lock) {
			return resp.encodeURL(url);
		}
	}

	@Override
	public String encodeRedirectURL(String url) {
		synchronized(lock) {
			return resp.encodeRedirectURL(url);
		}
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String encodeUrl(String url) {
		synchronized(lock) {
			return resp.encodeUrl(url);
		}
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String encodeRedirectUrl(String url) {
		synchronized(lock) {
			return resp.encodeRedirectUrl(url);
		}
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendError(int sc) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setDateHeader(String name, long date) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addDateHeader(String name, long date) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setHeader(String name, String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addHeader(String name, String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setIntHeader(String name, int value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addIntHeader(String name, int value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setStatus(int sc) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public void setStatus(int sc, String sm) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public String getHeader(String name) {
		List<String> values = headers.get(name);
		return values==null ? null : values.get(0);
	}

	/**
	 * TODO: Case insensitive?
	 */
	@Override
	public Collection<String> getHeaders(String name) {
		List<String> values = headers.get(name);
		if(values == null) return new ArrayList<String>(0);
		return new ArrayList<String>(values);
	}

	@Override
	public Collection<String> getHeaderNames() {
		return new ArrayList<String>(headers.keySet());
	}
}
