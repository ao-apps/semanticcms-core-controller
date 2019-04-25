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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;

/**
 * Synchronizes access to the wrapped request.
 */
public class ThreadSafeServletRequest extends ServletRequestWrapper {

	protected static class Lock {}
	protected final Lock lock = new Lock();

	public ThreadSafeServletRequest(ServletRequest req) {
		super(req);
	}

	@Override
	public ServletRequest getRequest() {
		synchronized(lock) {
			return super.getRequest();
		}
	}

	@Override
	public void setRequest(ServletRequest request) {
		synchronized(lock) {
			super.setRequest(request);
		}
	}

	@Override
	public Object getAttribute(String name) {
		synchronized(lock) {
			return super.getAttribute(name);
		}
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		List<String> attributeNames = new ArrayList<>();
		synchronized(lock) {
			Enumeration<String> e = super.getAttributeNames();
			while(e.hasMoreElements()) attributeNames.add(e.nextElement());
		}
		return Collections.enumeration(attributeNames);
	}

	@Override
	public String getCharacterEncoding() {
		synchronized(lock) {
			return super.getCharacterEncoding();
		}
	}

	@Override
	public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
		synchronized(lock) {
			super.setCharacterEncoding(enc);
		}
	}

	@Override
	public int getContentLength() {
		synchronized(lock) {
			return super.getContentLength();
		}
	}

	@Override
	public String getContentType() {
		synchronized(lock) {
			return super.getContentType();
		}
	}

	private ThreadSafeServletInputStream in;
	@Override
	public ThreadSafeServletInputStream getInputStream() throws IOException {
		synchronized(lock) {
			if(in == null) {
				in = new ThreadSafeServletInputStream(super.getInputStream());
			}
			return in;
		}
	}

	@Override
	public String getParameter(String name) {
		synchronized(lock) {
			return super.getParameter(name);
		}
	}

	@Override
	public Map<String,String[]> getParameterMap() {
		synchronized(lock) {
			return Collections.synchronizedMap(super.getParameterMap());
		}
	}

	@Override
	public Enumeration<String> getParameterNames() {
		synchronized(lock) {
			return super.getParameterNames();
		}
	}

	@Override
	public String[] getParameterValues(String name) {
		synchronized(lock) {
			return super.getParameterValues(name);
		}
	}

	@Override
	public String getProtocol() {
		synchronized(lock) {
			return super.getProtocol();
		}
	}

	@Override
	public String getScheme() {
		synchronized(lock) {
			return super.getScheme();
		}
	}

	@Override
	public String getServerName() {
		synchronized(lock) {
			return super.getServerName();
		}
	}

	@Override
	public int getServerPort() {
		synchronized(lock) {
			return super.getServerPort();
		}
	}

	@Override
	public BufferedReader getReader() throws IOException {
		synchronized(lock) {
			// Implementation of BufferedReader looks to be thread safe, but that is not in it's documented specification
			return super.getReader();
		}
	}

	@Override
	public String getRemoteAddr() {
		synchronized(lock) {
			return super.getRemoteAddr();
		}
	}

	@Override
	public String getRemoteHost() {
		synchronized(lock) {
			return super.getRemoteHost();
		}
	}

	@Override
	public void setAttribute(String name, Object o) {
		synchronized(lock) {
			super.setAttribute(name, o);
		}
	}

	@Override
	public void removeAttribute(String name) {
		synchronized(lock) {
			super.removeAttribute(name);
		}
	}

	@Override
	public Locale getLocale() {
		synchronized(lock) {
			return super.getLocale();
		}
	}

	@Override
	public Enumeration<Locale> getLocales() {
		synchronized(lock) {
			return super.getLocales();
		}
	}

	@Override
	public boolean isSecure() {
		synchronized(lock) {
			return super.isSecure();
		}
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		synchronized(lock) {
			return super.getRequestDispatcher(path);
		}
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String getRealPath(String path) {
		synchronized(lock) {
			return super.getRealPath(path);
		}
	}

	@Override
	public int getRemotePort() {
		synchronized(lock) {
			return super.getRemotePort();
		}
	}

	@Override
	public String getLocalName() {
		synchronized(lock) {
			return super.getLocalName();
		}
	}

	@Override
	public String getLocalAddr() {
		synchronized(lock) {
			return super.getLocalAddr();
		}
	}

	@Override
	public int getLocalPort() {
		synchronized(lock) {
			return super.getLocalPort();
		}
	}

	@Override
	public ServletContext getServletContext() {
		synchronized(lock) {
			return super.getServletContext();
		}
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		synchronized(lock) {
			return super.startAsync();
		}
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
		synchronized(lock) {
			return super.startAsync(servletRequest, servletResponse);
		}
	}

	@Override
	public boolean isAsyncStarted() {
		synchronized(lock) {
			return super.isAsyncStarted();
		}
	}

	@Override
	public boolean isAsyncSupported() {
		synchronized(lock) {
			return super.isAsyncSupported();
		}
	}

	@Override
	public AsyncContext getAsyncContext() {
		synchronized(lock) {
			return super.getAsyncContext();
		}
	}

	@Override
	public boolean isWrapperFor(ServletRequest wrapped) {
		synchronized(lock) {
			return super.isWrapperFor(wrapped);
		}
	}

	@Override
	@SuppressWarnings("rawtypes")
	public boolean isWrapperFor(Class wrappedType) {
		synchronized(lock) {
			return super.isWrapperFor(wrappedType);
		}
	}

	@Override
	public DispatcherType getDispatcherType() {
		synchronized(lock) {
			return super.getDispatcherType();
		}
	}
}
