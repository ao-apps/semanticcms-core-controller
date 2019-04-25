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

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

/**
 * {@inheritDoc}
 */
public class HttpServletSubRequest extends ServletSubRequest implements HttpServletRequest {

	private final HttpServletRequest req;

	private boolean loggedOut;

	public HttpServletSubRequest(HttpServletRequest req) {
		super(req);
		this.req = req;
	}

	@Override
	public String getAuthType() {
		return loggedOut ? null : req.getAuthType();
	}

	@Override
	public Cookie[] getCookies() {
		return req.getCookies();
	}

	@Override
	public long getDateHeader(String name) {
		return req.getDateHeader(name);
	}

	@Override
	public String getHeader(String name) {
		return req.getHeader(name);
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		return req.getHeaders(name);
	}

	@Override
	public Enumeration<String> getHeaderNames() {
		return req.getHeaderNames();
	}

	@Override
	public int getIntHeader(String name) {
		return req.getIntHeader(name);
	}

	@Override
	public String getMethod() {
		return req.getMethod();
	}

	@Override
	public String getPathInfo() {
		return req.getPathInfo();
	}

	@Override
	public String getPathTranslated() {
		return req.getPathTranslated();
	}

	@Override
	public String getContextPath() {
		return req.getContextPath();
	}

	@Override
	public String getQueryString() {
		return req.getQueryString();
	}

	@Override
	public String getRemoteUser() {
		return loggedOut ? null : req.getRemoteUser();
	}

	@Override
	public boolean isUserInRole(String role) {
		return !loggedOut && req.isUserInRole(role);
	}

	@Override
	public Principal getUserPrincipal() {
		return loggedOut ? null : req.getUserPrincipal();
	}

	@Override
	public String getRequestedSessionId() {
		return req.getRequestedSessionId();
	}

	@Override
	public String getRequestURI() {
		return req.getRequestURI();
	}

	@Override
	public StringBuffer getRequestURL() {
		return req.getRequestURL();
	}

	@Override
	public String getServletPath() {
		return req.getServletPath();
	}

	@Override
	public HttpSession getSession(boolean create) {
		return req.getSession(create);
	}

	@Override
	public HttpSession getSession() {
		return req.getSession();
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		return req.isRequestedSessionIdValid();
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return req.isRequestedSessionIdFromCookie();
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		return req.isRequestedSessionIdFromURL();
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public boolean isRequestedSessionIdFromUrl() {
		return req.isRequestedSessionIdFromUrl();
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		throw new com.aoindustries.lang.NotImplementedException();
	}

	@Override
	@SuppressWarnings("deprecation")
	public void login(String username, String password) throws ServletException {
		throw new com.aoindustries.lang.NotImplementedException();
	}

	@Override
	public void logout() throws ServletException {
		loggedOut = true;
	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		return req.getParts();
	}

	@Override
	public Part getPart(String name) throws IOException, ServletException {
		return req.getPart(name);
	}
}
