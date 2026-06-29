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
import org.apache.jackrabbit.webdav.search.SearchInfo;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * <code>SearchMethod</code>...
 */
public class SearchMethod extends DavMethodBase {

    private static Logger log = LoggerFactory.getLogger(SearchMethod.class);

    public SearchMethod(String uri, String statement, String language) throws IOException {
        this(uri, statement, language, Namespace.EMPTY_NAMESPACE);
    }

    public SearchMethod(String uri, String statement, String language, Namespace languageNamespace) throws IOException {
        super(uri);
        if (language != null && statement != null) {
            // build the request body
            SearchInfo searchInfo = new SearchInfo(language, languageNamespace, statement);
            setRequestBody(searchInfo);
        }
    }

    public SearchMethod(String uri, SearchInfo searchInfo) throws IOException {
        super(uri);
        setRequestBody(searchInfo);
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return DavMethods.METHOD_SEARCH;
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