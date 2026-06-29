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
package org.apache.jackrabbit.webdav.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>Namespace</code>
 */
public class Namespace {

    private static Logger log = LoggerFactory.getLogger(Namespace.class);

    public static final Namespace EMPTY_NAMESPACE = Namespace.getNamespace("","");
    public static final Namespace XML_NAMESPACE = Namespace.getNamespace("xml", "http://www.w3.org/XML/1998/namespace");
    public static final Namespace XMLNS_NAMESPACE = Namespace.getNamespace("xmlns", "http://www.w3.org/2000/xmlns/");

    private final String prefix;
    private final String uri;

    private Namespace(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getURI() {
        return uri;
    }

    public boolean isSame(String namespaceURI) {
        return uri.equals(namespaceURI);
    }

    public int hashCode() {
        return uri.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Namespace) {
            return uri.equals(((Namespace)obj).uri);
        }
        return false;
    }

    public static Namespace getNamespace(String prefix, String uri) {
        if (prefix == null) {
            prefix = EMPTY_NAMESPACE.getPrefix();
        }
        if (uri == null) {
            uri = EMPTY_NAMESPACE.getURI();
        }
        return new Namespace(prefix, uri);
    }

    public static Namespace getNamespace(String uri) {
        return getNamespace("", uri);
    }
}