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

import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.taglib.AutoEncodingBufferedTag;
import com.aoindustries.tempfiles.TempFileContext;
import com.aoindustries.util.WrappedException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;

/**
 * <p>
 * Wraps a servlet response with the intent to operate as a concurrent sub response.
 * Any changes made to the response will only affect this response and will not be passed
 * along to the wrapped response.
 * </p>
 * <p>
 * The wrapped response may change state while this sub response is being processed.
 * Any changes to the wrapped response will not affect this concurrent sub response.
 * </p>
 * <p>
 * This class is not thread safe.
 * </p>
 */
public class ServletSubResponseWrapper extends ServletResponseWrapper {

	private final TempFileContext tempFileContext;
	private String characterEncoding;
	private String contentType;
	private Locale locale;

	/**
	 * @param req  The request context that contains the temp file list for auto temp files;
	 *             getAttribute and setAttribute must write through to the original request
	 *             for proper temp file cleanup.
	 */
	public ServletSubResponseWrapper(ServletResponse resp, TempFileContext tempFileContext) {
		super(resp);
		this.tempFileContext = tempFileContext;
		characterEncoding = resp.getCharacterEncoding();
		contentType = resp.getContentType();
		locale = resp.getLocale();
	}

	@Override
	public void setCharacterEncoding(String charset) {
		// TODO: interact with contentType
		this.characterEncoding = charset;
	}

	@Override
	public String getCharacterEncoding() {
		return characterEncoding;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		throw new NotImplementedException();
	}

	private BufferWriter capturedOut;
	private PrintWriter capturedPW;
	@Override
	public PrintWriter getWriter() throws IOException {
		if(capturedOut == null) {
			capturedOut = AutoEncodingBufferedTag.newBufferWriter(tempFileContext);
		}
		if(capturedPW == null) {
			capturedPW = new PrintWriter(capturedOut);
		}
		return capturedPW;
	}

	@Override
	public void setContentLength(int len) {
		// Nothing to do
	}

	@Override
	public void setContentType(String type) {
		// TODO: interact with character set
		this.contentType = type;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public void setBufferSize(int size) {
		// Nothing to do
	}

	@Override
	public int getBufferSize() {
		return Integer.MAX_VALUE;
	}

	protected boolean committed;

	@Override
	public void flushBuffer() throws IOException {
		if(capturedPW != null) capturedPW.flush();
		committed = true;
	}

	@Override
	public boolean isCommitted() {
		return committed;
	}

	@Override
	public void reset() {
		resetBuffer();
		// TODO: Reset status code
		// TODO: Reset headers
	}

	@Override
	public void resetBuffer() {
		if(committed) throw new IllegalStateException("Concurrent response already committed");
		if(capturedPW != null) {
			capturedPW.close();
			capturedPW = null;
		}
		if(capturedOut != null) {
			try {
				capturedOut.close();
				capturedOut = null;
			} catch(IOException e) {
				throw new WrappedException(e);
			}
		}
	}

	@Override
	public void setLocale(Locale loc) {
		if(!committed) {
			locale = loc;
			// TODO: The spec has a bunch of other stuff, like locale affecting default character encoding
		}
	}

	@Override
	public Locale getLocale() {
		return locale;
	}
}
