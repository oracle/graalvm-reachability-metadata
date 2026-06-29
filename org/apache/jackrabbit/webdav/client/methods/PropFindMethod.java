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
package org.apache.jackrabbit.webdav.client.methods;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * <code>PropFindMethod</code>, as specified in
 * <a href="http://www.webdav.org/specs/rfc4918.html#rfc.section.9.1">RFC 4918, Section 9.1</a>
 * <p>
 * Supported types:
 * <ul>
 *   <li>{@link DavConstants#PROPFIND_ALL_PROP}: all custom properties, 
 *   plus the live properties defined in RFC2518/RFC4918
 *   <li>{@link DavConstants#PROPFIND_ALL_PROP_INCLUDE}: same as 
 *   {@link DavConstants#PROPFIND_ALL_PROP} plus the properties specified
 *   in <code>propNameSet</code>
 *   <li>{@link DavConstants#PROPFIND_BY_PROPERTY}: just the properties
 *   specified in <code>propNameSet</code>
 *   <li>{@link DavConstants#PROPFIND_PROPERTY_NAMES}: just the property names
 * </ul>
 * <p>
 * Note: only WebDAV level 3 servers support {@link DavConstants#PROPFIND_ALL_PROP_INCLUDE},
 * older servers will ignore the extension and act as if {@link DavConstants#PROPFIND_ALL_PROP}
 * was used.
 */
public class PropFindMethod extends DavMethodBase {

    private static Logger log = LoggerFactory.getLogger(PropFindMethod.class);

    public PropFindMethod(String uri) throws IOException {
        this(uri, PROPFIND_ALL_PROP, new DavPropertyNameSet(), DEPTH_INFINITY);
    }

    public PropFindMethod(String uri, DavPropertyNameSet propNameSet, int depth)
        throws IOException {
        this(uri, PROPFIND_BY_PROPERTY, propNameSet, depth);
    }

    public PropFindMethod(String uri, int propfindType, int depth)
        throws IOException {
        this(uri, propfindType, new DavPropertyNameSet(), depth);
    }

    public PropFindMethod(String uri, int propfindType, DavPropertyNameSet propNameSet,
                           int depth) throws IOException {
        super(uri);

        DepthHeader dh = new DepthHeader(depth);
        setRequestHeader(dh.getHeaderName(), dh.getHeaderValue());

        // build the request body
        try {
            // create the document and attach the root element
            Document document = BUILDER_FACTORY.newDocumentBuilder().newDocument();
            Element propfind = DomUtil.createElement(document, XML_PROPFIND, NAMESPACE);
            document.appendChild(propfind);

            // fill the propfind element
            switch (propfindType) {
                case PROPFIND_ALL_PROP:
                    propfind.appendChild(DomUtil.createElement(document, XML_ALLPROP, NAMESPACE));
                    break;
                    
                case PROPFIND_PROPERTY_NAMES:
                    propfind.appendChild(DomUtil.createElement(document, XML_PROPNAME, NAMESPACE));
                    break;
                    
                case PROPFIND_BY_PROPERTY:
                    if (propNameSet == null) {
                        // name set missing, ask for a property that is known to exist
                        Element prop = DomUtil.createElement(document, XML_PROP, NAMESPACE);
                        Element resourcetype = DomUtil.createElement(document, PROPERTY_RESOURCETYPE, NAMESPACE);
                        prop.appendChild(resourcetype);
                        propfind.appendChild(prop);
                    } else {
                        propfind.appendChild(propNameSet.toXml(document));
                    }
                    break;
                    
                case PROPFIND_ALL_PROP_INCLUDE:
                    propfind.appendChild(DomUtil.createElement(document, XML_ALLPROP, NAMESPACE));
                    if (propNameSet != null && ! propNameSet.isEmpty()) {
                        Element include = DomUtil.createElement(document, XML_INCLUDE, NAMESPACE);
                        Element prop = propNameSet.toXml(document);
                        for (Node c = prop.getFirstChild(); c != null; c = c.getNextSibling()) {
                            // copy over the children of <prop> to <include> element
                            include.appendChild(c.cloneNode(true));
                        }
                        propfind.appendChild(include);
                    }
                    break;
                  
               default:
                   throw new IllegalArgumentException("unknown propfind type");
            }

            // set the request body
            setRequestBody(document);
        } catch (ParserConfigurationException e) {
            throw new IOException(e.getMessage());
        }
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return DavMethods.METHOD_PROPFIND;
    }

    //------------------------------------------------------< DavMethodBase >---
    /**
     *
     * @param statusCode
     * @return true if status code is {@link DavServletResponse#SC_MULTI_STATUS 207 (Multi-Status)}.
     */
    protected boolean isSuccess(int statusCode) {
        return statusCode == DavServletResponse.SC_MULTI_STATUS;
    }
}
