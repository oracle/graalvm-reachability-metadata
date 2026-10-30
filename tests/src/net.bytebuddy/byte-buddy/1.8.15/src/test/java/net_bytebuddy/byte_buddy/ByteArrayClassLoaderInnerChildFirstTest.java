/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy;
import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ByteArrayClassLoaderInnerChildFirstTest {
    @Test
    void readsRetainedClassResourceBeforeParentResourceWithoutDefiningClasses() throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        String resourceName = "net/bytebuddy/utility/JavaType.class";
        byte[] bytes = ClassFileLocator.ForClassLoader.read(JavaType.class).resolve();
        ByteArrayClassLoader.ChildFirst loader = new ByteArrayClassLoader.ChildFirst(parent,
                Map.of(JavaType.class.getName(), bytes), null,
                ByteArrayClassLoader.PersistenceHandler.MANIFEST, PackageDefinitionStrategy.Trivial.INSTANCE);
        URL resource = loader.getResource(resourceName);
        assertNotNull(resource);
        assertEquals("bytebuddy", resource.getProtocol());
        try (InputStream stream = resource.openStream()) {
            assertArrayEquals(bytes, stream.readAllBytes());
        }
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            assertNotNull(stream);
            assertArrayEquals(bytes, stream.readAllBytes());
        }
        assertSame(JavaType.class, parent.loadClass(JavaType.class.getName()));
    }

    @Test
    void delegatesExistingClassesAndResourcesWhenNoLocalDefinitionsExist() throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        ByteArrayClassLoader.ChildFirst loader = new ByteArrayClassLoader.ChildFirst(parent, Map.of());
        assertSame(TestFixtures.Sample.class, loader.loadClass(TestFixtures.Sample.class.getName()));
        assertSame(TestFixtures.Sample.class, loader.loadClass(TestFixtures.Sample.class.getName()));
        URL resource = loader.getResource("byte-buddy-fixture.txt");
        assertNotNull(resource);
        try (InputStream stream = resource.openStream()) {
            assertEquals("byte-buddy resource fixture", new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
        List<URL> resources = Collections.list(loader.getResources("byte-buddy-fixture.txt"));
        assertEquals(Collections.list(parent.getResources("byte-buddy-fixture.txt")), resources);
        assertNull(loader.getResource("missing/bytebuddy/Absent.class"));
    }
}
