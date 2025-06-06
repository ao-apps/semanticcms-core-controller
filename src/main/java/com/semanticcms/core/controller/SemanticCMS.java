/*
 * semanticcms-core-controller - Serves SemanticCMS content from a Servlet environment.
 * Copyright (C) 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025  AO Industries, Inc.
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

import com.aoapps.hodgepodge.util.Tuple2;
import com.aoapps.lang.Strings;
import com.aoapps.lang.exception.WrappedException;
import com.aoapps.lang.validation.ValidationException;
import com.aoapps.lang.xml.XmlUtils;
import com.aoapps.net.DomainName;
import com.aoapps.net.Path;
import com.aoapps.servlet.PropertiesUtils;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.servlet.http.Dispatcher;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.renderer.Renderer;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The SemanticCMS application context.
 *
 * <p>TODO: Consider custom EL resolver for this variable.
 * http://stackoverflow.com/questions/5016965/how-to-add-a-custom-variableresolver-in-pure-jsp</p>
 */
public class SemanticCMS {

  // <editor-fold defaultstate="collapsed" desc="Singleton Instance (per application)">

  /**
   * Exposes the application context as an application-scope {@link SemanticCMS} instance named
   * "{@link #APPLICATION_ATTRIBUTE_NAME}".
   */
  @WebListener("Exposes the application context as an application-scope SemanticCMS instance named \"" + APPLICATION_ATTRIBUTE_NAME + "\".")
  public static class Initializer implements ServletContextListener {

    private SemanticCMS instance;

    @Override
    public void contextInitialized(ServletContextEvent event) {
      instance = getInstance(event.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
      if (instance != null) {
        instance.destroy();
        instance = null;
      }
      APPLICATION_ATTRIBUTE.context(event.getServletContext()).remove();
    }
  }

  private static final String APPLICATION_ATTRIBUTE_NAME = "semanticCms";

  public static final ScopeEE.Application.Attribute<SemanticCMS> APPLICATION_ATTRIBUTE =
      ScopeEE.APPLICATION.attribute(APPLICATION_ATTRIBUTE_NAME);

  /**
   * Gets the SemanticCMS instance, creating it if necessary.
   */
  public static SemanticCMS getInstance(ServletContext servletContext) {
    return APPLICATION_ATTRIBUTE.context(servletContext).computeIfAbsent(name -> {
      try {
        // TODO: Support custom implementations via context-param?
        return new SemanticCMS(servletContext);
      } catch (IOException | SAXException | ParserConfigurationException | ValidationException e) {
        throw new WrappedException(e);
      }
    });
  }

  private final ServletContext servletContext;

  protected SemanticCMS(ServletContext servletContext) throws IOException, SAXException, ParserConfigurationException, ValidationException {
    this.servletContext = servletContext;
    this.demoMode = Boolean.parseBoolean(servletContext.getInitParameter(DEMO_MODE_INIT_PARAM));
    int numProcessors = Runtime.getRuntime().availableProcessors();
    this.concurrentSubrequests =
        numProcessors > 1
            && Boolean.parseBoolean(servletContext.getInitParameter(CONCURRENT_SUBREQUESTS_INIT_PARAM));
    this.rootBook = initBooks();
    this.executors = new Executors();
  }

  /**
   * Called when the context is shutting down.
   */
  protected void destroy() {
    // Do nothing
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Demo Mode">
  // TODO: Move to semanticcms-controller.xml, or somehow its own plugin?

  private static final String DEMO_MODE_INIT_PARAM = "com.semanticcms.core.controller.SemanticCMS.demoMode";

  private final boolean demoMode;

  /**
   * When {@code true}, a cursory attempt will be made to hide sensitive information for demo mode.
   * Defaults to {@code false}.
   */
  public boolean getDemoMode() {
    return demoMode;
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Books">
  // See https://docs.oracle.com/javase/tutorial/jaxp/dom/validating.html
  private static final String BOOKS_XML_RESOURCE = "/WEB-INF/books.xml";
  private static final String BOOKS_XML_SCHEMA_1_0_RESOURCE = "/com/semanticcms/core/servlet/books-1.0.xsd";
  private static final String BOOKS_XML_SCHEMA_1_1_RESOURCE = "books-1.1.xsd";

  private static final String MISSING_BOOK_TAG = "missingBook";
  private static final String BOOK_TAG = "book";
  private static final String PARENT_TAG = "parent";
  private static final String ROOT_DOMAIN_ATTRIBUTE = "rootDomain";
  private static final String ROOT_BOOK_ATTRIBUTE = "rootBook";

  private final Map<BookRef, Book> books = new LinkedHashMap<>();
  private final Map<BookRef, Book> unmodifiableBooks = Collections.unmodifiableMap(books);

  private final Map<Path, Book> publishedBooks = new LinkedHashMap<>();
  private final Map<Path, Book> unmodifiablePublishedBooks = Collections.unmodifiableMap(publishedBooks);

  private final Book rootBook;

  private Book initBooks() throws IOException, SAXException, ParserConfigurationException, ValidationException {
    Document booksXml;
    {
      InputStream schemaIn10 = SemanticCMS.class.getResourceAsStream(BOOKS_XML_SCHEMA_1_0_RESOURCE);
      if (schemaIn10 == null) {
        throw new IOException("Schema not found: " + BOOKS_XML_SCHEMA_1_0_RESOURCE);
      }
      try {
        InputStream schemaIn11 = SemanticCMS.class.getResourceAsStream(BOOKS_XML_SCHEMA_1_1_RESOURCE);
        if (schemaIn11 == null) {
          throw new IOException("Schema not found: " + BOOKS_XML_SCHEMA_1_1_RESOURCE);
        }
        try {
          DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
          try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
          } catch (ParserConfigurationException e) {
            throw new AssertionError("All implementations are required to support the javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING feature.", e);
          }
          // See https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#java
          // See https://rules.sonarsource.com/java/RSPEC-2755
          dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
          dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "https"); // TODO: How can avoid this while schema included in JAR?
          dbf.setNamespaceAware(true);
          dbf.setValidating(true);
          dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", XMLConstants.W3C_XML_SCHEMA_NS_URI);
          dbf.setAttribute(
              "http://java.sun.com/xml/jaxp/properties/schemaSource",
              new InputStream[]{
                  schemaIn10,
                  schemaIn11
              }
          );
          DocumentBuilder db = dbf.newDocumentBuilder();
          InputStream booksXmlIn = servletContext.getResource(BOOKS_XML_RESOURCE).openStream();
          if (booksXmlIn == null) {
            throw new IOException(BOOKS_XML_RESOURCE + " not found");
          }
          try {
            booksXml = db.parse(booksXmlIn);
          } finally {
            booksXmlIn.close();
          }
        } finally {
          schemaIn11.close();
        }
      } finally {
        schemaIn10.close();
      }
    }
    org.w3c.dom.Element booksElem = booksXml.getDocumentElement();
    // Load missingBooks
    for (org.w3c.dom.Element missingBookElem : XmlUtils.iterableChildElementsByTagName(booksElem, MISSING_BOOK_TAG)) {
      String domainStr = missingBookElem.getAttribute("domain");
      BookRef missingBookRef = new BookRef(
          domainStr.isEmpty() ? BookRef.DEFAULT_DOMAIN : DomainName.valueOf(domainStr),
          Path.valueOf(missingBookElem.getAttribute("name"))
      );
      String publishedStr = Strings.nullIfEmpty(missingBookElem.getAttribute("published"));
      boolean published = publishedStr != null && Boolean.valueOf(publishedStr);
      MissingBook book = new MissingBook(
          missingBookRef,
          null,
          Strings.nullIfEmpty(missingBookElem.getAttribute("base"))
      );
      if (books.put(missingBookRef, book) != null) {
        throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Duplicate value for \"" + MISSING_BOOK_TAG + "\": " + missingBookRef);
      }
      if (published) {
        if (publishedBooks.put(missingBookRef.getPath(), book) != null) {
          throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Duplicate published book for \"" + MISSING_BOOK_TAG + "\": " + missingBookRef);
        }
      }
    }
    // Load books
    BookRef rootBookRef;
    {
      String rootDomainStr = booksElem.getAttribute(ROOT_DOMAIN_ATTRIBUTE);
      Path rootBookPath = Path.valueOf(
          Strings.nullIfEmpty(
              booksElem.getAttribute(ROOT_BOOK_ATTRIBUTE)
          )
      );
      if (rootBookPath == null) {
        throw new IllegalStateException(BOOKS_XML_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE + "\" not found");
      }
      rootBookRef = new BookRef(
          rootDomainStr.isEmpty() ? BookRef.DEFAULT_DOMAIN : DomainName.valueOf(rootDomainStr),
          rootBookPath
      );
    }
    for (org.w3c.dom.Element bookElem : XmlUtils.iterableChildElementsByTagName(booksElem, BOOK_TAG)) {
      BookRef bookRef;
      {
        String domainStr = bookElem.getAttribute("domain");
        bookRef = new BookRef(
            domainStr.isEmpty() ? BookRef.DEFAULT_DOMAIN : DomainName.valueOf(domainStr),
            Path.valueOf(bookElem.getAttribute("name"))
        );
      }
      Set<ParentRef> parentRefs = new LinkedHashSet<>();
      for (org.w3c.dom.Element parentElem : XmlUtils.iterableChildElementsByTagName(bookElem, PARENT_TAG)) {
        String domainStr = parentElem.getAttribute("domain");
        BookRef parentBookRef = new BookRef(
            domainStr.isEmpty() ? BookRef.DEFAULT_DOMAIN : DomainName.valueOf(domainStr),
            Path.valueOf(parentElem.getAttribute("book"))
        );
        Path parentPage = Path.valueOf(parentElem.getAttribute("page"));
        String parentShortTitle = parentElem.hasAttribute("shortTitle") ? parentElem.getAttribute("shortTitle") : null;
        Book parentBook = books.get(parentBookRef);
        if (parentBook == null) {
          throw new IllegalStateException(BOOKS_XML_RESOURCE + ": parent book not found (loading order currently matters): " + parentBookRef);
        }
        parentRefs.add(new ParentRef(new PageRef(parentBookRef, parentPage), parentShortTitle));
      }
      if (bookRef.equals(rootBookRef)) {
        if (!parentRefs.isEmpty()) {
          throw new IllegalStateException(BOOKS_XML_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE + "\" may not have any parents: " + rootBookRef);
        }
      } else {
        if (parentRefs.isEmpty()) {
          throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Non-root books must have at least one parent: " + bookRef);
        }
      }
      String publishedStr = Strings.nullIfEmpty(bookElem.getAttribute("published"));
      boolean published = publishedStr == null || Boolean.valueOf(publishedStr);
      String cvsworkDirectory = Strings.nullIfEmpty(bookElem.getAttribute("cvsworkDirectory"));
      Collection<String> resourceDirectories;
      if (cvsworkDirectory == null) {
        resourceDirectories = Collections.emptySet();
      } else {
        resourceDirectories = Collections.singleton(cvsworkDirectory);
      }
      ServletBook book = new ServletBook(
          servletContext,
          bookRef,
          resourceDirectories,
          Boolean.valueOf(bookElem.getAttribute("allowRobots")),
          parentRefs,
          PropertiesUtils.loadFromResource(
              servletContext,
              bookRef.getPrefix() + "/book.properties"
          )
      );
      if (books.put(bookRef, book) != null) {
        throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Duplicate value for \"" + BOOK_TAG + "\": " + bookRef);
      }
      if (published) {
        if (publishedBooks.put(bookRef.getPath(), book) != null) {
          throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Duplicate published book for \"" + BOOK_TAG + "\": " + bookRef);
        }
      }
    }

    // Load rootBook
    Book newRootBook = books.get(rootBookRef);
    if (newRootBook == null) {
      throw new AssertionError();
    }

    // Successful book load
    return newRootBook;
  }

  /**
   * Gets the mapping of all configured books, including those that are not
   * {@link #getPublishedBooks() published} and/or {@link Book#isAccessible() inaccessible}.
   *
   * @see  #getBook(com.semanticcms.core.model.BookRef)
   * @see  #getBook(com.aoapps.net.DomainName, com.aoapps.net.Path)
   */
  public Map<BookRef, Book> getBooks() {
    return unmodifiableBooks;
  }

  /**
   * Gets a book given its reference, including those that are not
   * {@link #getPublishedBooks() published} and/or {@link Book#isAccessible() inaccessible}.
   *
   * @throws  NoSuchElementException  when book not found
   *
   * @see  #getBooks()
   */
  public Book getBook(BookRef bookRef) throws NoSuchElementException {
    Book book = books.get(bookRef);
    if (book == null) {
      throw new NoSuchElementException("Book not found: " + bookRef);
    }
    return book;
  }

  /**
   * Gets a book given its domain and name, including those that are not
   * {@link #getPublishedBooks() published} and/or {@link Book#isAccessible() inaccessible}.
   *
   * @throws  NoSuchElementException  when book not found
   *
   * @see  #getBook(com.semanticcms.core.model.BookRef)
   */
  public Book getBook(DomainName domain, Path path) throws NoSuchElementException {
    return getBook(new BookRef(domain, path));
  }

  /**
   * A published book is one that is served by the local web application.
   * Its content may or may not be generated locally.
   *
   * <p>Its content may also be inaccessible/missing, which can serve as a placeholder
   * for future content or allow for maintenance of parts of a site.</p>
   *
   * <p>There may exist books that are generated locally, but not published.
   * In this configuration, the local book is used for captures, while links
   * may be optionally generated to an external base.  This is one option to
   * integrate content between sites while avoiding real-time cross-server communication,
   * but at the cost of the two versions may get out of sync or be deployed separately.
   * Consider using with autogit modules.</p>
   *
   * @see  #getPublishedBook(java.lang.String)
   * @see  #getPublishedBook(javax.servlet.http.HttpServletRequest)
   */
  public Map<Path, Book> getPublishedBooks() {
    return unmodifiablePublishedBooks;
  }

  /**
   * Gets the published book for the provided context-relative servlet path or {@code null} if no book published at that path.
   * The book with the longest prefix match is used, matched along segments only (along '/' boundaries).
   * For example, a book at "/api" does not contain the servlet at the path "/apidocs/index.html".
   * The servlet path must begin with a slash (/).
   *
   * <p>This does not match a book with an exact servletPath without a slash separator.  Every path within a book is
   * contained after a slash separator, including the top-most index at "/", "/index.jsp", or "/index.jspx".
   * For example, a book at "/api" does not contain the servlet at the path "/api", but does contain every servlet
   * starting with "/api/" (unless a more specific book matches).</p>
   *
   * <p>Please note the book may be {@link Book#isAccessible() inaccessible}.</p>
   *
   * <pre>TODO: Support mount point different than name?
   * TODO: How to return null at local-but-not-published books.</pre>
   *
   * @see  #getPublishedBooks()
   * @see  #getPublishedBook(javax.servlet.http.HttpServletRequest)
   */
  public Book getPublishedBook(String servletPath) {
    // TODO: Was the old iterative search through all books actually faster?  Worth benchmarking?
    try {
      final int originalLen = servletPath.length();
      int len = originalLen;
      // Quick path for initial trailing slash: avoid map lookup that will never match
      if (servletPath.charAt(len - 1) == '/') {
        len -= 1;
      }
      while (len > 0) {
        servletPath = servletPath.substring(0, len);
        assert servletPath.charAt(len - 1) != '/' : "Should not end in slash: " + servletPath;
        // Do not match the full servletPath as a book
        if (len < originalLen) {
          Book book = publishedBooks.get(Path.valueOf(servletPath));
          if (book != null) {
            return book;
          }
        }
        int lastSlash = servletPath.lastIndexOf('/');
        assert lastSlash != -1 : "Starts with slash, so should always find one when len > 0";
        len = lastSlash;
      }
      return publishedBooks.get(Path.ROOT);
    } catch (ValidationException e) {
      throw new WrappedException(e);
    }
  }

  /**
   * Gets the published book for the provided request or <code>null</code> if no book published at the current request path.
   *
   * <p>Please note the book may be {@link Book#isAccessible() inaccessible}.</p>
   *
   * @see  #getPublishedBooks()
   * @see  Dispatcher#getCurrentPagePath(javax.servlet.http.HttpServletRequest)
   * @see  #getPublishedBook(java.lang.String)
   */
  public Book getPublishedBook(HttpServletRequest request) {
    return getPublishedBook(Dispatcher.getCurrentPagePath(request));
  }

  public Book getLocalBook(String servletPath) {
    throw new NotImplementedException("TODO");
  }

  public Book getLocalBook(HttpServletRequest request) {
    return getLocalBook(Dispatcher.getCurrentPagePath(request));
  }

  /**
   * Gets the root book as configured in <code>/WEB-INF/books.xml</code>.
   *
   * <p>The root book will always be {@link Book#isAccessible() accessible}.
   * TODO: Can this be enforced in the XML Schema for books.xml?</p>
   *
   * <p>The root book may or may not be {@link #getPublishedBooks() published} by the local
   * server.</p>
   */
  public Book getRootBook() {
    assert rootBook.isAccessible();
    return rootBook;
  }

  // TODO: What to do to protect a local book that is not published?

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Concurrency">

  /**
   * Initialization parameter, that when set to "true" will enable the
   * concurrent subrequest processing feature.  This is still experimental
   * and is off by default.
   */
  private static final String CONCURRENT_SUBREQUESTS_INIT_PARAM = SemanticCMS.class.getName() + ".concurrentSubrequests";

  private final boolean concurrentSubrequests;

  /**
   * Checks if concurrent subrequests are allowed.
   */
  boolean getConcurrentSubrequests() {
    return concurrentSubrequests;
  }

  private final Executors executors;

  /**
   * A shared executor available to all components.
   *
   * <p>Consider selecting concurrent or sequential implementations based on overall system load.
   * See {@link ConcurrencyCoordinator#isConcurrentProcessingRecommended(javax.servlet.ServletRequest)}.</p>
   *
   * @see  ConcurrencyCoordinator#isConcurrentProcessingRecommended(javax.servlet.ServletRequest)
   */
  public Executors getExecutors() {
    return executors;
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Renderers">

  private final SortedMap<String, Renderer> renderers = new TreeMap<>(
      (String s1, String s2) -> {
        // Order by length descending
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 < len2) {
          return 1;
        }
        if (len1 > len2) {
          return -1;
        }
        // Then order by suffix, case-insensitive ascending
        return s1.compareToIgnoreCase(s2);
      }
  );
  private final SortedMap<String, Renderer> unmodifiableRenderers = Collections.unmodifiableSortedMap(renderers);

  /**
   * Gets the mapping of all configured renderers, key is suffix, value is renderer.
   * A renderer may exist under multiple suffixes, but only one unique renderer may be
   * associated with each suffix.
   *
   * <p>The renderers are ordered by suffix length descending, then suffix case-insensitive ascending.</p>
   *
   * @see  #getRendererAndPath(com.aoapps.net.Path)
   */
  public SortedMap<String, Renderer> getRenderers() {
    // Not synchronizing renderers where because they are normally only set on application start-up
    return unmodifiableRenderers;
  }

  private static final String END_INDEX = "/index";
  private static final int END_INDEX_LEN = END_INDEX.length();

  /**
   * Finds the renderer that matches the given servletPath.  The renderer with the longest
   * suffix match is used.  This means that any renderer with an empty suffix will always match,
   * but only when no other renderer matches.  This also means that one renderer can be more
   * specific than another, such as both ".html" and ".amp.html" being registered as separate
   * renderers.
   *
   * <p>If the path ends with "/index" after the suffix is removed, the trailing "index" is removed.
   * TODO: Don't end with "/" ever except root.</p>
   *
   * @return  The matched renderer and trimmed path, or {@code null} if none found
   */
  public Tuple2<Renderer, Path> getRendererAndPath(Path path) {
    final String pathStr = path.toString();
    String suffix = null;
    Renderer renderer = null;
    synchronized (renderers) {
      for (Map.Entry<String, Renderer> entry : renderers.entrySet()) {
        String s = entry.getKey();
        if (pathStr.endsWith(s)) {
          suffix = s;
          renderer = entry.getValue();
          break;
        }
      }
    }
    if (suffix != null) {
      // Remove suffix from path
      int pathLen = pathStr.length() - suffix.length();
      // Remove any trailing /index, too
      if (pathStr.regionMatches(pathLen - END_INDEX_LEN, END_INDEX, 0, END_INDEX_LEN)) {
        pathLen -= END_INDEX.length() - 1;
      }
      return new Tuple2<>(
          renderer,
          path.prefix(pathLen)
      );
    } else {
      return null;
    }
  }

  /**
   * Registers a new renderer.
   *
   * @throws  IllegalStateException  if a renderer is already registered with the suffix.
   */
  public void addRenderer(String suffix, Renderer renderer) throws IllegalStateException {
    synchronized (renderers) {
      if (renderers.containsKey(suffix)) {
        throw new IllegalStateException("Renderer already registered: " + suffix);
      }
      if (renderers.put(suffix, renderer) != null) {
        throw new AssertionError();
      }
    }
  }
  // </editor-fold>

  // TODO: Add /META-INF, /META-INF/***, /WEB-INF, /WEB-INF/*** as not found in global firewall space
  // TODO: Add "message" option to rules, only shown in dev mode but included in any logging
  //       message within controller flow, too
}
