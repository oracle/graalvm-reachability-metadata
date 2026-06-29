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
package org.apache.jackrabbit.webdav.io;

import org.apache.jackrabbit.webdav.DavConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.Date;

/**
 * <code>InputContextImpl</code> class encapsulates the <code>InputStream</code>
 * and some header values as present in the POST, PUT or MKCOL request.
 */
public class InputContextImpl implements InputContext {

    private static Logger log = LoggerFactory.getLogger(InputContextImpl.class);

    private final HttpServletRequest request;
    private final InputStream in;

    public InputContextImpl(HttpServletRequest request, InputStream in) {
        if (request == null) {
            throw new IllegalArgumentException("DavResource and Request must not be null.");
        }

        this.request = request;
        this.in = in;
    }

    public boolean hasStream() {
        return in != null;
    }

    /**
     * Returns the input stream of the resource to import.
     *
     * @return the input stream.
     */
    public InputStream getInputStream() {
        return in;
    }

    public long getModificationTime() {
        return new Date().getTime();
    }

    /**
     * Returns the content language or <code>null</code>.
     *
     * @return contentLanguage
     */
    public String getContentLanguage() {
        return request.getHeader(DavConstants.HEADER_CONTENT_LANGUAGE);
    }

    public long getContentLength() {
        int length = request.getIntHeader(DavConstants.HEADER_CONTENT_LENGTH);
        return Long.parseLong(length + "");
    }

    public String getContentType() {
        return request.getHeader(DavConstants.HEADER_CONTENT_TYPE);
    }

    public String getProperty(String propertyName) {
        return request.getHeader(propertyName);
    }
}
