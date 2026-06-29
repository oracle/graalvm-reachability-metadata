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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <code>ElementIterator</code>...
 */
public class ElementIterator implements Iterator {

    private static Logger log = LoggerFactory.getLogger(ElementIterator.class);

    private final Namespace namespace;
    private final String localName;

    private Element next;

    /**
     * Create a new instance of <code>ElementIterator</code> with the given
     * parent element. Only child elements that match the given local name
     * and namespace will be respected by {@link #hasNext()} and {@link #nextElement()}.
     *
     * @param parent
     * @param localName local name the child elements must match
     * @param namespace namespace the child elements must match
     */
    public ElementIterator(Element parent, String localName, Namespace namespace) {
        this.localName = localName;
        this.namespace = namespace;
        seek(parent);
    }

    /**
     * Create a new instance of <code>ElementIterator</code> with the given
     * parent element. No filtering is applied to child elements that are
     * iterated.
     *
     * @param parent
     */
    public ElementIterator(Element parent) {
        this(parent, null, null);
    }

    /**
     * Not implemented
     *
     * @throws UnsupportedOperationException
     */
    public void remove() {
        throw new UnsupportedOperationException("Remove not implemented.");
    }

    /**
     * Returns true if there is a next <code>Element</code>
     *
     * @return true if a next <code>Element</code> is available.
     */
    public boolean hasNext() {
        return next != null;
    }

    /**
     * @see java.util.Iterator#next()
     * @see #nextElement()
     */
    public Object next() {
        return nextElement();
    }

    /**
     * Returns the next <code>Element</code> in the iterator.
     *
     * @return the next element
     * @throws NoSuchElementException if there is no next element.
     */
    public Element nextElement() {
        if (next==null) {
            throw new NoSuchElementException();
        }
        Element ret = next;
        seek();
        return ret;
    }

    /**
     * Seeks for the first matching child element
     */
    private void seek(Element parent) {
        NodeList nodeList = parent.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            if (DomUtil.isElement(n) && DomUtil.matches(n, localName, namespace)) {
                next = (Element)n;
                return;
            }
        }
    }

    /**
     * Seeks for the next valid element (i.e. the next valid sibling)
     */
    private void seek() {
        Node n = next.getNextSibling();
        while (n != null) {
            if (DomUtil.isElement(n) && DomUtil.matches(n, localName, namespace)) {
                next = (Element)n;
                return;
            } else {
                n = n.getNextSibling();
            }
        }
        // no next element found -> set to null in order to leave the loop.
        next = null;
    }
}