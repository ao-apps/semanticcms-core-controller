/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2017, 2018, 2019, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.semanticcms.core.model.Page;
import jakarta.servlet.ServletException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A page cache that is thread safe through concurrent collections.
 *
 * <p>Is currently still synchronized on parent-child verifications, which only occurs on put.</p>
 */
class ConcurrentCache extends MapCache {

  private final ConcurrentMap<String, Object> concurrentAttributes;

  ConcurrentCache() {
    super(
        new ConcurrentHashMap<>(),
        VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<>() : null,
        VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<>() : null,
        new ConcurrentHashMap<>()
    );
    concurrentAttributes = (ConcurrentMap<String, Object>) attributes;
  }

  /**
   * Overridden to add synchronization.
   */
  @Override
  protected synchronized void verifyAdded(Page page) throws ServletException {
    super.verifyAdded(page);
  }

  @Override
  public <K, V> ConcurrentMap<K, V> newMap() {
    return new ConcurrentHashMap<>();
  }

  @Override
  public <K, V> ConcurrentMap<K, V> newMap(int size) {
    return new ConcurrentHashMap<>(size);
  }

  /**
   * {@inheritDoc}
   *
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @Override
  // TODO: Ex extends Throwable
  public <V, Ex extends Exception> V getAttribute(
      String key,
      Class<V> clazz,
      Callable<? extends V, Ex> callable
  ) throws Ex {
    V attribute = getAttribute(key, clazz);
    if (attribute == null) {
      attribute = callable.call();
      Object existing = concurrentAttributes.putIfAbsent(key, attribute);
      if (existing != null) {
        attribute = clazz.cast(existing);
      }
    }
    return attribute;
  }
}
