/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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
 * along with semanticcms-core-controller.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.controller;

import com.aoapps.collections.AoCollections;
import com.semanticcms.core.model.Page;
import java.util.HashMap;
import java.util.Map;
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
			new HashMap<>(),
			VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<>() : null,
			VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<>() : null,
			new HashMap<>()
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
	public <K, V> Map<K, V> newMap() {
		assert assertingThread == Thread.currentThread();
		return new HashMap<>();
	}

	@Override
	public <K, V> Map<K, V> newMap(int size) {
		assert assertingThread == Thread.currentThread();
		return AoCollections.newHashMap(size);
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

	/**
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 */
	@Override
	// TODO: Ex extends Throwable
	public <V, Ex extends Exception> V getAttribute(String key, Class<V> clazz, Callable<? extends V, Ex> callable) throws Ex {
		assert assertingThread == Thread.currentThread();
		return super.getAttribute(key, clazz, callable);
	}

	@Override
	public void removeAttribute(String key) {
		assert assertingThread == Thread.currentThread();
		super.removeAttribute(key);
	}
}
