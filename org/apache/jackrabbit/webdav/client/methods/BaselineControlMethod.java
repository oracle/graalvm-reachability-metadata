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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * <code>BaselineControlMethod</code>...
 */
public class BaselineControlMethod extends DavMethodBase {

    private static Logger log = LoggerFactory.getLogger(BaselineControlMethod.class);

    public BaselineControlMethod(String uri) {
        super(uri);
    }

    public BaselineControlMethod(String uri, String baselineHref) throws IOException {
        super(uri);
        if (baselineHref != null) {
            // build the request body
            try {
                // create the document and attach the root element
                Document document = BUILDER_FACTORY.newDocumentBuilder().newDocument();
                Element el = DomUtil.addChildElement(document, "baseline-control", DeltaVConstants.NAMESPACE);
                el.appendChild(DomUtil.hrefToXml(baselineHref, document));
                // set the request body
                setRequestBody(document);
            } catch (ParserConfigurationException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    //---------------------------------------------------------< HttpMethod >---
    /**
     * @see org.apache.commons.httpclient.HttpMethod#getName()
     */
    public String getName() {
        return DavMethods.METHOD_BASELINE_CONTROL;
    }

    //------------------------------------------------------< DavMethodBase >---
    /**
     * @param statusCode
     * @return true if status code is {@link DavServletResponse#SC_OK 200 (OK)}.
     */
    protected boolean isSuccess(int statusCode) {
        return statusCode == DavServletResponse.SC_OK;
    }
}