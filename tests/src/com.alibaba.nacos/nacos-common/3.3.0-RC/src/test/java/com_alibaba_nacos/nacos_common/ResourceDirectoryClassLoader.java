/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/// Exposes one filesystem resource while delegating all class loading to the parent.
final class ResourceDirectoryClassLoader extends ClassLoader {
    private final String resourceName;
    private final URL resourceUrl;

    ResourceDirectoryClassLoader(String resourceName, URL resourceUrl, ClassLoader parent) {
        super(parent);
        this.resourceName = resourceName;
        this.resourceUrl = resourceUrl;
    }

    @Override
    public URL getResource(String name) {
        return resourceName.equals(name) ? resourceUrl : super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return resourceName.equals(name) ? Collections.enumeration(List.of(resourceUrl)) : super.getResources(name);
    }
}
