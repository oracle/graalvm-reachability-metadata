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
package org.apache.jackrabbit.webdav.security;

import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * <code>SupportedPrivilege</code>...
 */
public class SupportedPrivilege implements XmlSerializable {

    private static final String XML_SUPPORTED_PRIVILEGE = "supported-privilege";
    private static final String XML_ABSTRACT = "abstract";
    private static final String XML_DESCRIPTION = "description";

    private final Privilege privilege;
    private final boolean isAbstract;
    private final String description;
    private final String descriptionLanguage;
    private final SupportedPrivilege[] supportedPrivileges;

    /**
     *
     * @param privilege
     * @param description
     * @param descriptionLanguage
     * @param isAbstract
     * @param supportedPrivileges
     */
    public SupportedPrivilege(Privilege privilege, String description,
                              String descriptionLanguage, boolean isAbstract,
                              SupportedPrivilege[] supportedPrivileges) {
        if (privilege == null) {
            throw new IllegalArgumentException("DAV:supported-privilege element must contain a single privilege.");
        }
        this.privilege = privilege;
        this.description = description;
        this.descriptionLanguage = descriptionLanguage;
        this.isAbstract = isAbstract;
        this.supportedPrivileges = supportedPrivileges;
    }

    /**
     * @see XmlSerializable#toXml(Document)
     */
    public Element toXml(Document document) {
        Element spElem = DomUtil.createElement(document, XML_SUPPORTED_PRIVILEGE, SecurityConstants.NAMESPACE);
        spElem.appendChild(privilege.toXml(document));
        if (isAbstract) {
            DomUtil.addChildElement(spElem, XML_ABSTRACT, SecurityConstants.NAMESPACE);
        }
        if (description != null) {
            Element desc = DomUtil.addChildElement(spElem, XML_DESCRIPTION, SecurityConstants.NAMESPACE, description);
            if (descriptionLanguage != null) {
                DomUtil.setAttribute(desc, "lang", Namespace.XML_NAMESPACE, descriptionLanguage);
            }
        }
        if (supportedPrivileges != null) {
            for (int i = 0; i < supportedPrivileges.length; i++) {
                spElem.appendChild(supportedPrivileges[i].toXml(document));
            }
        }
        return spElem;
    }
}