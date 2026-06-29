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

import org.apache.jackrabbit.webdav.bind.RebindInfo;
import org.apache.jackrabbit.webdav.DavServletResponse;

import java.io.IOException;

/**
 * <code>RebindMethod</code> replaces a binding to a resource (atomic version of move).
 */
public class RebindMethod extends DavMethodBase {

    public RebindMethod(String uri, RebindInfo info) throws IOException {
        super(uri);
        setRequestBody(info);
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return "REBIND";
    }

    //------------------------------------------------------< DavMethodBase >---
    /**
     *
     * @param statusCode
     * @return true if status code is 200 (existing binding was overwritten) or 201 (new binding created).
     */
    protected boolean isSuccess(int statusCode) {
        return statusCode == DavServletResponse.SC_CREATED || statusCode == DavServletResponse.SC_OK;
    }
}

