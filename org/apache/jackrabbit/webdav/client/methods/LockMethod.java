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
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.header.IfHeader;
import org.apache.jackrabbit.webdav.header.TimeoutHeader;
import org.apache.jackrabbit.webdav.header.CodedUrlHeader;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;

/**
 * <code>LockMethod</code>...
 */
public class LockMethod extends DavMethodBase {

    private static Logger log = LoggerFactory.getLogger(LockMethod.class);

    private final boolean isRefresh;
    private LockDiscovery lockDiscovery;

    /**
     * Creates a new <code>LockMethod</code>.
     *
     * @param uri
     * @param lockScope
     * @param lockType
     * @param owner
     * @param timeout
     * @param isDeep
     */
    public LockMethod(String uri, Scope lockScope, Type lockType, String owner,
                      long timeout, boolean isDeep) throws IOException {
        this(uri, new LockInfo(lockScope, lockType, owner, timeout, isDeep));
    }

    /**
     * Creates a new <code>LockMethod</code>.
     *
     * @param uri
     * @param lockInfo
     */
    public LockMethod(String uri, LockInfo lockInfo) throws IOException {
        super(uri);
        if (lockInfo != null && !lockInfo.isRefreshLock()) {
            TimeoutHeader th = new TimeoutHeader(lockInfo.getTimeout());
            setRequestHeader(th);
            DepthHeader dh = new DepthHeader(lockInfo.isDeep());
            setRequestHeader(dh);
            setRequestBody(lockInfo);
            isRefresh = false;
        } else {
            throw new IllegalArgumentException("Cannot create a LOCK request without lock info. Use the constructor taking lock tokens in order to build a LOCK request for refresh.");
        }
    }

    /**
     * Create a new Lock method used to 'REFRESH' an existing lock.
     *
     * @param uri
     * @param timeout
     * @param lockTokens used to build the untagged If header.
     * @see IfHeader
     */
    public LockMethod(String uri, long timeout, String[] lockTokens) {
        super(uri);
        TimeoutHeader th = new TimeoutHeader(timeout);
        setRequestHeader(th);
        IfHeader ifh = new IfHeader(lockTokens);
        setRequestHeader(ifh);
        isRefresh = true;
    }

    /**
     *
     * @return
     * @throws IOException
     * @throws DavException
     */
    public LockDiscovery getResponseAsLockDiscovery() throws IOException, DavException {
        checkUsed();
        // lockDiscovery has been build while processing the response body.
        // if its still null, this indicates that either the method failed
        // or that the response body could not be parsed.
        // in either case this is an error and will be reported to the caller.
        if (lockDiscovery != null) {
            return lockDiscovery;
        } else {
            DavException dx = getResponseException();
            if (dx != null) {
                throw dx;
            } else {
                throw new DavException(getStatusCode(), getName() + " resulted with unexpected status: " + getStatusLine());
            }
        }
    }

    /**
     *
     * @return
     */
    public String getLockToken() {
        checkUsed();
        Header ltHeader = getResponseHeader(DavConstants.HEADER_LOCK_TOKEN);
        if (ltHeader != null) {
            CodedUrlHeader cuh = new CodedUrlHeader(DavConstants.HEADER_LOCK_TOKEN, ltHeader.getValue());
            return cuh.getCodedUrl();
        } else {
            // not Lock-Token header must be sent in response to a 'refresh'.
            // see the validation performed while processing the response.
            return null;
        }
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return DavMethods.METHOD_LOCK;
    }

    //----------------------------------------------------------< DavMethod >---
    /**
     * @return true, if the status code indicates success and if the response
     * contains a Lock-Token header for a request used to create a new lock.
     */
    public boolean succeeded() {
        checkUsed();
        String lt = getLockToken();
        boolean containsRequiredHeader = (isRefresh) ? lt == null : lt != null;
        return getSuccess() && containsRequiredHeader;
    }
    //------------------------------------------------------< DavMethodBase >---
    /**
     *
     * @param statusCode
     * @return true if status code is {@link DavServletResponse#SC_OK 200 (OK)}.
     */
    protected boolean isSuccess(int statusCode) {
        return statusCode == DavServletResponse.SC_OK;
    }

    //-----------------------------------------------------< HttpMethodBase >---
    /**
     * Retrieves the DAV:lockdiscovery property present in the response body
     * and builds 'ActiveLock' objects from the corresponding DAV:activelock
     * child elements inside the lock discovery. If parsing the response body
     * fails for whatever reason or if the DAV:lockdiscovery did not contain
     * at least a single DAV:activelock entry (the one created by the LOCK
     * call) this methods in addition resets the 'success' flag to false.
     *
     * @param httpState
     * @param httpConnection
     * @see HttpMethodBase#processResponseBody(HttpState, HttpConnection)
     */
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        // in case of successful response code -> parse xml body into lockDiscovery.
        if (getSuccess()) {
            try {
                setSuccess(buildDiscoveryFromRoot(getRootElement()));
            } catch (IOException e) {
                log.error("Error while parsing multistatus response: " + e);
                setSuccess(false);
            }
        }
    }

    //------------------------------------------------------------< private >---
    /**
     * Builds a new <code>LockDiscovery</code> object from the root XML
     * present in the response body and returns true if the object could be
     * created successfully.
     *
     * @param root
     * @return if a <code>LockDiscovery</code> object could be created from the
     * given XML, false otherwise.
     */
    private boolean buildDiscoveryFromRoot(Element root) {
        if (DomUtil.matches(root, XML_PROP, NAMESPACE) && DomUtil.hasChildElement(root, PROPERTY_LOCKDISCOVERY, NAMESPACE)) {
            Element lde = DomUtil.getChildElement(root, PROPERTY_LOCKDISCOVERY, NAMESPACE);
            if (DomUtil.hasChildElement(lde, XML_ACTIVELOCK, NAMESPACE)) {
                lockDiscovery = LockDiscovery.createFromXml(lde);
                return true;
            } else {
                log.debug("The DAV:lockdiscovery must contain a least a single DAV:activelock in response to a successful LOCK request.");
            }
        } else {
            log.debug("Missing DAV:prop response body in LOCK method.");
        }
        return false;
    }
}