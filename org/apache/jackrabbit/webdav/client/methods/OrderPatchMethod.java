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

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.ordering.OrderPatch;
import org.apache.jackrabbit.webdav.ordering.OrderingConstants;
import org.apache.jackrabbit.webdav.ordering.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * <code>OrderPatchMethod</code>...
 */
public class OrderPatchMethod extends DavMethodBase {

    private static Logger log = LoggerFactory.getLogger(OrderPatchMethod.class);

    /**
     * Create a new <code>OrderPatchMethod</code> with the given order patch
     * object.
     *
     * @param uri
     * @param orderPatch
     */
    public OrderPatchMethod(String uri, OrderPatch orderPatch) throws IOException {
        super(uri);
        setRequestBody(orderPatch);
    }

    /**
     * Create a new <code>OrderPatchMethod</code> that reorders the members
     * of the resource identified by 'uri': the member identified by 'memberSegment'
     * is moved to the first or to the last position, respectively.<br>
     * See the constructor taking an <code>OrderPatch</code> object for a ORDERPATCH call
     * that reorders multiple members at once.
     *
     * @param uri
     * @param orderingType href String identifying the ordering type
     * @param memberSegment
     * @param first
     */
    public OrderPatchMethod(String uri, String orderingType, String memberSegment, boolean first) throws IOException {
        super(uri);
        String orderPosition = (first) ? OrderingConstants.XML_FIRST : OrderingConstants.XML_LAST;
        Position p = new Position(orderPosition);
        OrderPatch op = new OrderPatch(orderingType, new OrderPatch.Member(memberSegment, p));
        setRequestBody(op);
    }

    /**
     * Create a new <code>OrderPatchMethod</code> that reorders the members
     * of the resource identified by 'uri': the member identified by 'memberSegment'
     * is reordered before or after the member identified by 'targetMemberSegmet'.<br>
     * See the constructor taking an <code>OrderPatch</code> object for a ORDERPATCH call
     * that reorders multiple members at once.
     *
     * @param uri
     * @param orderingType href String identifying the ordering type
     * @param memberSegment
     * @param targetMemberSegmet
     * @param before
     */
    public OrderPatchMethod(String uri, String orderingType, String memberSegment, String targetMemberSegmet, boolean before) throws IOException {
        super(uri);
        String orderPosition = (before) ? OrderingConstants.XML_BEFORE : OrderingConstants.XML_AFTER;
        Position p = new Position(orderPosition, targetMemberSegmet);
        OrderPatch op = new OrderPatch(orderingType, new OrderPatch.Member(memberSegment, p));
        setRequestBody(op);
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return DavMethods.METHOD_ORDERPATCH;
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
}