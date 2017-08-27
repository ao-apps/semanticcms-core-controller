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
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;

/**
 * Achieves thread safety by making copies of most fields during constructor and being unmodifiable.
 * This forms a base point for subrequests to diverge from.
 * <p>
 * Some methods have to read-through to the wrapped response, so it should not change
 * state while this wrapper is in use.
 * Synchronizes access to the wrapped response.
 * </p>
 */
public class UnmodifiableCopyServletResponse implements ServletResponse {

	protected static class Lock {}
	protected final Lock lock = new Lock();

	private final ServletResponse resp;

	private final String characterEncoding;
	private final String contentType;
	private final int bufferSize;
	private final boolean committed;
	private final Locale locale;

	public UnmodifiableCopyServletResponse(ServletResponse resp) {
		this.resp = resp;
		characterEncoding = resp.getCharacterEncoding();
		contentType = resp.getContentType();
		bufferSize = resp.getBufferSize();
		committed = resp.isCommitted();
		locale = resp.getLocale();
	}

	@Override
	public void setCharacterEncoding(String charset) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getCharacterEncoding() {
		return characterEncoding;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentLength(int len) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentType(String type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public void setBufferSize(int size) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBufferSize() {
		return bufferSize;
	}

	@Override
	public void flushBuffer() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isCommitted() {
		return committed;
	}

	@Override
	public void reset() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void resetBuffer() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLocale(Locale loc) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Locale getLocale() {
		return locale;
	}
}
