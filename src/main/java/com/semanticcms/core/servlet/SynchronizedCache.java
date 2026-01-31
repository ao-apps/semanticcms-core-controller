/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2025, 2026  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-servlet.
 *
 * semanticcms-core-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.servlet;

import com.aoapps.collections.AoCollections;
import com.aoapps.lang.concurrent.CallableE;
import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A page cache that is thread safe using synchronization on the default HashMaps.
 */
class SynchronizedCache extends MapCache {

  SynchronizedCache() {
    super(
        new HashMap<>(),
        VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<>() : null,
        VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<>() : null,
        new HashMap<>()
    );
  }

  @Override
  synchronized Page get(CaptureKey key) {
    return super.get(key);
  }

  @Override
  synchronized void put(CaptureKey key, Page page) throws ServletException {
    super.put(key, page);
  }

  @Override
  protected void verifyAdded(Page page) throws ServletException {
    assert Thread.holdsLock(this);
    super.verifyAdded(page);
  }

  @Override
  public <K, V> Map<K, V> newMap() {
    return Collections.synchronizedMap(new HashMap<>());
  }

  @Override
  public <K, V> Map<K, V> newMap(int size) {
    return Collections.synchronizedMap(AoCollections.newHashMap(size));
  }

  @Override
  public void setAttribute(String key, Object value) {
    synchronized (attributes) {
      super.setAttribute(key, value);
    }
  }

  @Override
  public Object getAttribute(String key) {
    synchronized (attributes) {
      return super.getAttribute(key);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @Override
  public <V, Ex extends Throwable> V getAttribute(String key, Class<V> clazz, CallableE<? extends V, Ex> callable) throws Ex {
    synchronized (attributes) {
      return super.getAttribute(key, clazz, callable);
    }
  }

  @Override
  public void removeAttribute(String key) {
    synchronized (attributes) {
      super.removeAttribute(key);
    }
  }
}
