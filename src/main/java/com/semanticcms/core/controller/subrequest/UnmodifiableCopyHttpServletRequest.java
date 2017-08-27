/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017  AO Industries, Inc.
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

import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.util.MinimalList;
import com.aoindustries.util.MinimalMap;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

/**
 * {@inheritDoc}
 */
public class UnmodifiableCopyHttpServletRequest extends UnmodifiableCopyServletRequest implements HttpServletRequest {

	private static Cookie[] copyCookies(Cookie[] cookies) {
		if(cookies == null) return null;
		Cookie[] copy = new Cookie[cookies.length];
		for(int i=0; i<cookies.length; i++) {
			copy[i] = (Cookie)cookies[i].clone();
		}
		return copy;
	}

	private final HttpServletRequest req;

	private final String authType;
	private final Cookie[] cookies;
	private final Map<String,List<String>> headers;
	private final String method;
	private final String pathInfo;
	private final String pathTranslated;
	private final String contextPath;
	private final String queryString;
	private final String remoteUser;
	private final Principal userPrincipal;
	private final String requestedSessionId;
	private final String requestURI;
	private final String servletPath;
	private final boolean requestedSessionIdValid;
	private final boolean requestedSessionIdFromCookie;
	private final boolean requestedSessionIdFromURL;

	public UnmodifiableCopyHttpServletRequest(HttpServletRequest req) {
		super(req);
		this.req = req;

		authType = req.getAuthType();
		cookies = copyCookies(req.getCookies());
		Map<String,List<String>> newHeaders = null;
		Enumeration<String> headerNames = req.getHeaderNames();
		while(headerNames.hasMoreElements()) {
			String name = headerNames.nextElement();
			List<String> values = null;
			Enumeration<String> headerValues = req.getHeaders(name);
			while(headerValues.hasMoreElements()) {
				values = MinimalList.add(
					values,
					headerValues.nextElement()
				);
			}
			newHeaders = MinimalMap.put(
				newHeaders,
				name,
				MinimalList.unmodifiable(values)
			);
		}
		headers = MinimalMap.unmodifiable(newHeaders);
		method = req.getMethod();
		pathInfo = req.getPathInfo();
		pathTranslated = req.getPathTranslated();
		contextPath = req.getContextPath();
		queryString = req.getQueryString();
		remoteUser = req.getRemoteUser();
		userPrincipal = req.getUserPrincipal();
		requestedSessionId = req.getRequestedSessionId();
		requestURI = req.getRequestURI();
		servletPath = req.getServletPath();
		requestedSessionIdValid = req.isRequestedSessionIdValid();
		requestedSessionIdFromCookie = req.isRequestedSessionIdFromCookie();
		requestedSessionIdFromURL = req.isRequestedSessionIdFromURL();
	}

	@Override
	public String getAuthType() {
		return authType;
	}

	@Override
	public Cookie[] getCookies() {
		return copyCookies(cookies);
	}

	@Override
	public long getDateHeader(String name) {
		if(!headers.containsKey(name)) return -1;
		// TODO: cache here?
		synchronized(lock) {
			return req.getDateHeader(name);
		}
	}

	@Override
	public String getHeader(String name) {
		List<String> values = headers.get(name);
		return values==null ? null : values.get(0);
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		List<String> values = headers.get(name);
		if(values == null) values = Collections.emptyList();
		// Java 1.7: Collections.emptyEnumerator()
		return Collections.enumeration(values);
	}

	@Override
	public Enumeration<String> getHeaderNames() {
		return Collections.enumeration(headers.keySet());
	}

	@Override
	public int getIntHeader(String name) {
		String header = getHeader(name);
		return header==null ? -1 : Integer.parseInt(header);
	}

	@Override
	public String getMethod() {
		return method;
	}

	@Override
	public String getPathInfo() {
		return pathInfo;
	}

	@Override
	public String getPathTranslated() {
		return pathTranslated;
	}

	@Override
	public String getContextPath() {
		return contextPath;
	}

	@Override
	public String getQueryString() {
		return queryString;
	}

	@Override
	public String getRemoteUser() {
		return remoteUser;
	}

	@Override
	public boolean isUserInRole(String role) {
		// TODO: Cache?
		synchronized(lock) {
			return req.isUserInRole(role);
		}
	}

	@Override
	public Principal getUserPrincipal() {
		return userPrincipal;
	}

	@Override
	public String getRequestedSessionId() {
		return requestedSessionId;
	}

	@Override
	public String getRequestURI() {
		return requestURI;
	}

	@Override
	public StringBuffer getRequestURL() {
		// TODO: Cache?
		synchronized(lock) {
			return req.getRequestURL();
		}
	}

	@Override
	public String getServletPath() {
		return servletPath;
	}

	@Override
	public HttpSession getSession(boolean create) {
		// TODO: Cache?
		synchronized(lock) {
			return req.getSession(create);
		}
	}

	@Override
	public HttpSession getSession() {
		// TODO: Cache?
		synchronized(lock) {
			return req.getSession();
		}
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		return requestedSessionIdValid;
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return requestedSessionIdFromCookie;
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		return requestedSessionIdFromURL;
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public boolean isRequestedSessionIdFromUrl() {
		return requestedSessionIdFromURL;
	}

	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		throw new NotImplementedException();
	}

	@Override
	public void login(String username, String password) throws ServletException {
		throw new NotImplementedException();
	}

	@Override
	public void logout() throws ServletException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		// TODO: Cache?
		synchronized(lock) {
			Collection<Part> parts = req.getParts();
			List<Part> wrapped = new ArrayList<Part>(parts.size());
			for(Part part : parts) {
				wrapped.add(new ThreadSafePart(part, lock));
			}
			return wrapped;
		}
	}

	@Override
	public Part getPart(String name) throws IOException, ServletException {
		// TODO: Cache?
		synchronized(lock) {
			Part part = req.getPart(name);
			return part==null ? null : new ThreadSafePart(part, lock);
		}
	}
}
