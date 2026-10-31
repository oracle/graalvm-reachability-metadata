/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.control.SourceExtensionHandler;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Collections;
import java.util.Enumeration;
import java.net.URL;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SourceExtensionHandlerTest {
    @Test
    void discoversLegacyServiceDescriptorsWhenModernOnesAreAbsent() {
        ClassLoader legacyResources = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if ("META-INF/groovy/org.codehaus.groovy.source.Extensions".equals(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };
        Set<String> extensions = SourceExtensionHandler.getRegisteredExtensions(legacyResources);
        assertTrue(extensions.contains("groovy"));
        assertTrue(extensions.contains("manifest"));
    }

    @Test
    void discoversSourceExtensionsFromAClasspathServiceDescriptor() {
        Set<String> extensions = SourceExtensionHandler.getRegisteredExtensions(getClass().getClassLoader());
        assertTrue(extensions.contains("groovy"));
        assertTrue(extensions.contains("inventory"));
    }
}
