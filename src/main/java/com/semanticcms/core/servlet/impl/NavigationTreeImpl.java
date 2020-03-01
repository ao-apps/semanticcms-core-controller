/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2019, 2020  AO Industries, Inc.
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
 * along with semanticcms-core-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.servlet.impl;

import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import com.aoindustries.html.Html;
import com.aoindustries.net.URIDecoder;
import com.aoindustries.net.URIEncoder;
import static com.aoindustries.taglib.AttributeUtils.resolveValue;
import com.aoindustries.util.StringUtility;
import static com.aoindustries.util.StringUtility.nullIfEmpty;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.PageReferrer;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.PageIndex;
import com.semanticcms.core.servlet.PageRefResolver;
import com.semanticcms.core.servlet.PageUtils;
import com.semanticcms.core.servlet.SemanticCMS;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class NavigationTreeImpl {

	public static <T extends Node> List<T> filterNodes(Collection<T> children, Set<T> nodesToInclude) {
		int size = children.size();
		if(size == 0) return Collections.emptyList();
		List<T> filtered = new ArrayList<>(size);
		for(T child : children) {
			if(nodesToInclude.contains(child)) {
				filtered.add(child);
			}
		}
		return filtered;
	}

	public static <T extends PageReferrer> List<T> filterPages(Collection<T> children, Set<PageRef> pagesToInclude) {
		int size = children.size();
		if(size == 0) return Collections.emptyList();
		List<T> filtered = new ArrayList<>(size);
		for(T child : children) {
			if(pagesToInclude.contains(child.getPageRef())) {
				filtered.add(child);
			}
		}
		return filtered;
	}

	public static List<Node> getChildNodes(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		boolean includeElements,
		boolean metaCapture,
		Node node
	) throws ServletException, IOException {
		// Both elements and pages are child nodes
		List<Element> childElements = includeElements ? node.getChildElements() : null;
		Set<ChildRef> childRefs = (node instanceof Page) ? ((Page)node).getChildRefs() : null;
		List<Node> childNodes = new ArrayList<>(
			(childElements==null ? 0 : childElements.size())
			+ (childRefs==null ? 0 : childRefs.size())
		);
		if(includeElements) {
			assert childElements != null;
			for(Element childElem : childElements) {
				if(!childElem.isHidden()) childNodes.add(childElem);
			}
		}
		if(childRefs != null) {
			for(ChildRef childRef : childRefs) {
				PageRef childPageRef = childRef.getPageRef();
				// Child not in missing book
				if(childPageRef.getBook() != null) {
					Page childPage = CapturePage.capturePage(servletContext, request, response, childPageRef, includeElements || metaCapture ? CaptureLevel.META : CaptureLevel.PAGE);
					childNodes.add(childPage);
				}
			}
		}
		return childNodes;
	}

	private static boolean findLinks(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef linksTo,
		Set<Node> nodesWithLinks,
		Set<Node> nodesWithChildLinks,
		Node node,
		boolean includeElements
	) throws ServletException, IOException {
		boolean hasChildLink = false;
		if(node.getPageLinks().contains(linksTo)) {
			nodesWithLinks.add(node);
			hasChildLink = true;
		}
		if(includeElements) {
			for(Element childElem : node.getChildElements()) {
				if(
					!childElem.isHidden()
					&& findLinks(servletContext, request, response, linksTo, nodesWithLinks, nodesWithChildLinks, childElem, includeElements)
				) {
					hasChildLink = true;
				}
			}
		} else {
			assert (node instanceof Page);
			if(!hasChildLink) {
				// Not including elements, so any link from an element must be considered a link from the page the element is on
				Page page = (Page)node;
				for(Element e : page.getElements()) {
					if(e.getPageLinks().contains(linksTo)) {
						nodesWithLinks.add(node);
						hasChildLink = true;
						break;
					}
				}
			}
		}
		if(node instanceof Page) {
			for(ChildRef childRef : ((Page)node).getChildRefs()) {
				PageRef childPageRef = childRef.getPageRef();
				// Child not in missing book
				if(childPageRef.getBook() != null) {
					Page child = CapturePage.capturePage(servletContext, request, response, childPageRef, CaptureLevel.META);
					if(findLinks(servletContext, request, response, linksTo, nodesWithLinks, nodesWithChildLinks, child, includeElements)) {
						hasChildLink = true;
					}
				}
			}
		}
		if(hasChildLink) {
			nodesWithChildLinks.add(node);
		}
		return hasChildLink;
	}

	public static String encodeHexData(String data) {
		// Note: This is always UTF-8 encoded and does not depend on response encoding
		return StringUtility.convertToHex(data.getBytes(StandardCharsets.UTF_8));
	}

	public static void writeNavigationTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Html html,
		Page root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		String thisBook,
		String thisPage,
		String linksToBook,
		String linksToPage,
		int maxDepth
	) throws ServletException, IOException {
		// Get the current capture state
		CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			writeNavigationTreeImpl(
				servletContext,
				request,
				response,
				html,
				root,
				skipRoot,
				yuiConfig,
				includeElements,
				target,
				thisBook,
				thisPage,
				linksToBook,
				linksToPage,
				maxDepth,
				captureLevel
			);
		}
	}

	/**
	 * @param root  ValueExpression that returns Page
	 * @param thisBook  ValueExpression that returns String
	 * @param thisPage  ValueExpression that returns String
	 * @param linksToBook  ValueExpression that returns String
	 * @param linksToPage  ValueExpression that returns String
	 */
	public static void writeNavigationTreeImpl(
		ServletContext servletContext,
		ELContext elContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Html html,
		ValueExpression root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		ValueExpression thisBook,
		ValueExpression thisPage,
		ValueExpression linksToBook,
		ValueExpression linksToPage,
		int maxDepth
	) throws ServletException, IOException {
		// Get the current capture state
		CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			writeNavigationTreeImpl(
				servletContext,
				request,
				response,
				html,
				resolveValue(root, Page.class, elContext),
				skipRoot,
				yuiConfig,
				includeElements,
				target,
				resolveValue(thisBook, String.class, elContext),
				resolveValue(thisPage, String.class, elContext),
				resolveValue(linksToBook, String.class, elContext),
				resolveValue(linksToPage, String.class, elContext),
				maxDepth,
				captureLevel
			);
		}
	}

	private static void writeNavigationTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Html html,
		Page root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		String thisBook,
		String thisPage,
		String linksToBook,
		String linksToPage,
		int maxDepth,
		CaptureLevel captureLevel
	) throws ServletException, IOException {
		assert captureLevel.compareTo(CaptureLevel.META) >= 0;
		final Node currentNode = CurrentNode.getCurrentNode(request);

		thisBook = nullIfEmpty(thisBook);
		thisPage = nullIfEmpty(thisPage);
		linksToBook = nullIfEmpty(linksToBook);
		linksToPage = nullIfEmpty(linksToPage);

		// Filter by link-to
		final Set<Node> nodesWithLinks;
		final Set<Node> nodesWithChildLinks;
		if(linksToPage == null) {
			if(linksToBook != null) throw new ServletException("linksToPage must be provided when linksToBook is provided.");
			nodesWithLinks = null;
			nodesWithChildLinks = null;
		} else {
			// Find all nodes in the navigation tree that link to the linksToPage
			PageRef linksTo = PageRefResolver.getPageRef(servletContext, request, linksToBook, linksToPage);
			nodesWithLinks = new HashSet<>();
			nodesWithChildLinks = new HashSet<>();
			findLinks(
				servletContext,
				request,
				response,
				linksTo,
				nodesWithLinks,
				nodesWithChildLinks,
				root,
				includeElements
			);
		}

		PageRef thisPageRef;
		if(thisPage == null) {
			if(thisBook != null) throw new ServletException("thisPage must be provided when thisBook is provided.");
			thisPageRef = null;
		} else {
			thisPageRef = PageRefResolver.getPageRef(servletContext, request, thisBook, thisPage);
		}

		boolean foundThisPage = false;
		PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
		if(skipRoot) {
			List<Node> childNodes = NavigationTreeImpl.getChildNodes(
				servletContext,
				request,
				response,
				includeElements,
				false,
				root
			);
			if(nodesWithChildLinks != null) {
				childNodes = NavigationTreeImpl.filterNodes(childNodes, nodesWithChildLinks);
			}
			if(!childNodes.isEmpty()) {
				if(captureLevel == CaptureLevel.BODY) html.out.write("<ul>\n");
				for(Node childNode : childNodes) {
					foundThisPage = writeNode(
						servletContext,
						request,
						response,
						captureLevel == CaptureLevel.BODY ? html : null,
						currentNode,
						nodesWithLinks,
						nodesWithChildLinks,
						pageIndex,
						null,
						childNode,
						yuiConfig,
						includeElements,
						target,
						thisPageRef,
						foundThisPage,
						maxDepth,
						1
					);
				}
				if(captureLevel == CaptureLevel.BODY) html.out.write("</ul>\n");
			}
		} else {
			if(captureLevel == CaptureLevel.BODY) html.out.write("<ul>\n");
			/*foundThisPage =*/ writeNode(
				servletContext,
				request,
				response,
				captureLevel == CaptureLevel.BODY ? html : null,
				currentNode,
				nodesWithLinks,
				nodesWithChildLinks,
				pageIndex,
				null,
				root,
				yuiConfig,
				includeElements,
				target,
				thisPageRef,
				foundThisPage,
				maxDepth,
				1
			);
			if(captureLevel == CaptureLevel.BODY) html.out.write("</ul>\n");
		}
	}

	private static boolean writeNode(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Html html,
		Node currentNode,
		Set<Node> nodesWithLinks,
		Set<Node> nodesWithChildLinks,
		PageIndex pageIndex,
		PageRef parentPageRef,
		Node node,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		PageRef thisPageRef,
		boolean foundThisPage,
		int maxDepth,
		int level
	) throws IOException, ServletException {
		final Page page;
		final Element element;
		if(node instanceof Page) {
			page = (Page)node;
			element = null;
		} else if(node instanceof Element) {
			assert includeElements;
			element = (Element)node;
			assert !element.isHidden();
			page = element.getPage();
		} else {
			throw new AssertionError();
		}
		final PageRef pageRef = page.getPageRef();
		if(currentNode != null) {
			// Add page links
			currentNode.addPageLink(pageRef);
		}
		final String servletPath;
		if(html == null) {
			// Will be unused
			servletPath = null;
		} else {
			if(element == null) {
				servletPath = pageRef.getServletPath();
			} else {
				// TODO: encodeIRIComponent to do this in one shot?
				String elemIdIri = URIDecoder.decodeURI(URIEncoder.encodeURIComponent(element.getId()));
				assert elemIdIri != null;
				String bookPrefix = pageRef.getBookPrefix();
				String pagePath = pageRef.getPath();
				int sbLen =
					bookPrefix.length()
					+ pagePath.length()
					+ 1 // '#'
					+ elemIdIri.length();
				StringBuilder sb = new StringBuilder(sbLen);
				sb
					.append(bookPrefix)
					.append(pagePath)
					.append('#')
					.append(elemIdIri);
				assert sb.length() == sbLen;
				servletPath = sb.toString();
			}
		}
		if(html != null) {
			html.out.write("<li");
			if(yuiConfig) {
				html.out.write(" yuiConfig='{\"data\":\"");
				encodeTextInXhtmlAttribute(encodeHexData(servletPath), html.out);
				html.out.write("\"}'");
			}
			SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			String listItemCssClass = semanticCMS.getListItemCssClass(node);
			if(listItemCssClass != null || level == 1) {
				html.out.write(" class=\"");
				boolean didClass = false;
				if(listItemCssClass != null) {
					encodeTextInXhtmlAttribute(listItemCssClass, html.out);
					didClass = true;
				}
				if(level == 1) {
					if(didClass) html.out.write(' ');
					html.out.write("expanded");
				}
				html.out.write('"');
			}
			html.out.write("><a");
		}
		// Look for thisPage match
		boolean thisPageClass = false;
		if(pageRef.equals(thisPageRef) && element == null) {
			if(!foundThisPage) {
				if(html != null) html.out.write(" id=\"semanticcms-core-tree-this-page\"");
				foundThisPage = true;
			}
			thisPageClass = true;
		}
		// Look for linkToPage match
		boolean linksToPageClass = nodesWithLinks!=null && nodesWithLinks.contains(node);
		if(html != null && (thisPageClass || linksToPageClass)) {
			html.out.write(" class=\"");
			if(thisPageClass && nodesWithLinks!=null && !linksToPageClass) {
				html.out.write("semanticcms-core-no-link-to-this-page");
			} else if(thisPageClass) {
				html.out.write("semanticcms-core-tree-this-page");
			} else if(linksToPageClass) {
				html.out.write("semanticcms-core-links-to-page");
			} else {
				throw new AssertionError();
			}
			html.out.write('"');
		}
		if(html != null) {
			if(target != null) {
				html.out.write(" target=\"");
				encodeTextInXhtmlAttribute(target, html.out);
				html.out.write('"');
			}
			Integer index = pageIndex==null ? null : pageIndex.getPageIndex(pageRef);
			html.out.write(" href=\"");
			StringBuilder href = new StringBuilder();
			if(index != null) {
				href.append('#');
				URIEncoder.encodeURIComponent(
					PageIndex.getRefId(
						index,
						element==null ? null : element.getId()
					),
					href
				);
			} else {
				URIEncoder.encodeURI(request.getContextPath(), href);
				URIEncoder.encodeURI(servletPath, href);
			}
			encodeTextInXhtmlAttribute(
				response.encodeURL(
					href.toString()
				),
				html.out
			);
			html.out.write("\">");
			if(node instanceof Page) {
				// Use shortTitle for pages
				html.text(PageUtils.getShortTitle(parentPageRef, (Page)node));
			} else {
				html.text(node.getLabel());
			}
			if(index != null) {
				html.out.write("<sup>[");
				html.text(index + 1);
				html.out.write("]</sup>");
			}
			html.out.write("</a>");
		}
		if(maxDepth==0 || level < maxDepth) {
			List<Node> childNodes = NavigationTreeImpl.getChildNodes(servletContext, request, response, includeElements, false, node);
			if(nodesWithChildLinks!=null) {
				childNodes = NavigationTreeImpl.filterNodes(childNodes, nodesWithChildLinks);
			}
			if(!childNodes.isEmpty()) {
				if(html != null) {
					html.out.write("\n"
						+ "<ul>\n");
				}
				for(Node childNode : childNodes) {
					foundThisPage = writeNode(
						servletContext,
						request,
						response,
						html,
						currentNode,
						nodesWithLinks,
						nodesWithChildLinks,
						pageIndex,
						element==null ? pageRef : parentPageRef,
						childNode,
						yuiConfig,
						includeElements,
						target,
						thisPageRef,
						foundThisPage,
						maxDepth,
						level+1
					);
				}
				if(html != null) html.out.write("</ul>\n");
			}
		}
		if(html != null) html.out.write("</li>\n");
		return foundThisPage;
	}

	/**
	 * Make no instances.
	 */
	private NavigationTreeImpl() {
	}
}
