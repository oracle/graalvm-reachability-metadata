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

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.header.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.w3c.dom.Document;

import java.io.IOException;

/**
 * <code>DavMethod</code>...
 */
public interface DavMethod extends HttpMethod {

    /**
     * Adds the specified {@link Header request header}, NOT overwriting any
     * previous value. Note that header-name matching is case insensitive.
     *
     * @param header
     * @see HttpMethod#addRequestHeader(String, String)
     */
    public void addRequestHeader(Header header);

    /**
     * Set the specified request header, overwriting any previous value. Note
     * that header-name matching is case-insensitive.
     *
     * @param header
     * @see HttpMethod#setRequestHeader(String, String)
     */
    public void setRequestHeader(Header header);

    /**
     * Parse the response body into an Xml <code>Document</code>.
     *
     * @return Xml document or <code>null</code> if the response stream is
     * <code>null</code>.
     * @throws IOException If an I/O (transport) problem occurs while obtaining
     * the response body of if the XML parsing fails.
     * @see HttpMethod#getResponseBody()
     * @see HttpMethod#getResponseBodyAsStream()
     * @see HttpMethod#getResponseBodyAsString()
     */
    public Document getResponseBodyAsDocument() throws IOException;

    /**
     * Return the response body as <code>MultiStatus</code> object.
     *
     * @return
     * @throws IOException if the response body could not be parsed
     * @throws DavException if the status code is other than MultiStatus or if
     * obtaining the response XML document fails
     * @see #getResponseBodyAsDocument()
     */
    public MultiStatus getResponseBodyAsMultiStatus() throws IOException, DavException;

    /**
     * Builds a DavException for the status line and the DAV:error element that
     * may be present in the response body. If the response does not indicate an
     * error, <code>null</code> will be returned.
     *
     * @return DavException or <code>null</code> if this method did not result
     * in an error.
     * @throws IOException
     */
    public DavException getResponseException() throws IOException;

    /**
     *
     * @throws DavException
     * @throws IOException
     */
    public void checkSuccess() throws DavException, IOException;

    /**
     *
     * @return true if the method was successfully executed
     */
    public boolean succeeded();
}