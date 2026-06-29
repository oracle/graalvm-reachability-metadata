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
package org.apache.jackrabbit.webdav;

import org.apache.jackrabbit.commons.xml.SerializingContentHandler;
import org.apache.jackrabbit.webdav.header.CodedUrlHeader;
import org.apache.jackrabbit.webdav.header.Header;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.observation.EventDiscovery;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.Subscription;
import org.apache.jackrabbit.webdav.observation.SubscriptionDiscovery;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * WebdavResponseImpl implements the <code>WebdavResponse</code> interface.
 */
public class WebdavResponseImpl implements WebdavResponse {

    private static Logger log = LoggerFactory.getLogger(WebdavResponseImpl.class);

    private HttpServletResponse httpResponse;

    /**
     * Create a new <code>WebdavResponse</code>
     *
     * @param httpResponse
     */
    public WebdavResponseImpl(HttpServletResponse httpResponse) {
        this(httpResponse, false);
    }

    /**
     * Create a new <code>WebdavResponse</code>
     *
     * @param httpResponse
     * @param noCache
     */
    public WebdavResponseImpl(HttpServletResponse httpResponse, boolean noCache) {
        this.httpResponse = httpResponse;
        if (noCache) {
            /* set cache control headers */
            addHeader("Pragma", "No-cache");  // http1.0
            addHeader("Cache-Control", "no-cache"); // http1.1
        }
    }

    /**
     * If the specifid exception provides an error condition an Xml response body
     * is sent providing more detailed information about the error. Otherwise only
     * the error code and status phrase is sent back.
     *
     * @param exception
     * @throws IOException
     * @see DavServletResponse#sendError(org.apache.jackrabbit.webdav.DavException)
     * @see #sendError(int, String)
     * @see #sendXmlResponse(XmlSerializable, int)
     */
    public void sendError(DavException exception) throws IOException {
        if (!exception.hasErrorCondition()) {
            httpResponse.sendError(exception.getErrorCode(), exception.getStatusPhrase());
        } else {
            sendXmlResponse(exception, exception.getErrorCode());
        }
    }

    /**
     * Send a multistatus response.
     *
     * @param multistatus
     * @throws IOException
     * @see DavServletResponse#sendMultiStatus(org.apache.jackrabbit.webdav.MultiStatus)
     */
    public void sendMultiStatus(MultiStatus multistatus) throws IOException {
        sendXmlResponse(multistatus, SC_MULTI_STATUS);
    }

    /**
     * Send response body for a lock request intended to create a new lock.
     *
     * @param lock
     * @throws java.io.IOException
     * @see DavServletResponse#sendLockResponse(org.apache.jackrabbit.webdav.lock.ActiveLock)
     */
    public void sendLockResponse(ActiveLock lock) throws IOException {
        CodedUrlHeader ltHeader = new CodedUrlHeader(DavConstants.HEADER_LOCK_TOKEN, lock.getToken());
        httpResponse.setHeader(ltHeader.getHeaderName(), ltHeader.getHeaderValue());

        DavPropertySet propSet = new DavPropertySet();
        propSet.add(new LockDiscovery(lock));
        sendXmlResponse(propSet, SC_OK);
    }

    /**
     * Send response body for a lock request that was intended to refresh one
     * or several locks.
     *
     * @param locks
     * @throws java.io.IOException
     * @see DavServletResponse#sendRefreshLockResponse(org.apache.jackrabbit.webdav.lock.ActiveLock[])
     */
    public void sendRefreshLockResponse(ActiveLock[] locks) throws IOException {
        DavPropertySet propSet = new DavPropertySet();
        propSet.add(new LockDiscovery(locks));
        sendXmlResponse(propSet, SC_OK);
    }

    /**
     * Send Xml response body.
     *
     * @param serializable
     * @param status
     * @throws IOException
     * @see DavServletResponse#sendXmlResponse(XmlSerializable, int)
     */
    public void sendXmlResponse(XmlSerializable serializable, int status) throws IOException {
        httpResponse.setStatus(status);

        if (serializable != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                Document doc = DomUtil.BUILDER_FACTORY.newDocumentBuilder().newDocument();
                doc.appendChild(serializable.toXml(doc));

                ContentHandler handler =
                    SerializingContentHandler.getSerializer(out);
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();
                transformer.transform(
                        new DOMSource(doc), new SAXResult(handler));

                // TODO: Should this be application/xml? See JCR-1621
                httpResponse.setContentType(
                        "text/xml; charset=" + SerializingContentHandler.ENCODING);
                httpResponse.setContentLength(out.size());
                out.writeTo(httpResponse.getOutputStream());
            } catch (ParserConfigurationException e) {
                log.error(e.getMessage());
                throw new IOException(e.getMessage());
            } catch (TransformerException e) {
                log.error(e.getMessage());
                throw new IOException(e.getMessage());
            } catch (SAXException e) {
                log.error(e.getMessage());
                throw new IOException(e.getMessage());
            }
        }
    }

    //----------------------------< ObservationDavServletResponse Interface >---
    /**
     *
     * @param subscription
     * @throws IOException
     * @see org.apache.jackrabbit.webdav.observation.ObservationDavServletResponse#sendSubscriptionResponse(org.apache.jackrabbit.webdav.observation.Subscription)
     */
    public void sendSubscriptionResponse(Subscription subscription) throws IOException {
        String id = subscription.getSubscriptionId();
        if (id != null) {
            Header h = new CodedUrlHeader(ObservationConstants.HEADER_SUBSCRIPTIONID, id);
            httpResponse.setHeader(h.getHeaderName(), h.getHeaderValue());
        }
        DavPropertySet propSet = new DavPropertySet();
        propSet.add(new SubscriptionDiscovery(subscription));
        sendXmlResponse(propSet, SC_OK);
    }

    /**
     *
     * @param eventDiscovery
     * @throws IOException
     * @see org.apache.jackrabbit.webdav.observation.ObservationDavServletResponse#sendPollResponse(org.apache.jackrabbit.webdav.observation.EventDiscovery)
     */
    public void sendPollResponse(EventDiscovery eventDiscovery) throws IOException {
        sendXmlResponse(eventDiscovery, SC_OK);
    }

    //--------------------------------------< HttpServletResponse interface >---
    public void addCookie(Cookie cookie) {
        httpResponse.addCookie(cookie);
    }

    public boolean containsHeader(String s) {
        return httpResponse.containsHeader(s);
    }

    public String encodeURL(String s) {
        return httpResponse.encodeRedirectURL(s);
    }

    public String encodeRedirectURL(String s) {
        return httpResponse.encodeRedirectURL(s);
    }

    public String encodeUrl(String s) {
        return httpResponse.encodeUrl(s);
    }

    public String encodeRedirectUrl(String s) {
        return httpResponse.encodeRedirectURL(s);
    }

    public void sendError(int i, String s) throws IOException {
        httpResponse.sendError(i, s);
    }

    public void sendError(int i) throws IOException {
        httpResponse.sendError(i);
    }

    public void sendRedirect(String s) throws IOException {
        httpResponse.sendRedirect(s);
    }

    public void setDateHeader(String s, long l) {
        httpResponse.setDateHeader(s, l);
    }

    public void addDateHeader(String s, long l) {
        httpResponse.addDateHeader(s, l);
    }

    public void setHeader(String s, String s1) {
        httpResponse.setHeader(s, s1);
    }

    public void addHeader(String s, String s1) {
        httpResponse.addHeader(s, s1);
    }

    public void setIntHeader(String s, int i) {
        httpResponse.setIntHeader(s, i);
    }

    public void addIntHeader(String s, int i) {
        httpResponse.addIntHeader(s, i);
    }

    public void setStatus(int i) {
        httpResponse.setStatus(i);
    }

    public void setStatus(int i, String s) {
        httpResponse.setStatus(i, s);
    }

    public String getCharacterEncoding() {
        return httpResponse.getCharacterEncoding();
    }

    public ServletOutputStream getOutputStream() throws IOException {
        return httpResponse.getOutputStream();
    }

    public PrintWriter getWriter() throws IOException {
        return httpResponse.getWriter();
    }

    public void setContentLength(int i) {
        httpResponse.setContentLength(i);
    }

    public void setContentType(String s) {
        httpResponse.setContentType(s);
    }

    public void setBufferSize(int i) {
        httpResponse.setBufferSize(i);
    }

    public int getBufferSize() {
        return httpResponse.getBufferSize();
    }

    public void flushBuffer() throws IOException {
        httpResponse.flushBuffer();
    }

    public void resetBuffer() {
        httpResponse.resetBuffer();
    }

    public boolean isCommitted() {
        return httpResponse.isCommitted();
    }

    public void reset() {
        httpResponse.reset();
    }

    public void setLocale(Locale locale) {
        httpResponse.setLocale(locale);
    }

    public Locale getLocale() {
        return httpResponse.getLocale();
    }
}
