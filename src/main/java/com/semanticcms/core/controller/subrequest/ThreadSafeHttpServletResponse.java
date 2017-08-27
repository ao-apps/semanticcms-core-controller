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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Synchronizes access to the wrapped response.
 */
public class ThreadSafeHttpServletResponse extends ThreadSafeServletResponse implements HttpServletResponse {

	private HttpServletResponse resp;

	public ThreadSafeHttpServletResponse(HttpServletResponse resp) {
		super(resp);
		this.resp = resp;
	}

	@Override
	public void setResponse(ServletResponse response) {
		synchronized(lock) {
			this.resp = (HttpServletResponse)response;
			super.setResponse(response);
		}
	}

	@Override
	public void addCookie(Cookie cookie) {
		synchronized(lock) {
			resp.addCookie(cookie);
		}
	}

	@Override
	public boolean containsHeader(String name) {
		synchronized(lock) {
			return resp.containsHeader(name);
		}
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
		synchronized(lock) {
			resp.sendError(sc, msg);
		}
	}

	@Override
	public void sendError(int sc) throws IOException {
		synchronized(lock) {
			resp.sendError(sc);
		}
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		synchronized(lock) {
			resp.sendRedirect(location);
		}
	}

	@Override
	public void setDateHeader(String name, long date) {
		synchronized(lock) {
			resp.setDateHeader(name, date);
		}
	}

	@Override
	public void addDateHeader(String name, long date) {
		synchronized(lock) {
			resp.addDateHeader(name, date);
		}
	}

	@Override
	public void setHeader(String name, String value) {
		synchronized(lock) {
			resp.setHeader(name, value);
		}
	}

	@Override
	public void addHeader(String name, String value) {
		synchronized(lock) {
			resp.addHeader(name, value);
		}
	}

	@Override
	public void setIntHeader(String name, int value) {
		synchronized(lock) {
			resp.setIntHeader(name, value);
		}
	}

	@Override
	public void addIntHeader(String name, int value) {
		synchronized(lock) {
			resp.addIntHeader(name, value);
		}
	}

	@Override
	public void setStatus(int sc) {
		synchronized(lock) {
			resp.setStatus(sc);
		}
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	@Override
	public void setStatus(int sc, String sm) {
		synchronized(lock) {
			resp.setStatus(sc, sm);
		}
	}

	@Override
	public int getStatus() {
		synchronized(lock) {
			return resp.getStatus();
		}
	}

	@Override
	public String getHeader(String name) {
		synchronized(lock) {
			return resp.getHeader(name);
		}
	}

	@Override
	public Collection<String> getHeaders(String name) {
		synchronized(lock) {
			return new ArrayList<String>(resp.getHeaders(name));
		}
	}

	@Override
	public Collection<String> getHeaderNames() {
		synchronized(lock) {
			return new ArrayList<String>(resp.getHeaderNames());
		}
	}
}
