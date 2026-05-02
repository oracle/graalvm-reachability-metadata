/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.launch.Locator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocatorTest {
    private static final String LOCATOR_CLASS_CACHE_FIELD = "class$org$apache$tools$ant$launch$Locator";

    @TempDir
    Path temporaryDirectory;

    @Test
    void locatesResourceSourceFromAnExplicitClassLoader() throws Exception {
        String resourceName = "locator-resource.txt";
        Path resource = temporaryDirectory.resolve(resourceName);
        Files.writeString(resource, "launcher resource", StandardCharsets.UTF_8);
        ClassLoader loader = new SingleResourceClassLoader(resourceName, resource.toUri().toURL());

        File source = Locator.getResourceSource(loader, resourceName);

        assertThat(source).isNotNull();
        assertThat(source.toPath().toRealPath()).isEqualTo(temporaryDirectory.toRealPath());
    }

    @Test
    void locatesAntLauncherClassResourceWithDefaultLoaderSelection() {
        File source = Locator.getResourceSource(null, "org/apache/tools/ant/launch/Locator.class");

        assertThat(source).satisfiesAnyOf(
            value -> assertThat(value).isNull(),
            value -> assertThat(value).exists()
        );
    }

    @Test
    void locatesBootstrapResourceThroughSystemResourceFallback() throws Exception {
        VarHandle locatorClassCache = locatorClassCache();
        Class<?> previousLocatorClass = (Class<?>) locatorClassCache.get();
        locatorClassCache.set(String.class);
        try {
            File source = Locator.getResourceSource(null, "java/lang/String.class");

            assertThat(source).satisfiesAnyOf(
                value -> assertThat(value).isNull(),
                value -> assertThat(value).exists()
            );
        } finally {
            locatorClassCache.set(previousLocatorClass);
        }
    }

    private static VarHandle locatorClassCache() throws NoSuchFieldException, IllegalAccessException {
        return MethodHandles.privateLookupIn(Locator.class, MethodHandles.lookup())
            .findStaticVarHandle(Locator.class, LOCATOR_CLASS_CACHE_FIELD, Class.class);
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private SingleResourceClassLoader(String resourceName, URL resourceUrl) {
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return super.getResource(name);
        }
    }
}
