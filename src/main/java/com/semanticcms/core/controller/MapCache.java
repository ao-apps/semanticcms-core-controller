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

import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.pages.CaptureLevel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;

/**
 * A page cache implemented via a map.
 */
abstract class MapCache extends Cache {

  protected final SemanticCMS semanticCMS;

  private final Map<CaptureKey, CaptureResult> pageCache;

  /**
   * Tracks which parent pages are still not verified.
   * <ul>
   *   <li>Key: The parent pageRef.</li>
   *   <li>Value: The page(s) that claim the pageRef as a parent but are still not verified.</li>
   * </ul>
   */
  private final Map<PageRef, Set<PageRef>> unverifiedParentsByPageRef;

  /**
   * Tracks which child pages are still not verified.
   * <ul>
   *   <li>Key: The child pageRef.</li>
   *   <li>Value: The page(s) that claim the pageRef as a child but are still not verified.</li>
   * </ul>
   */
  private final Map<PageRef, Set<PageRef>> unverifiedChildrenByPageRef;

  /**
   * The map used to store attributes.
   */
  protected final Map<String, Object> attributes;

  MapCache(
      SemanticCMS semanticCMS,
      Map<CaptureKey, CaptureResult> pageCache,
      Map<PageRef, Set<PageRef>> unverifiedParentsByPageRef,
      Map<PageRef, Set<PageRef>> unverifiedChildrenByPageRef,
      Map<String, Object> attributes
  ) {
    this.semanticCMS = semanticCMS;
    this.pageCache = pageCache;
    this.unverifiedParentsByPageRef = unverifiedParentsByPageRef;
    this.unverifiedChildrenByPageRef = unverifiedChildrenByPageRef;
    this.attributes = attributes;
  }

  @Override
  CaptureResult get(CaptureKey key) {
    CaptureResult result = pageCache.get(key);
    if (result == null && key.level == CaptureLevel.PAGE) {
      // Look for meta in place of page
      result = pageCache.get(new CaptureKey(key.pageRef, CaptureLevel.META));
    }
    return result;
  }

  private static void addToSet(Map<PageRef, Set<PageRef>> map, PageRef key, PageRef pageRef) {
    Set<PageRef> pageRefs = map.get(key);
    if (pageRefs == null) {
      map.put(key, Collections.singleton(pageRef));
    } else if (pageRefs.size() == 1) {
      pageRefs = new HashSet<>(pageRefs);
      pageRefs.add(pageRef);
      map.put(key, pageRefs);
    } else {
      pageRefs.add(pageRef);
    }
  }

  @Override
  void put(CaptureKey key, Page page) throws ServletException {
    // Check if found in other level, this is used to avoid verifying twice
    CaptureResult otherLevelResult = page == null ? null : pageCache.get(
        new CaptureKey(key.pageRef, key.level == CaptureLevel.PAGE ? CaptureLevel.META : CaptureLevel.PAGE)
    );
    // Add to cache, verify if this page not yet put into cache
    if (pageCache.put(key, CaptureResult.of(page)) == null) {
      // Was added, now avoid verifying twice typically.
      // In the race condition where both levels check null then are added concurrently, this will verify twice
      // rather than verify none.
      if (VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS) {
        if (otherLevelResult == null) {
          verifyAdded(page);
        }
      }
    }
  }

  protected void verifyAdded(Page page) throws ServletException {
    assert VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS;
    final PageRef pageRef = page.getPageRef();
    Set<ParentRef> parentRefs = null; // Set when first needed
    Set<ChildRef> childRefs = null; // Set when first needed
    // Verify parents that happened to already be cached
    if (!page.getAllowParentMismatch()) {
      parentRefs = page.getParentRefs();
      for (ParentRef parentRef : parentRefs) {
        PageRef parentPageRef = parentRef.getPageRef();
        // Can't verify parent reference to missing book
        if (semanticCMS.getBook(parentPageRef.getBookRef()).isAccessible()) {
          // Check if parent in cache
          CaptureResult parentResult = get(parentPageRef, CaptureLevel.PAGE);
          if (parentResult != null && parentResult.page != null) {
            PageUtils.verifyChildToParent(pageRef, parentPageRef, parentResult.page.getChildRefs());
          } else {
            addToSet(unverifiedParentsByPageRef, parentPageRef, pageRef);
          }
        }
      }
    }
    // Verify children that happened to already be cached
    if (!page.getAllowChildMismatch()) {
      childRefs = page.getChildRefs();
      for (ChildRef childRef : childRefs) {
        PageRef childPageRef = childRef.getPageRef();
        // Can't verify child reference to missing book
        if (semanticCMS.getBook(childPageRef.getBookRef()).isAccessible()) {
          // Check if child in cache
          CaptureResult childResult = get(childPageRef, CaptureLevel.PAGE);
          if (childResult != null && childResult.page != null) {
            PageUtils.verifyParentToChild(pageRef, childPageRef, childResult.page.getParentRefs());
          } else {
            addToSet(unverifiedChildrenByPageRef, childPageRef, pageRef);
          }
        }
      }
    }
    // Verify any pages that have claimed this page as their parent and are not yet verified
    Set<PageRef> unverifiedParents = unverifiedParentsByPageRef.remove(pageRef);
    if (unverifiedParents != null) {
      if (childRefs == null) {
        childRefs = page.getChildRefs();
      }
      for (PageRef unverifiedParent : unverifiedParents) {
        PageUtils.verifyChildToParent(unverifiedParent, pageRef, childRefs);
      }
    }
    // Verify any pages that have claimed this page as their child and are not yet verified
    Set<PageRef> unverifiedChildren = unverifiedChildrenByPageRef.remove(pageRef);
    if (unverifiedChildren != null) {
      if (parentRefs == null) {
        parentRefs = page.getParentRefs();
      }
      for (PageRef unverifiedChild : unverifiedChildren) {
        PageUtils.verifyParentToChild(unverifiedChild, pageRef, parentRefs);
      }
    }
  }

  @Override
  public void setAttribute(String key, Object value) {
    if (value == null) {
      attributes.remove(key);
    } else {
      attributes.put(key, value);
    }
  }

  @Override
  public Object getAttribute(String key) {
    return attributes.get(key);
  }

  /**
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
      setAttribute(key, attribute);
    }
    return attribute;
  }

  @Override
  public void removeAttribute(String key) {
    attributes.remove(key);
  }
}
