/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.lang.NullArgumentException;
import com.semanticcms.core.model.Author;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Copyright;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.model.ResourceRef;
import com.semanticcms.core.pages.PageRepository;
import com.semanticcms.core.resources.Resource;
import com.semanticcms.core.resources.ResourceStore;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A book contains {@link Page pages} and {@link Resource resources} and is the
 * central mechanism for high-level separation of content.  Each book usually
 * has its own code repository and a book can be added to multiple webapps.
 *
 * TODO: Interface + abstract base?
 */
public abstract class Book implements Comparable<Book> {

	protected final BookRef bookRef;

	// TODO: Move out of Book to specific subclasses that use it
	private final String canonicalBase;

	protected Book(BookRef bookRef, String canonicalBase) {
		this.bookRef = NullArgumentException.checkNotNull(bookRef, "bookRef");
		this.canonicalBase = canonicalBase;
	}

	/**
	 * @see  BookRef#toString()
	 */
	@Override
	public String toString() {
		return bookRef.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof Book)) return false;
		Book other = (Book)obj;
		return bookRef.equals(other.bookRef);
	}

	@Override
	public int hashCode() {
		return bookRef.hashCode();
	}

	/**
	 * Ordered by bookRef.
	 *
	 * @see  BookRef#compareTo(com.semanticcms.core.model.BookRef)
	 */
	@Override
	public int compareTo(Book o) {
		return bookRef.compareTo(o.bookRef);
	}

	public final BookRef getBookRef() {
		return bookRef;
	}

	/**
	 * An accessible book is able to invoke/capture its pages and is fully
	 * connected into the page DAG.  Please note that accessible books may not
	 * be local, but they are still part of the set of pages.
	 */
	public abstract boolean isAccessible();

	public boolean isProtected() {
		throw new NotImplementedException("TODO");
	}

	public boolean isPassThroughEnabled() {
		throw new NotImplementedException("TODO");
	}

	public boolean isLocal() {
		throw new NotImplementedException("TODO");
	}

	/**
	 * Gets the {@link PageRepository} for this book.
	 *
	 * @return  The {@link PageRepository} or {@code null} for an inaccessible book
	 */
	public abstract PageRepository getPages();

	/**
	 * Gets the {@link ResourceStore} for this book.
	 *
	 * @return  The {@link ResourceStore} or {@code null} for an inaccessible book
	 */
	public abstract ResourceStore getResources();

	/**
	 * Gets the {@link ResourceRef} for the source of the given {@link Page}.
	 * Although not typical, the resulting reference might be to a different domain/book.
	 *
	 * TODO: This belongs where?
	 * TODO: How is this interact with remote books that have directly accessible resources?
	 *
	 * @return  The {@link ResourceRef} or {@code null} for unknown or an inaccessible book
	 */
	public abstract ResourceRef getPageSource(PageRef pageRef) throws IOException;

	/**
	 * Gets the parent pages for this book in the context of the current overall
	 * content.
	 *
	 * @return  The, possibly empty, set of parents for an accessible book
	 *          or {@code null} for an inaccessible book
	 */
	public abstract Set<ParentRef> getParentRefs();

	/**
	 * Gets the configured canonicalBase for this book, or {@code null} if not
	 * configured or inaccessible.  Any trailing slash (/) has been stripped from the canonicalBase
	 * so can directly concatenate canonicalBase + path
	 */
	public String getCanonicalBase() {
		return canonicalBase;
	}

	/**
	 * Gets the content root for the book or {@code null} if inaccessible.
	 */
	public abstract PageRef getContentRoot();

	/**
	 * Gets the copyright for the book or {@code null} if none declared or inaccessible.
	 * As book copyrights are not inherited, all copyright fields will be non-null.
	 */
	public abstract Copyright getCopyright();

	/**
	 * Gets the authors of the book.  Any page without more specific authors
	 * in itself or a parent (within the book) will use these authors.
	 *
	 * @return  The, possibly empty, set of authors for an accessible book
	 *          or {@code null} for an inaccessible book
	 */
	public abstract Set<Author> getAuthors();

	/**
	 * Gets the book's title or {@code null} if none declared or inaccessible.
	 */
	public abstract String getTitle();

	/**
	 * Gets the allowRobots setting of the book.  Any page with an "auto"
	 * setting and no parents within the book will use this setting.
	 * An inaccessible book must return {@code false}.
	 */
	public abstract boolean getAllowRobots();

	/**
	 * Accesses the books parameters.
	 *
	 * TODO: Should this be named "property" to be consistent with per-page properties?
	 *
	 * @return  The, possibly empty, map of parameters for an accessible book
	 *          or {@code null} for an inaccessible book
	 */
	public abstract Map<String, String> getParam();
}
