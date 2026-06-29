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
package org.apache.jackrabbit.webdav;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.util.Text;

/**
 * <code>AbstractLocatorFactory</code> is an implementation of the DavLocatorFactory
 * interface that defines how a given uri is split to workspace path an
 * resource path and how it's implementation of <code>DavResourceLocator</code>
 * builds the href. In contrast, the conversion from repository path to
 * resource path and vice versa is left to subclasses.
 */
public abstract class AbstractLocatorFactory implements DavLocatorFactory {

    private static Logger log = LoggerFactory.getLogger(AbstractLocatorFactory.class);

    private final String pathPrefix;

    /**
     * Create a new factory
     *
     * @param pathPrefix Prefix, that needs to be removed in order to retrieve
     * the path of the repository item from a given <code>DavResourceLocator</code>.
     */
    public AbstractLocatorFactory(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    //--------------------------------------------------< DavLocatorFactory >---
    /**
     * Create a new <code>DavResourceLocator</code>. Any leading prefix and
     * path-prefix (as defined with the constructor) are removed from the
     * given request handle. The same applies for trailing '/'. The remaining
     * String is called the 'resource handle' and it's first segment is treated
     * as workspace name. If resource handle (and therefore workspace name
     * are missing, both values are set to <code>null</code>.<p/>
     * Examples:
     *
     * <pre>
     * http://www.foo.bar/ (path prefix missing)
     * -> workspace path = null
     * -> resource path  = null
     * -> href           = http://www.foo.bar/pathPrefix/
     *
     * http://www.foo.bar/pathPrefix/
     * -> workspace path = null
     * -> resource path  = null
     * -> href           = http://www.foo.bar/pathPrefix/
     *
     * http://www.foo.bar/pathPrefix/wspName
     * -> workspace path = /wspName
     * -> resource path  = /wspName
     * -> href           = http://www.foo.bar/pathPrefix/wspName
     *
     * http://www.foo.bar/pathPrefix/wspName/anypath
     * -> workspace path = /wspName
     * -> resource path  = /wspName/anypath
     * -> href           = http://www.foo.bar/pathPrefix/wspName/anypath
     * </pre>
     *
     * NOTE: If the given href is an absolute uri it must start with the
     * specified prefix.
     *
     * @param prefix
     * @param href
     * @return a new <code>DavResourceLocator</code>
     * @throws IllegalArgumentException if the given href is <code>null</code>
     */
    public DavResourceLocator createResourceLocator(String prefix, String href) {
        if (href == null) {
            throw new IllegalArgumentException("Request handle must not be null.");
        }

        // build prefix string and remove all prefixes from the given href.
        StringBuffer b = new StringBuffer("");
        if (prefix != null && prefix.length() > 0) {
            b.append(prefix);
            if (href.startsWith(prefix)) {
                href = href.substring(prefix.length());
            }
        }
        if (pathPrefix != null && pathPrefix.length() > 0) {
            if (!b.toString().endsWith(pathPrefix)) {
                b.append(pathPrefix);
            }
            if (href.startsWith(pathPrefix)) {
                href = href.substring(pathPrefix.length());
            }
        }

        // remove trailing "/" that is present with collections
        if (href.endsWith("/")) {
            href = href.substring(0, href.length() - 1);
        }

        String resourcePath;
        String workspacePath;

        // an empty requestHandle (after removal of the "/") signifies a request
        // to the root that does not represent a repository item.
        if ("".equals(href)) {
            resourcePath = null;
            workspacePath = null;
        } else {
            resourcePath = Text.unescape(href);
            // retrieve wspPath: look for the first slash ignoring the leading one
            int pos = href.indexOf('/', 1);
            if (pos == -1) {
                // request to a 'workspace' resource
                workspacePath = resourcePath;
            } else {
                // separate the workspace path from the resource path.
                workspacePath = Text.unescape(href.substring(0, pos));
            }
        }

        return new DavResourceLocatorImpl(b.toString(), workspacePath, resourcePath, this);
    }

    /**
     * Create a new <code>DavResourceLocator</code> from the specified prefix,
     * workspace path and resource path, whithout modifying the specified Strings.
     * Note, that it is expected that the resource path starts with the
     * given workspace path unless both values are <code>null</code>.
     *
     * @param prefix
     * @param workspacePath path or the workspace containing this resource or
     * <code>null</code>.
     * @param resourcePath Path of the resource or <code>null</code>. Any non
     * null value must start with the specified workspace path.
     * @return a new <code>DavResourceLocator</code>
     * @see DavLocatorFactory#createResourceLocator(String, String, String)
     */
    public DavResourceLocator createResourceLocator(String prefix, String workspacePath, String resourcePath) {
        return createResourceLocator(prefix, workspacePath, resourcePath, true);
    }

    /**
     * Create a new <code>DavResourceLocator</code> from the specified prefix,
     * workspace path and resource path. If <code>isResourcePath</code> is set
     * to <code>false</code>, the given 'resourcePath' is converted by calling
     * {@link #getResourcePath(String, String)}. Otherwise the same restriction
     * applies as for {@link #createResourceLocator(String, String, String)}.
     *
     * @param prefix
     * @param workspacePath
     * @param path
     * @param isResourcePath
     * @return
     * @see DavLocatorFactory#createResourceLocator(String, String, String, boolean)
     */
    public DavResourceLocator createResourceLocator(String prefix, String workspacePath, String path, boolean isResourcePath) {
        String resourcePath = (isResourcePath) ? path : getResourcePath(path, workspacePath);
        return new DavResourceLocatorImpl(prefix, workspacePath, resourcePath, this);
    }

    //--------------------------------------------------------------------------
    /**
     * Subclasses must defined how the repository path is built from the given
     * resource and workspace path.
     *
     * @param resourcePath
     * @param wspPath
     * @return
     */
    protected abstract String getRepositoryPath(String resourcePath, String wspPath);

    /**
     * Subclasses must defined how the resource path is built from the given
     * repository and workspace path.
     *
     * @param repositoryPath
     * @param wspPath
     * @return
     */
    protected abstract String getResourcePath(String repositoryPath, String wspPath);

    //--------------------------------------------------------< DavResource >---
    /**
     * Private inner class <code>DavResourceLocatorImpl</code> implementing
     * the <code>DavResourceLocator</code> interface.
     */
    private class DavResourceLocatorImpl implements DavResourceLocator {

        private final String prefix;
        private final String workspacePath;
        private final String resourcePath;
        private final AbstractLocatorFactory factory;

        private final String href;

        /**
         * Create a new <code>DavResourceLocatorImpl</code>.
         *
         * @param prefix
         * @param workspacePath
         * @param resourcePath
         */
        private DavResourceLocatorImpl(String prefix, String workspacePath, String resourcePath, AbstractLocatorFactory factory) {

            this.prefix = prefix;
            this.workspacePath = workspacePath;
            this.resourcePath = resourcePath;
            this.factory = factory;

            StringBuffer buf = new StringBuffer(prefix);
            // NOTE: no need to append the workspace path, since it is must
            // be part of the resource path.
            if (resourcePath != null && resourcePath.length() > 0) {
                // check if condition is really met
                if (!resourcePath.startsWith(workspacePath)) {
                    throw new IllegalArgumentException("Resource path '" + resourcePath + "' does not start with workspace path '" + workspacePath + ".");
                }
                buf.append(Text.escapePath(resourcePath));
            }
            int length = buf.length();
            if (length > 0 && buf.charAt(length - 1) != '/') {
                buf.append("/");
            }
            href = buf.toString();
        }

        /**
         * Return the prefix used to build the href String. This includes the initial
         * hrefPrefix as well a the path prefix.
         *
         * @return prefix String used to build the href.
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Returns the resource path which always starts with the workspace
         * path, if a workspace resource exists. For the top most resource
         * (request handle '/'), <code>null</code> is returned.
         *
         * @return resource path or <code>null</code>
         * @see org.apache.jackrabbit.webdav.DavResourceLocator#getResourcePath()
         */
        public String getResourcePath() {
            return resourcePath;
        }

        /**
         * Return the workspace path or <code>null</code> if this locator object
         * represents the '/' request handle.
         *
         * @return workspace path or <code>null</code>
         * @see org.apache.jackrabbit.webdav.DavResourceLocator#getWorkspacePath()
         */
        public String getWorkspacePath() {
            return workspacePath;
        }

        /**
         * Return the workspace name or <code>null</code> if this locator object
         * represents the '/' request handle, which does not contain a workspace
         * path.
         *
         * @return workspace name or <code>null</code>
         * @see org.apache.jackrabbit.webdav.DavResourceLocator#getWorkspaceName()
         */
        public String getWorkspaceName() {
            if (workspacePath != null && workspacePath.length() > 0) {
                return workspacePath.substring(1);
            }
            return null;
        }

        /**
         * Returns true if the specified locator object refers to a resource within
         * the same workspace.
         *
         * @param locator
         * @return true if the workspace name obtained from the given locator
         * refers to the same workspace as the workspace name of this locator.
         * @see DavResourceLocator#isSameWorkspace(org.apache.jackrabbit.webdav.DavResourceLocator)
         */
        public boolean isSameWorkspace(DavResourceLocator locator) {
            return (locator == null) ? false : isSameWorkspace(locator.getWorkspaceName());
        }

        /**
         * Returns true if the specified string equals to this workspace name or
         * if both names are null.
         *
         * @param workspaceName
         * @return true if the workspace name is equal to this workspace name.
         * @see DavResourceLocator#isSameWorkspace(String)
         */
        public boolean isSameWorkspace(String workspaceName) {
            String thisWspName = getWorkspaceName();
            return (thisWspName == null) ? workspaceName == null : thisWspName.equals(workspaceName);
        }

        /**
         * Returns an 'href' consisting of prefix and resource path (which starts
         * with the workspace path). It assures a trailing '/' in case the href
         * is used for collection. Note, that the resource path is
         * {@link Text#escapePath(String) escaped}.
         *
         * @param isCollection
         * @return href String representing the text of the href element
         * @see org.apache.jackrabbit.webdav.DavConstants#XML_HREF
         * @see DavResourceLocator#getHref(boolean)
         */
        public String getHref(boolean isCollection) {
            return (isCollection) ? href : href.substring(0, href.length() - 1);
        }

        /**
         * Returns true if the 'workspacePath' field is <code>null</code>.
         *
         * @return true if the 'workspacePath' field is <code>null</code>.
         * @see org.apache.jackrabbit.webdav.DavResourceLocator#isRootLocation()
         */
        public boolean isRootLocation() {
            return getWorkspacePath() == null;
        }

        /**
         * Return the factory that created this locator.
         *
         * @return factory
         * @see org.apache.jackrabbit.webdav.DavResourceLocator#getFactory()
         */
        public DavLocatorFactory getFactory() {
            return factory;
        }

        /**
         * Uses {@link AbstractLocatorFactory#getRepositoryPath(String, String)}
         * to build the repository path.
         *
         * @see DavResourceLocator#getRepositoryPath()
         */
        public String getRepositoryPath() {
            return factory.getRepositoryPath(getResourcePath(), getWorkspacePath());
        }

        /**
         * Computes the hash code from the href, that is built from the prefix,
         * the workspace name and the resource path all of them representing
         * final instance fields.
         *
         * @return the hash code
         */
        public int hashCode() {
            return href.hashCode();
        }

        /**
         * Returns true, if the given object is a <code>DavResourceLocatorImpl</code>
         * with the same hash code.
         *
         * @param obj the object to compare to
         * @return <code>true</code> if the 2 objects are equal;
         *         <code>false</code> otherwise
         */
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof DavResourceLocatorImpl) {
                DavResourceLocatorImpl other = (DavResourceLocatorImpl) obj;
                return hashCode() == other.hashCode();
            }
            return false;
        }
    }
}