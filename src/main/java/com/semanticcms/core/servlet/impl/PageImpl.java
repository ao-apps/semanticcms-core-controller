/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2025  AO Industries, Inc.
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

package com.semanticcms.core.servlet.impl;

import static com.semanticcms.core.servlet.Resources.PACKAGE_RESOURCES;

import com.aoapps.encoding.Doctype;
import com.aoapps.encoding.Serialization;
import com.aoapps.encoding.servlet.DoctypeEE;
import com.aoapps.encoding.servlet.SerializationEE;
import com.aoapps.html.any.AnyDocument;
import com.aoapps.html.servlet.DocumentEE;
import com.aoapps.io.buffer.BufferResult;
import com.aoapps.servlet.LocalizedServletException;
import com.aoapps.servlet.ServletContextCache;
import com.aoapps.servlet.ServletUtil;
import com.aoapps.web.resources.registry.Registry;
import com.aoapps.web.resources.servlet.RegistryEE;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.CurrentPage;
import com.semanticcms.core.servlet.PageUtils;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.Theme;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public final class PageImpl {

  private static final Logger logger = Logger.getLogger(PageImpl.class.getName());

  /** Make no instances. */
  private PageImpl() {
    throw new AssertionError();
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @FunctionalInterface
  public static interface PageImplBody<Ex extends Throwable> {
    BufferResult doBody(boolean discard, Page page) throws Ex, IOException, SkipPageException;
  }

  /**
   * Verified one child-parent relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyChildToParent(ChildRef childRef, PageRef parentPageRef, Set<ChildRef> childRefs) throws ServletException {
    if (!childRefs.contains(childRef)) {
      throw new ServletException(
          "The parent page does not have this as a child.  this="
              + childRef
              + ", parent="
              + parentPageRef
              + ", parent.children="
              + childRefs
      );
    }
  }

  /**
   * Verified one child-parent relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyChildToParent(PageRef childPageRef, PageRef parentPageRef, Set<ChildRef> childRefs) throws ServletException {
    verifyChildToParent(new ChildRef(childPageRef), parentPageRef, childRefs);
  }

  /**
   * Verified one parent-child relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyParentToChild(ParentRef parentRef, PageRef childPageRef, Set<ParentRef> parentRefs) throws ServletException {
    if (!parentRefs.contains(parentRef)) {
      throw new ServletException(
          "The child page does not have this as a parent.  this="
              + parentRef
              + ", child="
              + childPageRef
              + ", child.parents="
              + parentRefs
      );
    }
  }

  /**
   * Verified one parent-child relationship.
   *
   * @throws  ServletException  if verification failed
   */
  public static void verifyParentToChild(PageRef parentPageRef, PageRef childPageRef, Set<ParentRef> parentRefs) throws ServletException {
    verifyParentToChild(new ParentRef(parentPageRef, null), childPageRef, parentRefs);
  }

  /**
   * Performs full parent/child verifications of the provided page.  This is normally
   * not needed for pages that have been added to the cache (PAGE/META level), as verification
   * is done within the cache.  This is used for BODY level captures which are not put in the
   * cache and desire full verification.
   *
   * @throws  ServletException  if verification failed
   */
  public static void fullVerifyParentChild(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      Page page
  ) throws ServletException, IOException {
    // Verify parents
    if (!page.getAllowParentMismatch()) {
      Map<PageRef, Page> notMissingParents = CapturePage.capturePages(
          servletContext,
          request,
          response,
          PageUtils.filterNotMissingBook(page.getParentRefs()),
          CaptureLevel.PAGE
      );
      PageRef pageRef = page.getPageRef();
      for (Map.Entry<PageRef, Page> entry : notMissingParents.entrySet()) {
        verifyChildToParent(pageRef, entry.getKey(), entry.getValue().getChildRefs());
      }
    }
    // Verify children
    if (!page.getAllowChildMismatch()) {
      Map<PageRef, Page> notMissingChildren = CapturePage.capturePages(
          servletContext,
          request,
          response,
          PageUtils.filterNotMissingBook(page.getChildRefs()),
          CaptureLevel.PAGE
      );
      PageRef pageRef = page.getPageRef();
      for (Map.Entry<PageRef, Page> entry : notMissingChildren.entrySet()) {
        verifyParentToChild(pageRef, entry.getKey(), entry.getValue().getParentRefs());
      }
    }
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   * @param pageRef  the default path to this page, this might be changed during page processing
   */
  // TODO: Doctype and Serialization set before this - document here like in WebPageLayout.startHtml
  // TODO: doctype and serialization on Page and PageTag like in ao:html tag
  // TODO: themes adhere to current doctype and serialization, don't reset in ao:html tag itself
  // TODO: page captures reset to default null on request
  // TODO: All theme/layout/skin support both HTML 4 and 5?
  // TODO: Fall-back to div without semantic tags?
  public static <Ex extends Throwable> void doPageImpl(
      final ServletContext servletContext,
      final HttpServletRequest request,
      final HttpServletResponse response,
      PageRef pageRef,
      ZonedDateTime dateCreated,
      ZonedDateTime datePublished,
      ZonedDateTime dateModified,
      ZonedDateTime dateReviewed,
      Serialization serialization,
      Doctype doctype,
      Boolean autonli,
      Boolean indent,
      String title,
      String shortTitle,
      String description,
      String keywords,
      Boolean allowRobots,
      Boolean toc,
      int tocLevels,
      boolean allowParentMismatch,
      boolean allowChildMismatch,
      Map<String, Object> properties,
      PageImplBody<Ex> body
  ) throws Ex, ServletException, IOException, SkipPageException {
    final Page page = new Page();
    page.setPageRef(pageRef);
    {
      // Pages may not be nested within any kind of node
      Node parentNode = CurrentNode.getCurrentNode(request);
      if (parentNode != null) {
        throw new ServletException("Pages may not be nested within other nodes: " + page.getPageRef() + " not allowed inside of " + parentNode);
      }
      assert CurrentPage.getCurrentPage(request) == null : "When no parent node, cannot have a parent page";
    }

    page.setDateCreated(dateCreated);
    page.setDatePublished(datePublished);
    page.setDateModified(dateModified);
    page.setDateReviewed(dateReviewed);
    page.setTitle(title);
    page.setShortTitle(shortTitle);
    page.setDescription(description);
    page.setKeywords(keywords);
    page.setAllowRobots(allowRobots);
    page.setToc(toc);
    page.setTocLevels(tocLevels);
    page.setAllowParentMismatch(allowParentMismatch);
    page.setAllowChildMismatch(allowChildMismatch);
    if (properties != null) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        String name = entry.getKey();
        if (!page.setProperty(name, entry.getValue())) {
          throw new LocalizedServletException(
              PACKAGE_RESOURCES,
              "error.duplicatePageProperty",
              name
          );
        }
      }
    }
    Serialization oldSerialization;
    boolean setSerialization;
    if (serialization == null) {
      serialization = SerializationEE.get(servletContext, request);
      oldSerialization = null;
      setSerialization = false;
    } else {
      oldSerialization = SerializationEE.replace(request, serialization);
      setSerialization = true;
    }
    assert serialization != null;
    try {
      Doctype oldDoctype;
      boolean setDoctype;
      if (doctype == null) {
        doctype = DoctypeEE.get(servletContext, request);
        oldDoctype = null;
        setDoctype = false;
      } else {
        oldDoctype = DoctypeEE.replace(request, doctype);
        setDoctype = true;
      }
      assert doctype != null;
      try {
        Boolean oldAutonli;
        boolean setAutonli;
        if (autonli == null) {
          autonli = DocumentEE.getAutonli(servletContext, request);
          oldAutonli = null;
          setAutonli = false;
        } else {
          oldAutonli = DocumentEE.replaceAutonli(request, autonli);
          setAutonli = true;
        }
        assert autonli != null;
        try {
          Boolean oldIndent;
          boolean setIndent;
          if (indent == null) {
            indent = DocumentEE.getIndent(servletContext, request);
            oldIndent = null;
            setIndent = false;
          } else {
            oldIndent = DocumentEE.replaceIndent(request, indent);
            setIndent = true;
          }
          assert indent != null;
          try {
            // Freeze page once body done
            try {
              Registry oldPageRegistry = RegistryEE.Page.get(request);
              try {
                Registry pageRegistry = new Registry();
                page.setRegistry(pageRegistry);
                RegistryEE.Page.set(request, pageRegistry);

                // Unlike elements, the page body is still invoked on captureLevel=PAGE, this
                // is done to catch parents and children.
                if (body != null) {
                  // Set currentNode
                  CurrentNode.setCurrentNode(request, page);
                  try {
                    // Set currentPage
                    CurrentPage.setCurrentPage(request, page);
                    try {
                      final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
                      if (captureLevel == CaptureLevel.BODY) {
                        // Invoke page body, capturing output
                        page.setBody(body.doBody(false, page).trim());
                      } else {
                        // Invoke page body, discarding output
                        body.doBody(true, page);
                      }
                      // Page may not move itself to a different book
                      PageRef newPageRef = page.getPageRef();
                      if (!newPageRef.getBook().equals(pageRef.getBook())) {
                        throw new ServletException(
                            "Page may not move itself into a different book.  pageRef="
                                + pageRef
                                + ", newPageRef="
                                + newPageRef
                        );
                      }
                    } finally {
                      // Restore previous currentPage
                      CurrentPage.setCurrentPage(request, null);
                    }
                  } finally {
                    // Restore previous currentNode
                    CurrentNode.setCurrentNode(request, null);
                  }
                }
              } finally {
                RegistryEE.Page.set(request, oldPageRegistry);
              }
              doAutoParents(servletContext, page);
            } finally {
              page.freeze();
            }
            CapturePage capture = CapturePage.getCaptureContext(request);
            if (capture != null) {
              // Capturing, add to capture
              capture.setCapturedPage(page);
            } else {
              // Perform full verification now since not interacting with the page cache
              fullVerifyParentChild(servletContext, request, response, page);

              // Resolve the view
              SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
              View view;
              {
                String viewName = request.getParameter(SemanticCMS.VIEW_PARAM);
                Map<String, View> viewsMap = semanticCms.getViewsByName();
                if (viewName == null) {
                  view = null;
                } else {
                  if (SemanticCMS.DEFAULT_VIEW_NAME.equals(viewName)) {
                    throw new ServletException(SemanticCMS.VIEW_PARAM + " paramater may not be sent for default view: " + viewName);
                  }
                  view = viewsMap.get(viewName);
                }
                if (view == null) {
                  // Find default
                  view = viewsMap.get(SemanticCMS.DEFAULT_VIEW_NAME);
                  if (view == null) {
                    throw new ServletException("Default view not found: " + SemanticCMS.DEFAULT_VIEW_NAME);
                  }
                }
              }

              // Find the theme
              Theme theme = null;
              {
                // Currently just picks the first non-default theme registered, the uses default
                Theme defaultTheme = null;
                for (Theme t : semanticCms.getThemes().values()) {
                  if (t.isDefault()) {
                    assert defaultTheme == null : "More than one default theme registered";
                    defaultTheme = t;
                  } else {
                    // Use first non-default
                    theme = t;
                    break;
                  }
                }
                if (theme == null) {
                  // Use default
                  if (defaultTheme == null) {
                    throw new ServletException("No themes registered");
                  }
                  theme = defaultTheme;
                }
                assert theme != null;
              }

              // Clear the output buffer
              response.resetBuffer();

              // Set the content type
              ServletUtil.setContentType(response, serialization.getContentType(), AnyDocument.ENCODING.name());

              Theme oldTheme = Theme.getTheme(request);
              try {
                Theme.setTheme(request, theme);

                // Configure the theme resources
                theme.configureResources(
                    servletContext,
                    request,
                    response,
                    view,
                    page,
                    RegistryEE.Request.get(servletContext, request)
                );

                // Configure the view resources
                view.configureResources(
                    servletContext,
                    request,
                    response,
                    theme,
                    page,
                    RegistryEE.Request.get(servletContext, request)
                );

                // TODO: Configure the page resources here or within view?

                // Forward to theme
                if (!response.isCommitted()) {
                  theme.doTheme(servletContext, request, response, view, page);
                } else {
                  logger.log(Level.FINE, "Not forwarding to theme due to response already committed: request.servletPath = {0}, theme = {1}",
                      new Object[] {request.getServletPath(), theme});
                  throw new SkipPageException("Response already committed, unable to forward to theme");
                }
              } finally {
                Theme.setTheme(request, oldTheme);
              }
              throw ServletUtil.SKIP_PAGE_EXCEPTION;
            }
          } finally {
            if (setIndent) {
              DocumentEE.setIndent(request, oldIndent);
            }
          }
        } finally {
          if (setAutonli) {
            DocumentEE.setAutonli(request, oldAutonli);
          }
        }
      } finally {
        if (setDoctype) {
          DoctypeEE.set(request, oldDoctype);
        }
      }
    } finally {
      if (setSerialization) {
        SerializationEE.set(request, oldSerialization);
      }
    }
  }

  // TODO: Profile this since have many books now.  Maybe create method on SemanticCMS for this lookup
  private static Book findBookByContentRoot(ServletContext servletContext, PageRef pageRef) {
    for (Book book : SemanticCMS.getInstance(servletContext).getBooks().values()) {
      if (pageRef.equals(book.getContentRoot())) {
        return book;
      }
    }
    return null;
  }

  private static void doAutoParents(ServletContext servletContext, Page page) throws ServletException, MalformedURLException {
    if (page.getParentRefs().isEmpty()) {
      // Auto parents

      // PageRef might have been changed during page capture if the default value was incorrect, such as when using pathInfo, get the new value
      PageRef pageRef = page.getPageRef();

      // If this page is the "content.root" of a book, include all parents configured when book imported.
      Book book = findBookByContentRoot(servletContext, pageRef);
      if (book != null) {
        for (ParentRef bookParentRef : book.getParentRefs()) {
          page.addParentRef(bookParentRef);
        }
      } else {
        // Otherwise, try auto parents
        ServletContextCache servletContextCache = ServletContextCache.getInstance(servletContext);
        Book pageBook = pageRef.getBook();
        assert pageBook != null;
        String pagePath = pageRef.getPath();
        if (
            pagePath.endsWith("/")
                || pagePath.endsWith("/index.jspx")
                || pagePath.endsWith("/index.jsp")
        ) {
          // If this page URL ends in "/", "/index.jspx" or "/index.jsp", look for JSP page at "../index.jspx" or "../index.jsp" then asssume "../", error if outside book.
          int lastSlash = pagePath.lastIndexOf('/');
          if (lastSlash == -1) {
            throw new AssertionError();
          }
          int nextLastSlash = pagePath.lastIndexOf('/', lastSlash - 1);
          if (nextLastSlash == -1) {
            throw new ServletException("Auto parent of page would be outside book: " + pageRef);
          }
          String endSlashPath = pagePath.substring(0, nextLastSlash + 1);
          PageRef indexJspxPageRef = new PageRef(pageBook, endSlashPath + "index.jspx");
          if (servletContextCache.getResource(indexJspxPageRef.getServletPath()) != null) {
            page.addParentRef(new ParentRef(indexJspxPageRef, null));
          } else {
            PageRef indexJspPageRef = new PageRef(pageBook, endSlashPath + "index.jsp");
            if (servletContextCache.getResource(indexJspPageRef.getServletPath()) != null) {
              page.addParentRef(new ParentRef(indexJspPageRef, null));
            } else {
              page.addParentRef(new ParentRef(new PageRef(pageBook, endSlashPath), null));
            }
          }
        } else {
          // Look for page at "./index.jspx" or "./index.jsp" then assume "./".
          int lastSlash = pagePath.lastIndexOf('/');
          if (lastSlash == -1) {
            throw new AssertionError();
          }
          String endSlashPath = pagePath.substring(0, lastSlash + 1);
          PageRef indexJspxPageRef = new PageRef(pageBook, endSlashPath + "index.jspx");
          if (servletContextCache.getResource(indexJspxPageRef.getServletPath()) != null) {
            page.addParentRef(new ParentRef(indexJspxPageRef, null));
          } else {
            PageRef indexJspPageRef = new PageRef(pageBook, endSlashPath + "index.jsp");
            if (servletContextCache.getResource(indexJspPageRef.getServletPath()) != null) {
              page.addParentRef(new ParentRef(indexJspPageRef, null));
            } else {
              page.addParentRef(new ParentRef(new PageRef(pageBook, endSlashPath), null));
            }
          }
        }
      }
    }
  }
}
