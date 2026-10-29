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

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ByteArrayClassLoaderInnerChildFirstTest {
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
