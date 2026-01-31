/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2016, 2017, 2018, 2019, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.lang.concurrent.CallableE;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.Objects;

/**
 * A caching cache, whether shared between requests or used within the scope of
 * a single request.
 *
 * <p>The thread safety will be the minimum needed for the expected usage.</p>
 *
 * <ol>
 *   <li>Thread safe for caches shared between requests.</li>
 *   <li>Thread safe for caches used in requests that may use concurrent subrequests.</li>
 *   <li>Not thread safe for per-request, no-subrequest caches. (in this case it's
 *       much like request attributes).</li>
 * </ol>
 */
// TODO: Make a Cache.Scope, Cache.Context, and Cache.Attribute that extends Scope, much like Scope.Request and Scope.REQUEST
public abstract class Cache {

  /**
   * Enables the page parent-child relationships verification.
   *
   * <p>This does not measurably affect performance; just leave it on.</p>
   */
  protected static final boolean VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS = true;

  /**
   * Caches pages that have been captured within the scope of a single request.
   *
   * <p>IDEA: Could also cache over time, since there is currently no concept of a "user" (except whether request is trusted
   *       127.0.0.1 or not).</p>
   */
  static class CaptureKey {

    final PageRef pageRef;
    final CaptureLevel level;

    CaptureKey(
        PageRef pageRef,
        CaptureLevel level
    ) {
      this.pageRef = pageRef;
      assert level != CaptureLevel.BODY : "Body captures are not cached";
      this.level = level;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CaptureKey)) {
        return false;
      }
      CaptureKey other = (CaptureKey) o;
      return
          level == other.level
              && pageRef.equals(other.pageRef);
    }

    private int hash;

    @Override
    public int hashCode() {
      int h = hash;
      if (h == 0) {
        h = level.hashCode();
        h = h * 31 + pageRef.hashCode();
        hash = h;
      }
      return h;
    }

    @Override
    public String toString() {
      return '(' + level.toString() + ", " + pageRef.toString() + ')';
    }
  }

  // Java 1.8: Optional<Page>
  static class CaptureResult {

    static final CaptureResult EMPTY = new CaptureResult(null);

    static CaptureResult of(Page page) {
      return page == null ? EMPTY : new CaptureResult(page);
    }

    final Page page;

    private CaptureResult(Page page) {
      this.page = page;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CaptureResult)) {
        return false;
      }
      CaptureResult other = (CaptureResult) o;
      return Objects.equals(page, other.page);
    }

    @Override
    public int hashCode() {
      return Objects.hash(page);
    }

    @Override
    public String toString() {
      return "CaptureResult(" + Objects.toString(page, null) + ')';
    }
  }

  /**
   * A lookup of level PAGE will also perform a lookup of META if not found.
   */
  abstract CaptureResult get(CaptureKey key);

  /**
   * A lookup of level PAGE will also perform a lookup of META if not found.
   */
  CaptureResult get(PageRef pageRef, CaptureLevel level) {
    return get(new CaptureKey(pageRef, level));
  }

  /**
   * Adds the provided page to the cache.  Will also verify parent-child relationships
   * on an as-needed basis.
   */
  abstract void put(CaptureKey key, Page page) throws ServletException;

  /**
   * Creates a new map that is suitable for the expected thread safety requirements.
   * This map will itself be consistent with the thread safety guarantees of this cache overall.
   */
  public abstract <K, V> Map<K, V> newMap();

  /**
   * Creates a new map that is suitable for the expected thread safety requirements.
   * This map will itself be consistent with the thread safety guarantees of this cache overall.
   *
   * @param  size  the number of elements the map can hold before internal resizing.
   *               this is the actual size, no need to consider load factor or other nonsense.
   */
  public abstract <K, V> Map<K, V> newMap(int size);

  /**
   * Sets a cache attribute.
   *
   * @see  Cache#removeAttribute(java.lang.String)  Setting to a null value is equivalent to calling removeAttribute.
   */
  public abstract void setAttribute(String key, Object value);

  /**
   * Gets a cache attribute or null if not in cache.
   */
  public abstract Object getAttribute(String key);

  /**
   * Gets a cache attribute or null if not in cache.
   * The attribute is cast to the provided type.
   */
  public <V> V getAttribute(String key, Class<V> clazz) {
    return clazz.cast(getAttribute(key));
  }

  /**
   * Atomically checks the cache then calls the callable and adds.
   * Note: It is possible the callable might be called and not used.  If
   * clean-up of unused callable is necessary, synchronize externally and use
   * regular getAttribute/setAttribute in sequence.
   *
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  public abstract <V, Ex extends Throwable> V getAttribute(
      String key,
      Class<V> clazz,
      CallableE<? extends V, Ex> callable
  ) throws Ex;

  /**
   * Removes a cache attribute.
   */
  public abstract void removeAttribute(String key);
}
