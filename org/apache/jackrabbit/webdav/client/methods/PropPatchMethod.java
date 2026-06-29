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
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameIterator;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;
import java.util.Iterator;

/**
 * <code>PropPatchMethod</code>...
 */
public class PropPatchMethod extends DavMethodBase implements DavConstants {

    private static Logger log = LoggerFactory.getLogger(PropPatchMethod.class);

    private final DavPropertyNameSet propertyNames = new DavPropertyNameSet();

    private DavException responseException;

    /**
     *
     * @param uri
     * @param changeList list of DavProperty (for 'set') and DavPropertyName
     * (for 'remove') entries.
     * @throws IOException
     */
    public PropPatchMethod(String uri, List changeList) throws IOException {
        super(uri);
        if (changeList == null || changeList.isEmpty()) {
            throw new IllegalArgumentException("PROPPATCH cannot be executed without properties to be set or removed.");
        }
        try {
            Document document = BUILDER_FACTORY.newDocumentBuilder().newDocument();
            Element propUpdateElement = DomUtil.addChildElement(document, XML_PROPERTYUPDATE, NAMESPACE);

            Element propElement = null;
            boolean isSet = false;
            Iterator it = changeList.iterator();
            while (it.hasNext()) {
                Object entry = it.next();
                if (entry instanceof DavPropertyName) {
                    // DAV:remove
                    DavPropertyName removeName = (DavPropertyName)entry;
                    if (propElement == null || isSet) {
                        isSet = false;
                        propElement = getPropElement(propUpdateElement, isSet);
                    }
                    propElement.appendChild(removeName.toXml(document));
                    propertyNames.add(removeName);
                } else if (entry instanceof DavProperty) {
                    // DAV:set
                    DavProperty setProperty = (DavProperty)entry;
                    if (propElement == null || !isSet) {
                        isSet = true;
                        propElement = getPropElement(propUpdateElement, isSet);
                    }
                    propElement.appendChild(setProperty.toXml(document));
                    propertyNames.add(setProperty.getName());
                } else {
                    throw new IllegalArgumentException("ChangeList may only contain DavPropertyName and DavProperty elements.");
                }
            }
            setRequestBody(document);
        } catch (ParserConfigurationException e) {
            throw new IOException(e.getMessage());
        }
    }

    public PropPatchMethod(String uri, DavPropertySet setProperties,
                           DavPropertyNameSet removeProperties) throws IOException {
        super(uri);
        if (setProperties == null || removeProperties == null) {
            throw new IllegalArgumentException("Neither setProperties nor removeProperties must be null.");
        }
        if (setProperties.isEmpty() && removeProperties.isEmpty()) {
            throw new IllegalArgumentException("Either setProperties or removeProperties can be empty; not both of them.");
        }

        propertyNames.addAll(removeProperties);
        DavPropertyName[] setNames = setProperties.getPropertyNames();
        for (int i = 0; i < setNames.length; i++) {
            propertyNames.add(setNames[i]);
        }

        try {
            Document document = BUILDER_FACTORY.newDocumentBuilder().newDocument();
            Element propupdate = DomUtil.addChildElement(document, XML_PROPERTYUPDATE, NAMESPACE);
            // DAV:set
            if (!setProperties.isEmpty()) {
                Element set = DomUtil.addChildElement(propupdate, XML_SET, NAMESPACE);
                set.appendChild(setProperties.toXml(document));
            }
            // DAV:remove
            if (!removeProperties.isEmpty()) {
                Element remove = DomUtil.addChildElement(propupdate, XML_REMOVE, NAMESPACE);
                remove.appendChild(removeProperties.toXml(document));
            }
            setRequestBody(document);
        } catch (ParserConfigurationException e) {
            throw new IOException(e.getMessage());
        }
    }

    private Element getPropElement(Element propUpdate, boolean isSet) {
        Element updateEntry = DomUtil.addChildElement(propUpdate, (isSet) ? XML_SET : XML_REMOVE , NAMESPACE);
        return DomUtil.addChildElement(updateEntry, XML_PROP, NAMESPACE);
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return DavMethods.METHOD_PROPPATCH;
    }

    //------------------------------------------------------< DavMethodBase >---
    /**
     *
     * @param statusCode
     * @return true if status code is {@link DavServletResponse#SC_MULTI_STATUS 207 (Multi-Status)}.
     * For compliance reason {@link DavServletResponse#SC_OK 200 (OK)} is
     * interpreted as successful response as well.
     */
    protected boolean isSuccess(int statusCode) {
        return statusCode == DavServletResponse.SC_MULTI_STATUS || statusCode == DavServletResponse.SC_OK;
    }

    /**
     *
     * @param multiStatus
     * @param httpState
     * @param httpConnection
     */
    protected void processMultiStatusBody(MultiStatus multiStatus, HttpState httpState, HttpConnection httpConnection) {
        // check of OK response contains all set/remove properties
        MultiStatusResponse[] resp = multiStatus.getResponses();
        if (resp.length != 1) {
            log.warn("Expected a single multi-status response in PROPPATCH.");
        }
        boolean success = true;
        // only check the first ms-response
        for (int i = 0; i < 1; i++) {
            DavPropertyNameSet okSet = resp[i].getPropertyNames(DavServletResponse.SC_OK);
            if (okSet.isEmpty()) {
                log.debug("PROPPATCH failed: No 'OK' response found for resource " + resp[i].getHref());
                success = false;
            } else {
                DavPropertyNameIterator it = propertyNames.iterator();
                while (it.hasNext()) {
                    DavPropertyName pn = it.nextPropertyName();
                    success = okSet.remove(pn);
                }
            }
            if (!okSet.isEmpty()) {
                StringBuffer b = new StringBuffer("The following properties outside of the original request where set or removed: ");
                DavPropertyNameIterator it = okSet.iterator();
                while (it.hasNext()) {
                    b.append(it.nextPropertyName().toString()).append("; ");
                }
                log.warn(b.toString());
            }
        }
        // if  build the error message
        if (!success) {
            Status[] st = resp[0].getStatus();
            // TODO: respect multiple error reasons (not only the first one)
            for (int i = 0; i < st.length && responseException == null; i ++) {
                switch (st[i].getStatusCode()) {
                    case DavServletResponse.SC_FAILED_DEPENDENCY:
                        // ignore
                        break;
                    default:
                        responseException = new DavException(st[i].getStatusCode());
                }
            }
        }
    }

    /**
     *
     * @return
     * @throws IOException
     * @see DavMethod#getResponseException()
     */
    public DavException getResponseException() throws IOException {
        checkUsed();
        if (getSuccess()) {
            String msg = "Cannot retrieve exception from successful response.";
            log.warn(msg);
            throw new IllegalStateException(msg);
        }
        if (responseException != null) {
            return responseException;
        } else {
            return super.getResponseException();
        }
    }
}