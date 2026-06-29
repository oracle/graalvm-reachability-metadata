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

import org.apache.jackrabbit.webdav.property.AbstractDavProperty;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <code>SupportedPrivilegeSetProperty</code> defines the
 * {@link SecurityConstants#SUPPORTED_PRIVILEGE_SET} property, used to identify
 * the privileges defined for the resource. RFC 3744 defines the the following
 * structure for this property:
 * <pre>
 * &lt;!ELEMENT supported-privilege-set (supported-privilege*)&gt;
 * &lt;!ELEMENT supported-privilege (privilege, abstract?, description, supported-privilege*)&gt;
 * &lt;!ELEMENT privilege ANY&gt;
 * &lt;!ELEMENT abstract EMPTY&gt;
 * &lt;!ELEMENT description #PCDATA&gt;
 * </pre>
 *
 * @see SupportedPrivilege
 * @see Privilege
 */
public class SupportedPrivilegeSetProperty extends AbstractDavProperty {

    private final SupportedPrivilege[] supportedPrivileges;

    /**
     * Create a new <code>SupportedPrivilegeSetProperty</code>.
     *
     * @param supportedPrivileges
     */
    public SupportedPrivilegeSetProperty(SupportedPrivilege[] supportedPrivileges) {
        super(SecurityConstants.SUPPORTED_PRIVILEGE_SET, true);
        this.supportedPrivileges = supportedPrivileges;
    }

    /**
     * @return List of {@link SupportedPrivilege}s.
     */
    public Object getValue() {
        return (supportedPrivileges == null) ? new ArrayList() : Arrays.asList(supportedPrivileges);
    }
}