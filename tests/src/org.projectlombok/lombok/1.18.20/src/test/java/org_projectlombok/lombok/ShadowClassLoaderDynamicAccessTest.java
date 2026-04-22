/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ShadowClassLoaderDynamicAccessTest {
    @Test
    void shadowClassLoaderUsesParentResourceAndClassLookups() throws Throwable {
        Path selfRoot = Files.createTempDirectory("lombok-shadow-self");
        Path externalRoot = Files.createTempDirectory("lombok-shadow-external");
        String externalResourceName = "org_projectlombok/lombok/ShadowLookup.class";
        String shadowResourceName = "lombok/launch/Main.class";

        URL externalResource = writeResource(externalRoot, externalResourceName);
        URL shadowResource = writeResource(selfRoot, shadowResourceName);
        URL externalShadowResource = writeResource(externalRoot, shadowResourceName);

        ClassLoader parent = new ResourceClassLoader(
                ShadowClassLoaderDynamicAccessTest.class.getClassLoader(),
                externalResourceName,
                externalResource,
                shadowResourceName,
                shadowResource,
                externalShadowResource);

        ClassLoader shadowLoader = (ClassLoader) LombokLaunchTestSupport.newInstance(
                "lombok.launch.ShadowClassLoader",
                new Class<?>[] {ClassLoader.class, String.class, String.class, List.class, List.class},
                parent,
                "lombok",
                selfRoot.toUri().toString(),
                Collections.emptyList(),
                Collections.emptyList());

        consume(shadowLoader.getResources(externalResourceName));
        assertThat(shadowLoader.getResource(externalResourceName)).isEqualTo(externalResource);

        LombokLaunchTestSupport.invoke(
                shadowLoader,
                shadowLoader.getClass(),
                "addOverrideClasspathEntry",
                new Class<?>[] {String.class},
                Files.createTempDirectory("lombok-shadow-override").toString());
        assertThat(shadowLoader.getResource(shadowResourceName)).isEqualTo(externalShadowResource);

        assertThat(shadowLoader.loadClass(LombokLaunchTestSupport.class.getName())).isSameAs(LombokLaunchTestSupport.class);

        URL targetClass = ShadowDefinedTarget.class.getClassLoader()
                .getResource(ShadowDefinedTarget.class.getName().replace('.', '/') + ".class");
        assertThat(targetClass).isNotNull();

        Class<?> firstDefinition = (Class<?>) LombokLaunchTestSupport.invoke(
                shadowLoader,
                shadowLoader.getClass(),
                "urlToDefineClass",
                new Class<?>[] {String.class, URL.class, boolean.class},
                ShadowDefinedTarget.class.getName(),
                targetClass,
                false);
        Class<?> secondDefinition = (Class<?>) LombokLaunchTestSupport.invoke(
                shadowLoader,
                shadowLoader.getClass(),
                "urlToDefineClass",
                new Class<?>[] {String.class, URL.class, boolean.class},
                ShadowDefinedTarget.class.getName(),
                targetClass,
                false);

        assertThat(firstDefinition.getName()).isEqualTo(ShadowDefinedTarget.class.getName());
        assertThat(secondDefinition).isSameAs(firstDefinition);
    }

    private static URL writeResource(Path root, String resourceName) throws IOException {
        Path resourcePath = root.resolve(resourceName);
        Files.createDirectories(resourcePath.getParent());
        Files.write(resourcePath, new byte[] {0x1});
        return resourcePath.toUri().toURL();
    }

    private static void consume(Enumeration<URL> resources) {
        while (resources.hasMoreElements()) {
            resources.nextElement();
        }
    }

    public static final class ShadowDefinedTarget {
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final String externalResourceName;
        private final URL externalResource;
        private final String shadowResourceName;
        private final URL shadowResource;
        private final URL externalShadowResource;

        private ResourceClassLoader(
                ClassLoader parent,
                String externalResourceName,
                URL externalResource,
                String shadowResourceName,
                URL shadowResource,
                URL externalShadowResource) {
            super(parent);
            this.externalResourceName = externalResourceName;
            this.externalResource = externalResource;
            this.shadowResourceName = shadowResourceName;
            this.shadowResource = shadowResource;
            this.externalShadowResource = externalShadowResource;
        }

        @Override
        public URL getResource(String name) {
            if (externalResourceName.equals(name)) {
                return externalResource;
            }
            if (shadowResourceName.equals(name)) {
                return shadowResource;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (externalResourceName.equals(name)) {
                return Collections.enumeration(List.of(externalResource));
            }
            if (shadowResourceName.equals(name)) {
                return Collections.enumeration(List.of(shadowResource, externalShadowResource));
            }
            return super.getResources(name);
        }
    }
}
