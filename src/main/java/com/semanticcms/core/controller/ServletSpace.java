/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2018  AO Industries, Inc.
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

import com.aoindustries.net.Path;
import com.aoindustries.net.pathspace.Prefix;
import com.aoindustries.util.AoCollections;
import com.aoindustries.util.Tuple2;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * See <a href="../servlet-space">Servlet Space</a>.
 *
 * TODO: Move to own semanticcms-servlet-space package? (or even more specific semanticcms-servlet-space-actions for Action?)
 * TODO: Allow configuration via /META-INF/semanticcms-servlet-space.xml files?
 */
public class ServletSpace {

	// TODO: All actions must implement Matcher, too?
	public static interface Action {

		// TODO: semanticCMS and servletPath parameters appropriate?
		void doServletSpace(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain,
			SemanticCMS semanticCMS,
			String servletPath
		) throws IOException, ServletException;

		/**
		 * Passes the request along to the {@link FilterChain#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}.
		 *
		 * // TODO: No longer implement matcher on action, but rather allow adding actions with implied match all matcher
		 *          Same below
		 */
		public static class PassThroughAction implements Matcher, Action {

			private static final PassThroughAction instance = new PassThroughAction();

			public static PassThroughAction getInstance() {
				return instance;
			}

			private PassThroughAction() {
			}

			@Override
			public PassThroughAction findAction(HttpServletRequest request, HttpServletResponse response, SemanticCMS semanticCMS, String servletPath, Prefix prefix, Path servletSpace, Path pathInSpace) throws IOException, ServletException {
				return this;
			}

			@Override
			public void doServletSpace(HttpServletRequest request, HttpServletResponse response, FilterChain chain, SemanticCMS semanticCMS, String servletPath) throws IOException, ServletException {
				chain.doFilter(request, response);
			}
		}

		public abstract static class SendErrorAction implements Matcher, Action {

			public SendErrorAction getInstance(final int errorCode) {
				switch(errorCode) {
					case HttpServletResponse.SC_NOT_FOUND : return NotFoundAction.getInstance();
					default : return new SendErrorAction() {
						@Override
						protected int getErrorCode(HttpServletRequest request, HttpServletResponse response, FilterChain chain, SemanticCMS semanticCMS, String servletPath) throws IOException, ServletException {
							return errorCode;
						}
					};
				}
			}

			protected SendErrorAction() {
			}

			@Override
			public SendErrorAction findAction(HttpServletRequest request, HttpServletResponse response, SemanticCMS semanticCMS, String servletPath, Prefix prefix, Path servletSpace, Path pathInSpace) throws IOException, ServletException {
				return this;
			}

			abstract protected int getErrorCode(HttpServletRequest request, HttpServletResponse response, FilterChain chain, SemanticCMS semanticCMS, String servletPath) throws IOException, ServletException;

			@Override
			public void doServletSpace(HttpServletRequest request, HttpServletResponse response, FilterChain chain, SemanticCMS semanticCMS, String servletPath) throws IOException, ServletException {
				response.sendError(getErrorCode(request, response, chain, semanticCMS, servletPath));
			}
		}

		public static class NotFoundAction extends SendErrorAction {

			private static final NotFoundAction instance = new NotFoundAction();

			public static NotFoundAction getInstance() {
				return instance;
			}

			private NotFoundAction() {
			}

			@Override
			protected int getErrorCode(HttpServletRequest request, HttpServletResponse response, FilterChain chain, SemanticCMS semanticCMS, String servletPath) throws IOException, ServletException {
				// TODO: Return method not allowed for non GET/HEAD/OPTIONS?
				return HttpServletResponse.SC_NOT_FOUND;
			}
		}

		// TODO: Other HTTP codes

		// TODO: Redirect action
	}

	/**
	 * Once a servlet space is resolved, the matcher is consulted to find the
	 * resulting {@link Action}
	 */
	public static interface Matcher {

		// TODO: Matcher for HTTP method

		/**
		 * Finds which {@link Action} to perform for the given request or {@code null} if no match.
		 */
		Action findAction(HttpServletRequest request, HttpServletResponse response, SemanticCMS semanticCMS, String servletPath, Prefix prefix, Path servletSpace, Path pathInSpace) throws IOException, ServletException;

		/**
		 * Iterates any {@link Iterable}&lt;{@link Matcher}, returning the first match.
		 * No defensive copy is made.  The provided iterable must be thread-safe.
		 */
		public static class IterableMatcher implements Matcher {

			private final Iterable<? extends Matcher> iterable;

			public IterableMatcher(Iterable<? extends Matcher> iterable) {
				this.iterable = iterable;
			}

			@Override
			public Action findAction(HttpServletRequest request, HttpServletResponse response, SemanticCMS semanticCMS, String servletPath, Prefix prefix, Path servletSpace, Path pathInSpace) throws IOException, ServletException {
				for(Matcher matcher : iterable) {
					Action action = matcher.findAction(request, response, semanticCMS, servletPath, prefix, servletSpace, pathInSpace);
					if(action != null) return action;
				}
				return null;
			}
		}
	}

	// TODO: Domain as matcher?
	// TODO: Method as matcher?

	private final Map<? extends Prefix, ? extends Matcher> matchersByPrefix;

	private static <P extends Prefix, M extends Matcher> Map<P,M> toMap(Iterable<? extends Tuple2<? extends P, ? extends M>> iterable) {
		Map<P,M> map = new LinkedHashMap<P,M>();
		for(Tuple2<? extends P, ? extends M> tuple : iterable) {
			P prefix = tuple.getElement1();
			M matcher = tuple.getElement2();
			M existing = map.put(prefix, matcher);
			if(existing != null) throw new IllegalArgumentException("Duplicate prefix: " + prefix + ": existing = " + existing + ", matcher = " + matcher);
		}
		return map;
	}

	/**
	 * @param matchersByPrefix  defensive copy made
	 */
	public ServletSpace(Map<? extends Prefix, ? extends Matcher> matchersByPrefix) {
		this.matchersByPrefix = AoCollections.unmodifiableCopyMap(matchersByPrefix);
		if(matchersByPrefix.isEmpty()) throw new IllegalArgumentException("matchersByPrefix is empty");
	}

	public ServletSpace(Iterable<? extends Tuple2<? extends Prefix, ? extends Matcher>> iterable) {
		this(toMap(iterable));
	}

	public ServletSpace(Prefix prefix, Matcher matcher) {
		this(Collections.singletonMap(prefix, matcher));
	}

	public Map<? extends Prefix, ? extends Matcher> getMatchersByPrefix() {
		return matchersByPrefix;
	}
}
