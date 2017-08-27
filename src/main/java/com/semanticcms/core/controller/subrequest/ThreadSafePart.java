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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import javax.servlet.http.Part;

/**
 * Synchronizes access to the wrapped part.
 */
public class ThreadSafePart implements Part {

	protected final Part part;
	protected final Object lock;

	public ThreadSafePart(Part part, Object lock) {
		this.part = part;
		this.lock = lock;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		synchronized(lock) {
			return part.getInputStream();
		}
	}

	@Override
	public String getContentType() {
		synchronized(lock) {
			return part.getContentType();
		}
	}

	@Override
	public String getName() {
		synchronized(lock) {
			return part.getName();
		}
	}

	@Override
	public long getSize() {
		synchronized(lock) {
			return part.getSize();
		}
	}

	@Override
	public void write(String fileName) throws IOException {
		synchronized(lock) {
			part.write(fileName);
		}
	}

	@Override
	public void delete() throws IOException {
		synchronized(lock) {
			part.delete();
		}
	}

	@Override
	public String getHeader(String name) {
		synchronized(lock) {
			return part.getHeader(name);
		}
	}

	@Override
	public Collection<String> getHeaders(String name) {
		synchronized(lock) {
			return new ArrayList<String>(part.getHeaders(name));
		}
	}

	@Override
	public Collection<String> getHeaderNames() {
		synchronized(lock) {
			return new ArrayList<String>(part.getHeaderNames());
		}
	}
}
