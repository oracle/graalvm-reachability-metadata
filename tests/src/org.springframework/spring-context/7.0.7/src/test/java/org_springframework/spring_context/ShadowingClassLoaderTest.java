/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.instrument.classloading.ShadowingClassLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShadowingClassLoaderTest {
    @Test
    void delegatesExcludedClassesAndResourcesWithoutDefiningClasses() throws Exception {
        ShadowingClassLoader loader = new ShadowingClassLoader(getClass().getClassLoader());
        assertSame(String.class, loader.loadClass("java.lang.String"));
        String name = "org_springframework/spring_context/shadow.txt";
        assertNotNull(loader.getResource(name));
        assertFalse(Collections.list(loader.getResources(name)).isEmpty());
        try (InputStream input = loader.getResourceAsStream(name)) {
            assertNotNull(input);
            assertEquals("delegated resource", new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
        assertTrue(loader.getResource(name).toExternalForm().contains("shadow.txt"));
    }
}
