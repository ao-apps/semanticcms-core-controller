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

import com.aoindustries.util.AoCollections;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Achieves thread safety by making copies of most fields during constructor and being unmodifiable.
 * This forms a base point for subrequests to diverge from.
 * <p>
 * Some methods have to read-through to the wrapped request, so it should not change
 * state while this wrapper is in use.
 * Synchronizes access to the wrapped request.
 * </p>
 */
public class UnmodifiableCopyServletRequest implements ServletRequest {

	protected static class Lock {}
	protected final Lock lock = new Lock();

	private final ServletRequest req;

	private final Map<String,Object> attributes;
	private final String characterEncoding;
	private final int contentLength;
	private final String contentType;
	private final Map<String,String[]> parameterMap;
	private final String protocol;
	private final String scheme;
	private final String serverName;
	private final int serverPort;
	private final String remoteAddr;
	private volatile String remoteHost;
	private final Locale locale;
	private final List<Locale> locales;
	private final boolean secure;
	private final int remotePort;
	private volatile String localName;
	private final String localAddr;
	private final int localPort;
	private final ServletContext servletContext;
	private final DispatcherType dispatcherType;

	public UnmodifiableCopyServletRequest(ServletRequest req) {
		this.req = req;
		attributes = ServletSubRequest.getAllAttributes(req);
		characterEncoding = req.getCharacterEncoding();
		contentLength = req.getContentLength();
		contentType = req.getContentType();
		parameterMap = new LinkedHashMap<>(req.getParameterMap());
		protocol = req.getProtocol();
		scheme = req.getScheme();
		serverName = req.getServerName();
		serverPort = req.getServerPort();
		remoteAddr = req.getRemoteAddr();
		locale = req.getLocale();
		List<Locale> newLocales = new ArrayList<>();
		Enumeration<Locale> e = req.getLocales();
		while(e.hasMoreElements()) newLocales.add(e.nextElement());
		locales = AoCollections.optimalUnmodifiableList(newLocales);
		secure = req.isSecure();
		remotePort = req.getRemotePort();
		localAddr = req.getLocalAddr();
		localPort = req.getLocalPort();
		servletContext = req.getServletContext();
		dispatcherType = req.getDispatcherType();
	}

	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		Set<String> attrNames = attributes.keySet();
		List<String> nonHiddenAttributeNames = new ArrayList<>(attrNames.size());
		for(String attrName : attrNames) {
			if(!ServletSubRequest.hiddenAttributeNames.contains(attrName)) {
				nonHiddenAttributeNames.add(attrName);
			}
		}
		return Collections.enumeration(nonHiddenAttributeNames);
	}

	@Override
	public String getCharacterEncoding() {
		return characterEncoding;
	}

	@Override
	public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getContentLength() {
		return contentLength;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	@SuppressWarnings("deprecation")
	public ServletInputStream getInputStream() throws IOException {
		throw new com.aoindustries.lang.NotImplementedException();
	}

	@Override
	public String getParameter(String name) {
		String[] values = parameterMap.get(name);
		return values==null ? null : values[0];
	}

	@Override
	public Map<String,String[]> getParameterMap() {
		return new LinkedHashMap<>(parameterMap);
	}

	@Override
	public Enumeration<String> getParameterNames() {
		return Collections.enumeration(parameterMap.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		String[] values = parameterMap.get(name);
		if(values == null) return null;
		return Arrays.copyOf(values, values.length);
	}

	@Override
	public String getProtocol() {
		return protocol;
	}

	@Override
	public String getScheme() {
		return scheme;
	}

	@Override
	public String getServerName() {
		return serverName;
	}

	@Override
	public int getServerPort() {
		return serverPort;
	}

	@Override
	@SuppressWarnings("deprecation")
	public BufferedReader getReader() throws IOException {
		throw new com.aoindustries.lang.NotImplementedException();
	}

	@Override
	public String getRemoteAddr() {
		return remoteAddr;
	}

	@Override
	public String getRemoteHost() {
		if(remoteHost == null) {
			synchronized(lock) {
				if(remoteHost == null) {
					remoteHost = req.getRemoteHost();
				}
			}
		}
		return remoteHost;
	}

	@Override
	public void setAttribute(String name, Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeAttribute(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Locale getLocale() {
		return locale;
	}

	@Override
	public Enumeration<Locale> getLocales() {
		return Collections.enumeration(locales);
	}

	@Override
	public boolean isSecure() {
		return secure;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		// TODO: Cache here?
		synchronized(lock) {
			return req.getRequestDispatcher(path);
		}
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String getRealPath(String path) {
		// TODO: Cache here?
		synchronized(lock) {
			return req.getRealPath(path);
		}
	}

	@Override
	public int getRemotePort() {
		return remotePort;
	}

	@Override
	public String getLocalName() {
		if(localName == null) {
			synchronized(lock) {
				if(localName == null) {
					localName = req.getLocalName();
				}
			}
		}
		return localName;
	}

	@Override
	public String getLocalAddr() {
		return localAddr;
	}

	@Override
	public int getLocalPort() {
		return localPort;
	}

	@Override
	public ServletContext getServletContext() {
		return servletContext;
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		throw new IllegalStateException("Not allowed on unmodifiable copy request");
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
		throw new IllegalStateException("Not allowed on unmodifiable copy request");
	}

	@Override
	public boolean isAsyncStarted() {
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		return false;
	}

	@Override
	public AsyncContext getAsyncContext() {
		throw new IllegalStateException("Not allowed on unmodifiable copy request");
	}

	@Override
	public DispatcherType getDispatcherType() {
		return dispatcherType;
	}
}
