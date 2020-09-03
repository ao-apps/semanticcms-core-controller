/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2018, 2019, 2020  AO Industries, Inc.
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
package com.semanticcms.core.controller;

import static com.semanticcms.core.controller.Cache.VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;

/**
 * A page cache that is not thread safe and should only be used within the
 * context of a single thread.
 */
class SingleThreadCache extends MapCache {

	private final Thread assertingThread;

	@SuppressWarnings("AssertWithSideEffects")
	SingleThreadCache(SemanticCMS semanticCMS) {
		super(
			semanticCMS,
			new HashMap<CaptureKey,CaptureResult>(),
			VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<PageRef,Set<PageRef>>() : null,
			VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<PageRef,Set<PageRef>>() : null,
			new HashMap<String, Object>()
		);
		Thread t = null;
		// Intentional side-effect from assert
		assert (t = Thread.currentThread()) != null;
		assertingThread = t;
	}

	@Override
	CaptureResult get(CaptureKey key) {
		assert assertingThread == Thread.currentThread();
		return super.get(key);
	}

	@Override
	void put(CaptureKey key, Page page) throws ServletException {
		assert assertingThread == Thread.currentThread();
		super.put(key, page);
	}

	@Override
	public <K,V> Map<K,V> newMap() {
		assert assertingThread == Thread.currentThread();
		return new HashMap<>();
	}

	@Override
	public <K,V> Map<K,V> newMap(int size) {
		assert assertingThread == Thread.currentThread();
		return new HashMap<>(size *4/3+1);
	}

	@Override
	public void setAttribute(String key, Object value) {
		assert assertingThread == Thread.currentThread();
		super.setAttribute(key, value);
	}

	@Override
	public Object getAttribute(String key) {
		assert assertingThread == Thread.currentThread();
		return super.getAttribute(key);
	}

	@Override
	public <V, E extends Exception> V getAttribute(String key, Class<V> clazz, Callable<? extends V, E> callable) throws E {
		assert assertingThread == Thread.currentThread();
		return super.getAttribute(key, clazz, callable);
	}

	@Override
	public void removeAttribute(String key) {
		assert assertingThread == Thread.currentThread();
		super.removeAttribute(key);
	}
}
