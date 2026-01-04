/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2023, 2025, 2026  AO Industries, Inc.
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

import static com.aoapps.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoapps.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoapps.lang.Strings.nullIfEmpty;
import static com.aoapps.servlet.el.ElUtils.resolveValue;

import com.aoapps.html.any.AnyA;
import com.aoapps.html.any.AnyA_c;
import com.aoapps.html.any.AnySPAN;
import com.aoapps.html.any.AnySPAN_c;
import com.aoapps.html.any.AnyUnion_Palpable_Phrasing;
import com.aoapps.net.URIEncoder;
import com.aoapps.net.URIParameters;
import com.aoapps.servlet.http.HttpServletUtil;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.CurrentPage;
import com.semanticcms.core.servlet.PageIndex;
import com.semanticcms.core.servlet.PageRefResolver;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import jakarta.el.ELContext;
import jakarta.el.ValueExpression;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.SkipPageException;
import java.io.IOException;

public final class LinkImpl {

  /** Make no instances. */
  private LinkImpl() {
    throw new AssertionError();
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  @FunctionalInterface
  public static interface LinkImplBody<Ex extends Throwable> {
    void doBody(boolean discard) throws Ex, IOException, SkipPageException;
  }

  /**
   * Writes a broken path reference as "¿/book/path{#targetId}?", no encoding.
   */
  // TODO: Encoder variants
  public static void writeBrokenPath(PageRef pageRef, String targetId, Appendable out) throws IOException {
    out.append('¿');
    out.append(pageRef.getServletPath());
    if (targetId != null) {
      out.append('#').append(targetId);
    }
    out.append('?');
  }

  /**
   * Writes a broken path reference as "¿/book/path?", no encoding.
   */
  public static void writeBrokenPath(PageRef pageRef, Appendable out) throws IOException {
    writeBrokenPath(pageRef, null, out);
  }

  public static String getBrokenPath(PageRef pageRef, String targetId) {
    int sbLen =
        1 // '¿'
            + pageRef.getServletPath().length();
    if (targetId != null) {
      sbLen +=
          1 // '#'
              + targetId.length();
    }
    sbLen++; // '?'
    StringBuilder sb = new StringBuilder(sbLen);
    try {
      writeBrokenPath(pageRef, targetId, sb);
    } catch (IOException e) {
      throw new AssertionError("Should not happen on StringBuilder", e);
    }
    assert sb.length() == sbLen;
    return sb.toString();
  }

  public static String getBrokenPath(PageRef pageRef) {
    return getBrokenPath(pageRef, null);
  }

  /**
   * Writes a broken path reference as "¿/book/path{#targetId}?", encoding for XHTML.
   */
  // TODO: Convert to a single Encoder variant
  public static void writeBrokenPathInXhtml(PageRef pageRef, String targetId, Appendable out) throws IOException {
    out.append('¿');
    encodeTextInXhtml(pageRef.getServletPath(), out);
    if (targetId != null) {
      out.append('#');
      encodeTextInXhtml(targetId, out);
    }
    out.append('?');
  }

  /**
   * Writes a broken path reference as "¿/book/path?", encoding for XHTML.
   */
  public static void writeBrokenPathInXhtml(PageRef pageRef, Appendable out) throws IOException {
    writeBrokenPathInXhtml(pageRef, null, out);
  }

  /**
   * Writes a broken path reference as "¿/book/path?", encoding for XML attribute.
   */
  public static void writeBrokenPathInXhtmlAttribute(PageRef pageRef, Appendable out) throws IOException {
    out.append('¿');
    encodeTextInXhtmlAttribute(pageRef.getServletPath(), out);
    out.append('?');
  }

  /**
   * Writes an href attribute with parameters.
   * Adds contextPath to URLs that begin with a slash (/).
   * Encodes the URL.
   */
  public static void writeHref(
      HttpServletRequest request,
      HttpServletResponse response,
      Appendable out,
      String href,
      URIParameters params,
      boolean absolute,
      boolean canonical
  ) throws ServletException, IOException {
    if (href != null) {
      out.append(" href=\"");
      encodeTextInXhtmlAttribute(
          HttpServletUtil.buildURL(
              request,
              response,
              href,
              params,
              absolute,
              canonical
          ),
          out
      );
      out.append('"');
    } else {
      if (params != null) {
        throw new ServletException("parameters provided without href");
      }
    }
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  public static <Ex extends Throwable> void writeLinkImpl(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      AnyUnion_Palpable_Phrasing<?, ?> content,
      String book,
      String page,
      String element,
      boolean allowGeneratedElement,
      String anchor,
      String viewName,
      boolean small,
      URIParameters params,
      boolean absolute,
      boolean canonical,
      Object clazz,
      LinkImplBody<Ex> body
  ) throws Ex, ServletException, IOException, SkipPageException {
    // Get the current capture state
    final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
    if (captureLevel.compareTo(CaptureLevel.META) >= 0) {
      writeLinkImpl(
          servletContext,
          request,
          response,
          content,
          book,
          page,
          element,
          allowGeneratedElement,
          anchor,
          viewName,
          small,
          params,
          absolute,
          canonical,
          clazz,
          body,
          captureLevel
      );
    }
  }

  /**
   * @param  <Ex>  An arbitrary exception type that may be thrown
   * @param book  ValueExpression that returns String, evaluated at {@link CaptureLevel#META} or higher
   * @param page  ValueExpression that returns String, evaluated at {@link CaptureLevel#META} or higher
   * @param element  ValueExpression that returns String, evaluated at {@link CaptureLevel#BODY} only.
   *                 Conflicts with {@code anchor}.
   * @param anchor  ValueExpression that returns String, evaluated at {@link CaptureLevel#BODY} only.
   *                Conflicts with {@code element}.
   * @param viewName   ValueExpression that returns String, evaluated at {@link CaptureLevel#BODY} only
   * @param clazz  ValueExpression that returns Object, evaluated at {@link CaptureLevel#BODY} only
   */
  public static <Ex extends Throwable> void writeLinkImpl(
      ServletContext servletContext,
      ELContext elContext,
      HttpServletRequest request,
      HttpServletResponse response,
      AnyUnion_Palpable_Phrasing<?, ?> content,
      ValueExpression book,
      ValueExpression page,
      ValueExpression element,
      boolean allowGeneratedElement,
      ValueExpression anchor,
      ValueExpression viewName,
      boolean small,
      URIParameters params,
      boolean absolute,
      boolean canonical,
      ValueExpression clazz,
      LinkImplBody<Ex> body
  ) throws Ex, ServletException, IOException, SkipPageException {
    // Get the current capture state
    final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
    if (captureLevel.compareTo(CaptureLevel.META) >= 0) {
      // Evaluate expressions
      String bookStr = resolveValue(book, String.class, elContext);
      String pageStr = resolveValue(page, String.class, elContext);
      String elementStr;
      String anchorStr;
      String viewNameStr;
      Object clazzObj;
      if (captureLevel == CaptureLevel.BODY) {
        elementStr = resolveValue(element, String.class, elContext);
        anchorStr = resolveValue(anchor, String.class, elContext);
        viewNameStr = resolveValue(viewName, String.class, elContext);
        clazzObj = resolveValue(clazz, Object.class, elContext);
      } else {
        elementStr = null;
        anchorStr = null;
        viewNameStr = null;
        clazzObj = null;
      }
      writeLinkImpl(
          servletContext,
          request,
          response,
          content,
          bookStr,
          pageStr,
          elementStr,
          allowGeneratedElement,
          anchorStr,
          viewNameStr,
          small,
          params,
          absolute,
          canonical,
          clazzObj,
          body,
          captureLevel
      );
    }
  }

  /**
   * @param  content  {@link AnyUnion_Palpable_Phrasing} provides both {@link AnyA} and {@link AnySPAN}.
   * @param  <Ex>  An arbitrary exception type that may be thrown
   */
  private static <Ex extends Throwable> void writeLinkImpl(
      ServletContext servletContext,
      HttpServletRequest request,
      HttpServletResponse response,
      AnyUnion_Palpable_Phrasing<?, ?> content,
      String book,
      String page,
      String element,
      boolean allowGeneratedElement,
      String anchor,
      String viewName,
      boolean small,
      URIParameters params,
      boolean absolute,
      boolean canonical,
      Object clazz,
      LinkImplBody<Ex> body,
      CaptureLevel captureLevel
  ) throws Ex, ServletException, IOException, SkipPageException {
    assert captureLevel.compareTo(CaptureLevel.META) >= 0;

    book = nullIfEmpty(book);
    page = nullIfEmpty(page);

    final Node currentNode = CurrentNode.getCurrentNode(request);
    final Page currentPage = CurrentPage.getCurrentPage(request);

    // Use current page when page not set
    final PageRef targetPageRef;
    if (page == null) {
      if (book != null) {
        throw new ServletException("page must be provided when book is provided.");
      }
      if (currentPage == null) {
        throw new ServletException("link must be nested in page when page attribute not set.");
      }
      targetPageRef = currentPage.getPageRef();
    } else {
      targetPageRef = PageRefResolver.getPageRef(servletContext, request, book, page);
    }
    // Add page links
    if (currentNode != null) {
      currentNode.addPageLink(targetPageRef);
    }
    if (captureLevel == CaptureLevel.BODY) {
      element = nullIfEmpty(element);
      anchor = nullIfEmpty(anchor);
      if (element != null && anchor != null) {
        throw new ServletException("May not provide both \"element\" and \"anchor\": element=\"" + element + "\", anchor=\"" + anchor + '"');
      }
      viewName = nullIfEmpty(viewName);
      // Evaluate expressions
      if (viewName == null) {
        viewName = SemanticCMS.DEFAULT_VIEW_NAME;
      }

      // Find the view
      final SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
      final View view = semanticCms.getViewsByName().get(viewName);
      if (view == null) {
        throw new ServletException("View not found: " + viewName);
      }
      final boolean isDefaultView = view.isDefault();

      // Capture the page
      Page targetPage;
      if (targetPageRef.getBook() == null) {
        targetPage = null;
      } else if (
          // Short-cut for element already added above within current page
          currentPage != null
              && targetPageRef.equals(currentPage.getPageRef())
              && (
              element == null
                  || currentPage.getElementsById().containsKey(element)
            )
      ) {
        targetPage = currentPage;
      } else {
        // Capture required, even if capturing self
        targetPage = CapturePage.capturePage(
            servletContext,
            request,
            response,
            targetPageRef,
            element == null ? CaptureLevel.PAGE : CaptureLevel.META
        );
      }

      // Find the element
      Element targetElement;
      if (element != null && targetPage != null) {
        targetElement = targetPage.getElementsById().get(element);
        if (targetElement == null) {
          throw new ServletException("Element not found in target page: " + element);
        }
        if (!allowGeneratedElement && targetPage.getGeneratedIds().contains(element)) {
          throw new ServletException("Not allowed to link to a generated element id, set an explicit id on the target element: " + element);
        }
        if (targetElement.isHidden()) {
          throw new ServletException("Not allowed to link to a hidden element: " + element);
        }
      } else {
        targetElement = null;
      }

      // Write a link to the page

      PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
      Integer index = pageIndex == null ? null : pageIndex.getPageIndex(targetPageRef);

      StringBuilder href = new StringBuilder();
      if (element == null) {
        if (anchor == null) {
          // Link to page
          if (index != null && isDefaultView) {
            href.append('#');
            URIEncoder.encodeURIComponent(PageIndex.getRefId(index, null), href);
          } else {
            targetPageRef.appendServletPath(href);
            if (!isDefaultView) {
              boolean hasQuestion = href.lastIndexOf("?") != -1;
              href.append(hasQuestion ? "&view=" : "?view=");
              URIEncoder.encodeURIComponent(viewName, href);
            }
          }
        } else {
          // Link to anchor in page
          if (index != null && isDefaultView) {
            // Link to target in indexed page (view=all mode)
            href.append('#');
            URIEncoder.encodeURIComponent(PageIndex.getRefId(index, anchor), href);
          } else if (!absolute && currentPage != null && currentPage.equals(targetPage) && isDefaultView) {
            // Link to target on same page
            href.append('#');
            URIEncoder.encodeURIComponent(anchor, href);
          } else {
            // Link to target on different page (or same page, absolute or different view)
            targetPageRef.appendServletPath(href);
            if (!isDefaultView) {
              boolean hasQuestion = href.lastIndexOf("?") != -1;
              href.append(hasQuestion ? "&view=" : "?view=");
              URIEncoder.encodeURIComponent(viewName, href);
            }
            href.append('#');
            URIEncoder.encodeURIComponent(anchor, href);
          }
        }
      } else {
        if (index != null && isDefaultView) {
          // Link to target in indexed page (view=all mode)
          href.append('#');
          URIEncoder.encodeURIComponent(PageIndex.getRefId(index, element), href);
        } else if (!absolute && currentPage != null && currentPage.equals(targetPage) && isDefaultView) {
          // Link to target on same page
          href.append('#');
          URIEncoder.encodeURIComponent(element, href);
        } else {
          // Link to target on different page (or same page, absolute or different view)
          targetPageRef.appendServletPath(href);
          if (!isDefaultView) {
            boolean hasQuestion = href.lastIndexOf("?") != -1;
            href.append(hasQuestion ? "&view=" : "?view=");
            URIEncoder.encodeURIComponent(viewName, href);
          }
          href.append('#');
          URIEncoder.encodeURIComponent(element, href);
        }
      }
      // Add nofollow consistent with view and page settings.
      boolean nofollow = targetPage != null && !view.getAllowRobots(servletContext, request, response, targetPage);

      final String element_ = element;
      if (small) {
        AnySPAN<?, ?, ?, ?, ?> span = content.span();
        if (clazz != null) {
          span.clazz(clazz);
        } else {
          if (targetElement != null) {
            span.clazz(semanticCms.getLinkCssClass(targetElement));
          }
        }
        try (AnySPAN_c<?, ?, ?> span__ = span._c()) {
          if (body == null) {
            if (targetElement != null) {
              span__.text(targetElement);
            } else if (targetPage != null) {
              span__.text(targetPage.getTitle());
            } else {
              span__.text(text -> writeBrokenPath(targetPageRef, element_, text));
            }
            if (index != null) {
              span__.sup__any(sup -> sup
                  .text('[').text(index + 1).text(']')
              );
            }
          } else {
            body.doBody(false);
          }
          span__.sup__any(sup -> sup
              .a()
              .href(
                  HttpServletUtil.buildURL(
                      request,
                      response,
                      href.toString(),
                      params,
                      absolute,
                      canonical
                  )
              )
              .rel(nofollow ? AnyA.Rel.NOFOLLOW : null)
              .__(
                  // TODO: Make [link] not copied during select/copy/paste, to not corrupt semantic meaning (and make more useful in copy/pasted code and scripts)?
                  // TODO: https://stackoverflow.com/questions/3271231/how-to-exclude-portions-of-text-when-copying
                  "[link]"
              )
          );
        }
      } else {
        AnyA<?, ? extends AnyUnion_Palpable_Phrasing<?, ?>, ?, ?> a = content.a(
            HttpServletUtil.buildURL(
                request,
                response,
                href.toString(),
                params,
                absolute,
                canonical
            )
        );
        if (clazz != null) {
          a.clazz(clazz);
        } else {
          if (targetElement != null) {
            a.clazz(semanticCms.getLinkCssClass(targetElement));
          }
        }
        if (nofollow) {
          a.rel(AnyA.Rel.NOFOLLOW);
        }
        try (AnyA_c<?, ? extends AnyUnion_Palpable_Phrasing<?, ?>, ?> a_c = a._c()) {
          if (body == null) {
            if (targetElement != null) {
              a_c.pc().text(targetElement);
            } else if (targetPage != null) {
              a_c.pc().text(targetPage.getTitle());
            } else {
              a_c.pc().text(text -> writeBrokenPath(targetPageRef, element_, text));
            }
            if (index != null) {
              a_c.pc().sup__any(sup -> sup
                  .text('[').text(index + 1).text(']')
              );
            }
          } else {
            body.doBody(false);
          }
        }
      }
    } else {
      // Invoke body for any meta data, but discard any output
      if (body != null) {
        body.doBody(true);
      }
    }
  }
}
