/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.NamespaceHandler;
import org.springframework.beans.factory.xml.UtilNamespaceHandler;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultNamespaceHandlerResolverTest {
    @Test
    void constructsAndCachesHandlerFromClasspathMappings() {
        DefaultNamespaceHandlerResolver resolver = new DefaultNamespaceHandlerResolver(getClass().getClassLoader());
        NamespaceHandler handler = resolver.resolve("http://www.springframework.org/schema/util");
        assertInstanceOf(UtilNamespaceHandler.class, handler);
        assertSame(handler, resolver.resolve("http://www.springframework.org/schema/util"));
        assertNull(resolver.resolve("urn:unregistered:namespace"));
    }
}
