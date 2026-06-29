/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  The ASF licenses this file to You
* under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License.
* You may obtain a copy of the License at
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.header.CodedUrlHeader;
import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.header.TimeoutHeader;
import org.apache.jackrabbit.webdav.observation.SubscriptionInfo;
import org.apache.jackrabbit.webdav.observation.SubscriptionDiscovery;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.Subscription;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpConnection;
import org.w3c.dom.Element;

import java.io.IOException;

/**
 * <code>SubscribeMethod</code>...
 */
public class SubscribeMethod extends DavMethodBase implements ObservationConstants {

    private static Logger log = LoggerFactory.getLogger(SubscribeMethod.class);

    private SubscriptionDiscovery subscriptionDiscovery;

    /**
     * Create a new <code>SubscribeMethod</code> used to register to the
     * observation events specified within the given <code>SubscriptionInfo</code>.
     * See {@link #SubscribeMethod(String, SubscriptionInfo, String)} for a
     * constructor that allows to redefined an existing subscription.
     *
     * @param uri
     * @param subscriptionInfo
     * @throws IOException
     */
    public SubscribeMethod(String uri, SubscriptionInfo subscriptionInfo) throws IOException {
        this(uri, subscriptionInfo, null);
    }

    /**
     * Create a new <code>SubscribeMethod</code> used to register to the
     * observation events specified within the given <code>SubscriptionInfo</code>.
     * Note that in contrast to {@link #SubscribeMethod(String, SubscriptionInfo)}
     * this constructor optionally takes a subscription id identifying a
     * subscription made before. In this case the subscription will be modified
     * according to the definitions present in the <code>SubscriptionInfo</code>.
     * If the id is <code>null</code> this constructor is identical to
     * {@link #SubscribeMethod(String, SubscriptionInfo)}.
     *
     * @param uri
     * @param subscriptionInfo
     * @throws IOException
     */
    public SubscribeMethod(String uri, SubscriptionInfo subscriptionInfo, String subscriptionId) throws IOException {
        super(uri);
        if (subscriptionInfo == null) {
            throw new IllegalArgumentException("SubscriptionInfo must not be null.");
        }
        // optional subscriptionId (only required to modify an existing subscription)
        if (subscriptionId != null) {
           setRequestHeader(new CodedUrlHeader(HEADER_SUBSCRIPTIONID, subscriptionId));
        }
        // optional timeout header
        long to = subscriptionInfo.getTimeOut();
        if (to != DavConstants.UNDEFINED_TIMEOUT) {
            setRequestHeader(new TimeoutHeader(subscriptionInfo.getTimeOut()));
        }
        // always set depth header since value is boolean flag
        setRequestHeader(new DepthHeader(subscriptionInfo.isDeep()));
        setRequestBody(subscriptionInfo);
    }

    public SubscriptionDiscovery getResponseAsSubscriptionDiscovery() throws IOException, DavException {
        checkUsed();
        if (subscriptionDiscovery != null) {
            return subscriptionDiscovery;
        } else {
            DavException dx = getResponseException();
            if (dx != null) {
                throw dx;
            } else {
                throw new DavException(getStatusCode(), getName() + " resulted with unexpected status: " + getStatusLine());
            }
        }
    }

    public String getSubscriptionId() {
        checkUsed();
        Header sbHeader = getResponseHeader(HEADER_SUBSCRIPTIONID);
        if (sbHeader != null) {
            CodedUrlHeader cuh = new CodedUrlHeader(HEADER_SUBSCRIPTIONID, sbHeader.getValue());
            return cuh.getCodedUrl();
        }
        return null;
    }

    //---------------------------------------------------------< HttpMethod >---
    public String getName() {
        return DavMethods.METHOD_SUBSCRIBE;
    }

    //------------------------------------------------------< DavMethodBase >---
    protected boolean isSuccess(int statusCode) {
        return DavServletResponse.SC_OK == statusCode;
    }

   //------------------------------------------------------< HttpMethodBase >---
    /**
     * Retrieves the DAV:subscriptiondiscovery property present in the response body
     * and builds 'Subscription' objects from the corresponding DAV:subscription
     * child elements inside the discovery. If parsing the response body
     * fails for whatever reason or if the DAV:subscriptiondiscovery did not contain
     * at least a single DAV:subscription entry (the one created by the SUBSCRIBE
     * call) this methods in addition resets the 'success' flag to false.
     *
     * @param httpState
     * @param httpConnection
     * @see HttpMethodBase#processResponseBody(HttpState, HttpConnection)
     */
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        // in case of successful response code -> parse xml body discovery object
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
     *
     * @param root
     * @return
     */
    private boolean buildDiscoveryFromRoot(Element root) {
        if (DomUtil.matches(root, XML_PROP, DavConstants.NAMESPACE) &&
            DomUtil.hasChildElement(root, SUBSCRIPTIONDISCOVERY.getName(), SUBSCRIPTIONDISCOVERY.getNamespace())) {
            Element sdElem = DomUtil.getChildElement(root, SUBSCRIPTIONDISCOVERY.getName(), SUBSCRIPTIONDISCOVERY.getNamespace());

            SubscriptionDiscovery sd = SubscriptionDiscovery.createFromXml(sdElem);
            if (((Subscription[])sd.getValue()).length > 0) {
                subscriptionDiscovery = sd;
                return true;
            } else {
                log.debug("Missing 'subscription' elements in SUBSCRIBE response body. At least a single subscription must be present if SUBSCRIBE was successful.");
            }
        } else {
            log.debug("Missing DAV:prop response body in SUBSCRIBE method.");
        }
        return false;
    }
}