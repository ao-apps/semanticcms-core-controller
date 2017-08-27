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
import java.io.PrintWriter;
import java.util.Locale;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;

/**
 * Synchronizes access to the wrapped response.
 */
public class ThreadSafeServletResponse extends ServletResponseWrapper {

	protected static class Lock {}
	protected final Lock lock = new Lock();

	public ThreadSafeServletResponse(ServletResponse resp) {
		super(resp);
	}

	@Override
	public ServletResponse getResponse() {
		synchronized(lock) {
			return super.getResponse();
		}
	}

	@Override
	public void setResponse(ServletResponse response) {
		synchronized(lock) {
			super.setResponse(response);
		}
	}

	@Override
	public void setCharacterEncoding(String charset) {
		synchronized(lock) {
			super.setCharacterEncoding(charset);
		}
	}

	@Override
	public String getCharacterEncoding() {
		synchronized(lock) {
			return super.getCharacterEncoding();
		}
	}

	private ThreadSafeServletOutputStream out;
	@Override
	public ThreadSafeServletOutputStream getOutputStream() throws IOException {
		synchronized(lock) {
			if(out == null) {
				out = new ThreadSafeServletOutputStream(super.getOutputStream());
			}
			return out;
		}
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		synchronized(lock) {
			// Implementation of PrintWriter looks to be thread safe, but that is not in it's documented specification
			return super.getWriter();
		}
	}

	@Override
	public void setContentLength(int len) {
		synchronized(lock) {
			super.setContentLength(len);
		}
	}

	@Override
	public void setContentType(String type) {
		synchronized(lock) {
			super.setContentType(type);
		}
	}

	@Override
	public String getContentType() {
		synchronized(lock) {
			return super.getContentType();
		}
	}

	@Override
	public void setBufferSize(int size) {
		synchronized(lock) {
			super.setBufferSize(size);
		}
	}

	@Override
	public int getBufferSize() {
		synchronized(lock) {
			return super.getBufferSize();
		}
	}

	@Override
	public void flushBuffer() throws IOException {
		synchronized(lock) {
			super.flushBuffer();
		}
	}

	@Override
	public boolean isCommitted() {
		synchronized(lock) {
			return super.isCommitted();
		}
	}

	@Override
	public void reset() {
		synchronized(lock) {
			super.reset();
		}
	}

	@Override
	public void resetBuffer() {
		synchronized(lock) {
			super.resetBuffer();
		}
	}

	@Override
	public void setLocale(Locale loc) {
		synchronized(lock) {
			super.setLocale(loc);
		}
	}

	@Override
	public Locale getLocale() {
		synchronized(lock) {
			return super.getLocale();
		}
	}

	@Override
	public boolean isWrapperFor(ServletResponse wrapped) {
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
}
