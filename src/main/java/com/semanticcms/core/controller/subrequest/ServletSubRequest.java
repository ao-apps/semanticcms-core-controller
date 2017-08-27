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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
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
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;

/**
 * <p>
 * <b>This does not implement {@link ServletRequestWrapper} and use of it is in violation
 * of the specification.</b>  When used in conjunction with new threads (or threads
 * from your own pool), Tomcat 7.0 and 8.5 do not notice you switched the request due to its
 * use of ThreadLocal to enforce the spec.  This is very hackish and fragile - use at
 * your own risk.
 * </p>
 * <p>
 * Wraps a servlet request with the intent to operate as a concurrent sub request.
 * Any changes made to the request will only affect this request and will not be passed
 * along to the wrapped request.
 * </p>
 * <p>
 * It is expected that the wrapped request will not change for the life of this wrapper.
 * If it does change, the changes may or may not be visible depending on what has been
 * accessed and changed on this request.
 * </p>
 * <p>
 * This class is not thread safe.
 * </p>
 */
public class ServletSubRequest implements ServletRequest {

	private static final boolean DEBUG = false;

	private final ServletRequest req;

	public ServletSubRequest(ServletRequest req) {
		this.req = req;
	}

	/**
	 * These attributes are hidden.  They exist when queried on request
	 * but are not returned as part of all attribute names. (At least
	 * in Tomcat 7.0 - is this a bug?)
	 */
	static final Set<String> hiddenAttributeNames = Collections.unmodifiableSet(
		new HashSet<String>(
			Arrays.asList(
				//"org.apache.catalina.core.DISPATCHER_TYPE",
				//"org.apache.catalina.core.DISPATCHER_REQUEST_PATH",
				//"org.apache.catalina.jsp_file",
				RequestDispatcher.INCLUDE_REQUEST_URI,
				RequestDispatcher.INCLUDE_CONTEXT_PATH,
				RequestDispatcher.INCLUDE_SERVLET_PATH,
				RequestDispatcher.INCLUDE_PATH_INFO,
				RequestDispatcher.INCLUDE_QUERY_STRING,
				RequestDispatcher.FORWARD_REQUEST_URI, 
				RequestDispatcher.FORWARD_CONTEXT_PATH,
				RequestDispatcher.FORWARD_SERVLET_PATH, 
				RequestDispatcher.FORWARD_PATH_INFO,
				RequestDispatcher.FORWARD_QUERY_STRING
			)
		)
	);

	static Map<String,Object> getAllAttributes(ServletRequest req) {
		Map<String,Object> newAttributes = new LinkedHashMap<String,Object>();
		for(String hiddenAttrName : hiddenAttributeNames) {
			if(DEBUG) System.out.println("DEBUG: setAttribute: hiddenAttrName: " + hiddenAttrName);
			Object hiddenAttrVal = req.getAttribute(hiddenAttrName);
			if(DEBUG) System.out.println("DEBUG: setAttribute: hiddenAttrVal: " + hiddenAttrVal);
			if(hiddenAttrVal != null) {
				newAttributes.put(hiddenAttrName, hiddenAttrVal);
			}
		}
		Enumeration<String> attrNames = req.getAttributeNames();
		while(attrNames.hasMoreElements()) {
			String attrName = attrNames.nextElement();
			if(DEBUG) System.out.println("DEBUG: setAttribute: attrName: " + attrName);
			Object attrVal = req.getAttribute(attrName);
			if(DEBUG) System.out.println("DEBUG: setAttribute: attrVal: " + attrVal);
			// Check for null in case attribute was removed during iteration
			if(attrVal != null) {
				newAttributes.put(attrName, attrVal);
			}
		}
		return newAttributes;
	}

	private Map<String,Object> attributes;

	@Override
	public Object getAttribute(String name) {
		if(DEBUG) System.out.println("DEBUG: getAttribute: " + name);
		Map<String,Object> a = attributes;
		if(
			a != null
			//&& !hiddenAttributeNames.contains(name)
		) {
			return a.get(name);
		} else {
			return req.getAttribute(name);
		}
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		if(DEBUG) System.out.println("DEBUG: getAttributeNames");
		Map<String,Object> a = attributes;
		if(a != null) {
			Set<String> attrNames = a.keySet();
			List<String> nonHiddenAttributeNames = new ArrayList<String>(attrNames.size());
			for(String attrName : attrNames) {
				if(!hiddenAttributeNames.contains(attrName)) {
					nonHiddenAttributeNames.add(attrName);
				}
			}
			return Collections.enumeration(nonHiddenAttributeNames);
		} else {
			return req.getAttributeNames();
		}
	}

	@Override
	public void setAttribute(String name, Object o) {
		if(DEBUG) {
			try {
				System.out.println("DEBUG: setAttribute: " + name + ", " + o);
			} catch(IllegalStateException e) {
				// Object not ready for toString
			}
		}
		if(attributes == null) attributes = getAllAttributes(req);
		if(o == null) {
			attributes.remove(name);
		} else {
			attributes.put(name, o);
		}
	}

	@Override
	public void removeAttribute(String name) {
		setAttribute(name, null);
	}

	private boolean characterEncodingSet;
	private String characterEncoding;
	@Override
	public String getCharacterEncoding() {
		if(characterEncodingSet) {
			return characterEncoding;
		} else {
			return req.getCharacterEncoding();
		}
	}

	@Override
	public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
		characterEncoding = enc;
		characterEncodingSet = true;
		// Not checking to throw UnsupportedEncodingException here, assuming no longer in a context where character encoding may be set
	}

	@Override
	public int getContentLength() {
		return  req.getContentLength();
	}

	@Override
	public String getContentType() {
		return req.getContentType();
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		throw new IllegalStateException("Not allowed on concurrent request");
	}

	@Override
	public String getParameter(String name) {
		return req.getParameter(name);
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		return req.getParameterMap();
	}

	@Override
	public Enumeration<String> getParameterNames() {
		return req.getParameterNames();
	}

	@Override
	public String[] getParameterValues(String name) {
		return req.getParameterValues(name);
	}

	@Override
	public String getProtocol() {
		return req.getProtocol();
	}

	@Override
	public String getScheme() {
		return req.getScheme();
	}

	@Override
	public String getServerName() {
		return req.getServerName();
	}

	@Override
	public int getServerPort() {
		return req.getServerPort();
	}

	@Override
	public BufferedReader getReader() throws IOException {
		throw new IllegalStateException("Not allowed on concurrent request");
	}

	@Override
	public String getRemoteAddr() {
		return req.getRemoteAddr();
	}

	@Override
	public String getRemoteHost() {
		return req.getRemoteHost();
	}

	@Override
	public Locale getLocale() {
		return req.getLocale();
	}

	@Override
	public Enumeration<Locale> getLocales() {
		return req.getLocales();
	}

	@Override
	public boolean isSecure() {
		return req.isSecure();
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		return req.getRequestDispatcher(path);
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public String getRealPath(String path) {
		return req.getRealPath(path);
	}

	@Override
	public int getRemotePort() {
		return req.getRemotePort();
	}

	@Override
	public String getLocalName() {
		return req.getLocalName();
	}

	@Override
	public String getLocalAddr() {
		return req.getLocalAddr();
	}

	@Override
	public int getLocalPort() {
		return req.getLocalPort();
	}

	@Override
	public ServletContext getServletContext() {
		return req.getServletContext();
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		throw new IllegalStateException("Not allowed on concurrent request");
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
		throw new IllegalStateException("Not allowed on concurrent request");
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
		throw new IllegalStateException("Not allowed on concurrent request");
	}

	@Override
	public DispatcherType getDispatcherType() {
		return req.getDispatcherType();
	}
}
