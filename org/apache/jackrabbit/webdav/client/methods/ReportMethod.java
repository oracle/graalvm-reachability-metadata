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
import org.apache.jackrabbit.webdav.header.DepthHeader;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.webdav.DavServletResponse;

import java.io.IOException;

/**
 * <code>ReportMethod</code>...
 */
public class ReportMethod extends DavMethodBase {

    private static Logger log = LoggerFactory.getLogger(ReportMethod.class);

    private final boolean isDeep;

    public ReportMethod(String uri, ReportInfo reportInfo) throws IOException {
	super(uri);
	DepthHeader dh = new DepthHeader(reportInfo.getDepth());
        isDeep = reportInfo.getDepth() > DavConstants.DEPTH_0;

	setRequestHeader(dh);
        setRequestBody(reportInfo);
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
	return DavMethods.METHOD_REPORT;
    }

    //------------------------------------------------------< DavMethodBase >---
    /**
     *
     * @param statusCode
     * @return true if status code is {@link DavServletResponse#SC_OK 200 (OK)}
     * or {@link DavServletResponse#SC_MULTI_STATUS 207 (Multi Status)}. If the
     * report request included a depth other than {@link DavConstants#DEPTH_0 0}
     * a multi status response is required.
     */
    protected boolean isSuccess(int statusCode) {
        if (isDeep) {
            return statusCode == DavServletResponse.SC_MULTI_STATUS;
        } else {
            return statusCode == DavServletResponse.SC_OK || statusCode == DavServletResponse.SC_MULTI_STATUS;
        }
    }
}