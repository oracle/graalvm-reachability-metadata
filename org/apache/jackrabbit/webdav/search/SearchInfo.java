/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.webdav.search;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Attr;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

/**
 * <code>SearchInfo</code> parses the 'searchrequest' element of a SEARCH
 * request body and performs basic validation. Both query language and the
 * query itself can be access from the resulting object.<br>
 * NOTE: The query is expected to be represented by the text contained in the
 * Xml element specifying the query language, thus the 'basicsearch' defined
 * by the Webdav Search Internet Draft is not supported by this implementation.
 * <p/>
 *
 * Example of a valid 'searchrequest' body
 * <pre>
 * &lt;d:searchrequest xmlns:d="DAV:" dcr:="http://www.day.com/jcr/webdav/1.0" &gt;
 *    &lt;dcr:xpath>//sv:node[@sv:name='myapp:paragraph'][1]&lt;/dcr:xpath&gt;
 * &lt;/d:searchrequest&gt;
 * </pre>
 *
 * Would return the following values:
 * <pre>
 *    getLanguageName() -&gt; xpath
 *    getQuery()        -&gt; //sv:node[@sv:name='myapp:paragraph'][1]
 * </pre>
 *
 */
public class SearchInfo implements SearchConstants, XmlSerializable {

    private static Logger log = LoggerFactory.getLogger(SearchInfo.class);

    public static final long NRESULTS_UNDEFINED = -1;
    public static final long OFFSET_UNDEFINED = -1;

    private static final String LIMIT = "limit";
    private static final String NRESULTS = "nresults";
    private static final String OFFSET = "offset";

    /**
     * Set of namespace uri String which are ignored in the search request.
     */
    private static final Set IGNORED_NAMESPACES;

    static {
        Set s = new HashSet();
        s.add(Namespace.XMLNS_NAMESPACE.getURI());
        s.add(Namespace.XML_NAMESPACE.getURI());
        s.add(DavConstants.NAMESPACE.getURI());
        IGNORED_NAMESPACES = Collections.unmodifiableSet(s);
    }

    private final String language;
    private final Namespace languageNamespace;
    private final String query;
    private final Map namespaces;

    private long nresults = NRESULTS_UNDEFINED;
    private long offset = OFFSET_UNDEFINED;

    /**
     * Create a new <code>SearchInfo</code> instance.
     *
     * @param language
     * @param languageNamespace
     * @param query
     * @param namespaces the re-mapped namespaces. Key=prefix, value=uri.
     */
    public SearchInfo(String language, Namespace languageNamespace, String query, Map namespaces) {
        this.language = language;
        this.languageNamespace = languageNamespace;
        this.query = query;
        this.namespaces = Collections.unmodifiableMap(new HashMap(namespaces));
    }

    /**
     * Create a new <code>SearchInfo</code> instance.
     *
     * @param language
     * @param languageNamespace
     * @param query
     */
    public SearchInfo(String language, Namespace languageNamespace, String query) {
        this(language,  languageNamespace, query, Collections.EMPTY_MAP);
    }

    /**
     * Returns the name of the query language to be used.
     *
     * @return name of the query language
     */
    public String getLanguageName() {
        return language;
    }

    /**
     * Returns the namespace of the language specified with the search request element.
     *
     * @return namespace of the requestes language.
     */
    public Namespace getLanguageNameSpace() {
        return languageNamespace;
    }

    /**
     * Return the query string.
     *
     * @return query string
     */
    public String getQuery() {
        return query;
    }

    /**
     * Returns the namespaces that have been re-mapped by the user.
     *
     * @return map of namespace to prefix mappings. Key=prefix, value=uri.
     */
    public Map getNamespaces() {
        return namespaces;
    }

    /**
     * Returns the maximal number of search results that should be returned.
     *
     * @return the maximal number of search results that should be returned.
     */
    public long getNumberResults() {
        return nresults;
    }

    /**
     * Sets the maximal number of search results that should be returned.
     *
     * @param nresults The maximal number of search results
     */
    public void setNumberResults(long nresults) {
        this.nresults = nresults;
    }

    /**
     * Returns the desired offset in the total result set.
     *
     * @return the desired offset in the total result set.
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Sets the desired offset in the total result set.
     *
     * @param offset The desired offset in the total result set.
     */
    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * Return the xml representation of this <code>SearchInfo</code> instance.
     *
     * @return xml representation
     * @param document
     */
    public Element toXml(Document document) {
        Element sRequestElem = DomUtil.createElement(document, XML_SEARCHREQUEST, NAMESPACE);
        for (Iterator it = namespaces.keySet().iterator(); it.hasNext(); ) {
            String prefix = (String) it.next();
            String uri = (String) namespaces.get(prefix);
            DomUtil.setNamespaceAttribute(sRequestElem, prefix, uri);
        }
        DomUtil.addChildElement(sRequestElem, language, languageNamespace, query);
        if (nresults != NRESULTS_UNDEFINED|| offset != OFFSET_UNDEFINED) {
            Element limitE = DomUtil.addChildElement(sRequestElem, LIMIT, NAMESPACE);
            if (nresults != NRESULTS_UNDEFINED) {
                DomUtil.addChildElement(limitE, NRESULTS, NAMESPACE, nresults + "");
            }
            if (offset != OFFSET_UNDEFINED) {
                // TODO define reasonable namespace...
                DomUtil.addChildElement(limitE, OFFSET, Namespace.EMPTY_NAMESPACE, offset + "");
            }
        }
        return sRequestElem;
    }

    /**
     * Create a new <code>SearchInfo</code> from the specifying document
     * retrieved from the request body.
     *
     * @param searchRequest
     * @throws DavException if the root element's name is other than
     * 'searchrequest' or if it does not contain a single child element specifying
     * the query language to be used.
     */
    public static SearchInfo createFromXml(Element searchRequest) throws DavException {
        if (searchRequest == null || !XML_SEARCHREQUEST.equals(searchRequest.getLocalName()))  {
            log.warn("The root element must be 'searchrequest'.");
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
        Element first = DomUtil.getFirstChildElement(searchRequest);
        Attr[] nsAttributes = DomUtil.getNamespaceAttributes(searchRequest);
        Map namespaces = new HashMap();
        for (int i = 0; i < nsAttributes.length; i++) {
            // filter out xmlns namespace and DAV namespace
            if (!IGNORED_NAMESPACES.contains(nsAttributes[i].getValue())) {
                namespaces.put(nsAttributes[i].getLocalName(), nsAttributes[i].getValue());
            }
        }
        SearchInfo sInfo;
        if (first != null) {
            sInfo = new SearchInfo(first.getLocalName(), DomUtil.getNamespace(first), DomUtil.getText(first), namespaces);
        } else {
            log.warn("A single child element is expected with the 'DAV:searchrequest'.");
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }

        Element limit = DomUtil.getChildElement(searchRequest, LIMIT, NAMESPACE);
        if (limit != null) {
            // try to get the value DAV:nresults element
            String nresults = DomUtil.getChildTextTrim(limit, NRESULTS, NAMESPACE);
            if (nresults != null) {
                try {
                    sInfo.setNumberResults(Long.valueOf(nresults));
                } catch (NumberFormatException e) {
                    log.error("DAV:nresults cannot be parsed into a long -> ignore.");
                }
            }
            // try of an offset is defined within the DAV:limit element.
            String offset = DomUtil.getChildTextTrim(limit, OFFSET, Namespace.EMPTY_NAMESPACE);
            if (offset != null) {
                try {
                    sInfo.setOffset(Long.valueOf(offset));
                } catch (NumberFormatException e) {
                    log.error("'offset' cannot be parsed into a long -> ignore.");
                }
            }
        }
        return sInfo;
    }
}