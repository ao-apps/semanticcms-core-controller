/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2019  AO Industries, Inc.
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

import com.aoindustries.tempfiles.TempFileContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * TODO: Use this on non-concurrent captures?
 *
 * {@inheritDoc}
 */
public class HttpServletSubResponseWrapper extends ServletSubResponseWrapper implements HttpServletResponse {

	private HttpServletResponse resp;

	public HttpServletSubResponseWrapper(HttpServletResponse resp, TempFileContext tempFileContext) {
		super(resp, tempFileContext);
		this.resp = resp;
	}

	@Override
	public void setResponse(ServletResponse response) {
		this.resp = (HttpServletResponse)response;
		super.setResponse(response);
	}

	@Override
	public void setContentLength(int len) {
		setIntHeader("Content-Length", len);
	}

	/**
	 * The cookies added by this response.
	 */
	private Map<String,Cookie> cookies;

	@Override
	public void addCookie(Cookie cookie) {
		if(cookies == null) cookies = new LinkedHashMap<>();
		cookies.put(cookie.getName(), cookie);
	}

	/**
	 * The headers added by this response, these include headers from the parent for each key in this map.
	 */
	private Map<String,List<String>> headers;

	@Override
	public boolean containsHeader(String name) {
		return
			(headers != null && headers.containsKey(name))
			|| resp.containsHeader(name)
		;
	}

	@Override
	public String encodeURL(String url) {
		return resp.encodeURL(url);
	}

	@Override
	public String encodeRedirectURL(String url) {
		return resp.encodeRedirectURL(url);
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String encodeUrl(String url) {
		return resp.encodeUrl(url);
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String encodeRedirectUrl(String url) {
		return resp.encodeRedirectUrl(url);
	}

	private int statusCode = Integer.MIN_VALUE; // Read-through when MIN_VALUE
	private String statusMessage;

	@Override
	public void sendError(int sc, String msg) throws IOException {
		if(committed) throw new IllegalStateException("Concurrent response already committed");
		this.statusCode = sc;
		this.statusMessage = msg;
		this.committed = true;
	}

	@Override
	public void sendError(int sc) throws IOException {
		sendError(sc, null);
	}

	private String redirectLocation;

	@Override
	public void sendRedirect(String location) throws IOException {
		if(committed) throw new IllegalStateException("Concurrent response already committed");
		this.redirectLocation = location;
		this.committed = true;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void setDateHeader(String name, long date) {
		throw new com.aoindustries.lang.NotImplementedException();
	}

	@Override
	@SuppressWarnings("deprecation")
	public void addDateHeader(String name, long date) {
		throw new com.aoindustries.lang.NotImplementedException();
	}

	@Override
	public void setHeader(String name, String value) {
		if(headers == null) headers = new LinkedHashMap<>();
		headers.put(name, Collections.singletonList(value));
	}

	@Override
	public void addHeader(String name, String value) {
		List<String> values;
		if(headers == null) {
			headers = new LinkedHashMap<>();
			values = null;
		} else {
			values = headers.get(name);
		}
		if(values == null) {
			Collection<String> existing = resp.getHeaders(name);
			if(existing.isEmpty()) {
				headers.put(name, Collections.singletonList(value));
			} else {
				List<String> newValues = new ArrayList<>();
				newValues.addAll(existing);
				newValues.add(value);
				headers.put(name, newValues);
			}
		} else if(values.size() == 1) {
			List<String> newValues = new ArrayList<>();
			newValues.addAll(values);
			newValues.add(value);
			headers.put(name, newValues);
		} else {
			values.add(value);
		}
	}

	@Override
	public void setIntHeader(String name, int value) {
		setHeader(name, Integer.toString(value));
	}

	@Override
	public void addIntHeader(String name, int value) {
		addHeader(name, Integer.toString(value));
	}

	@Override
	public void setStatus(int sc) {
		// Docs make no mention of committed status, so we'll ignore on committed response as seems to behave
		if(!committed) {
			this.statusCode = sc;
		}
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public void setStatus(int sc, String sm) {
		// Docs make no mention of committed status, so we'll ignore on committed response as seems to behave
		if(!committed) {
			this.statusCode = sc;
			this.statusMessage = sm;
		}
	}

	@Override
	public int getStatus() {
		return statusCode != Integer.MIN_VALUE ? statusCode : resp.getStatus();
	}

	/**
	 * TODO: Case insensitive?
	 */
	@Override
	public String getHeader(String name) {
		if(headers != null) {
			List<String> values = headers.get(name);
			if(values != null) {
				assert !values.isEmpty();
				return values.get(0);
			}
		}
		return resp.getHeader(name);
	}

	/**
	 * TODO: Case insensitive?
	 */
	@Override
	public Collection<String> getHeaders(String name) {
		if(headers != null) {
			List<String> values = headers.get(name);
			if(values != null) {
				return new ArrayList<>(values);
			}
		}
		return resp.getHeaders(name);
	}

	/**
	 * TODO: Case insensitive?
	 */
	@Override
	public Collection<String> getHeaderNames() {
		Collection<String> existingHeaderNames = resp.getHeaderNames();
		if(headers != null) {
			if(existingHeaderNames.isEmpty()) {
				return new ArrayList<>(headers.keySet());
			} else {
				// Combine all header names
				Set<String> headerNames = new LinkedHashSet<>(
					(existingHeaderNames.size() + headers.size()) *4/3+1
				);
				headerNames.addAll(existingHeaderNames);
				headerNames.addAll(headers.keySet());
				return headerNames;
			}
		} else {
			return existingHeaderNames;
		}
	}
}
