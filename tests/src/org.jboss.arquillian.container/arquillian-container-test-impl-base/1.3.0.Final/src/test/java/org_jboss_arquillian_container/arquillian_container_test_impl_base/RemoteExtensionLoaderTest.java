/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.container.test.impl.RemoteExtensionLoader;
import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteExtensionLoaderTest {
    private static final String REMOTE_EXTENSION_SERVICE_FILE =
            "META-INF/services/org.jboss.arquillian.container.test.spi.RemoteLoadableExtension";

    @Test
    void loadDiscoversRemoteExtensionsFromThreadContextClassLoaderServices() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ServiceResourceClassLoader serviceClassLoader = new ServiceResourceClassLoader(
                originalClassLoader,
                RemoteExtensionLoaderProvider.class.getName());
        try {
            Thread.currentThread().setContextClassLoader(serviceClassLoader);

            Collection<LoadableExtension> extensions = new RemoteExtensionLoader().load();

            assertThat(extensions)
                    .hasSize(1)
                    .first()
                    .isInstanceOf(RemoteExtensionLoaderProvider.class);
            assertThat(serviceClassLoader.requestedResourceName).isEqualTo(REMOTE_EXTENSION_SERVICE_FILE);
            assertThat(serviceClassLoader.requestedClassName).isEqualTo(RemoteExtensionLoaderProvider.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadVetoedDiscoversExclusionsAndLoadsServiceImplementations() {
        String firstProviderName = VetoedRemoteExtensionProviderOne.class.getName();
        String secondProviderName = VetoedRemoteExtensionProviderTwo.class.getName();
        String missingProviderName = "org.example.DoesNotExist";
        VetoedResourceClassLoader vetoedClassLoader = new VetoedResourceClassLoader(
                RemoteExtensionLoaderTest.class.getClassLoader(),
                VetoedRemoteExtensionService.class.getName() + "="
                        + firstProviderName + ", " + missingProviderName + ", " + secondProviderName + "\n");

        Map<Class<?>, Set<Class<?>>> vetoed = new RemoteExtensionLoader().loadVetoed(vetoedClassLoader);

        assertThat(vetoed)
                .containsOnlyKeys(VetoedRemoteExtensionService.class);
        assertThat(vetoed.get(VetoedRemoteExtensionService.class))
                .containsExactly(VetoedRemoteExtensionProviderOne.class, VetoedRemoteExtensionProviderTwo.class);
        assertThat(vetoedClassLoader.requestedResourceName).isEqualTo("META-INF/exclusions");
        assertThat(vetoedClassLoader.requestedClassNames)
                .containsExactly(
                        VetoedRemoteExtensionService.class.getName(),
                        firstProviderName,
                        missingProviderName,
                        secondProviderName);
    }

    private static final class ServiceResourceClassLoader extends ClassLoader {
        private final String providerClassName;
        private String requestedResourceName;
        private String requestedClassName;

        private ServiceResourceClassLoader(ClassLoader parent, String providerClassName) {
            super(parent);
            this.providerClassName = providerClassName;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceName = name;
            if (!REMOTE_EXTENSION_SERVICE_FILE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            URL serviceDescriptor = new URL(null, "memory:remote-extension-services", new StringUrlStreamHandler(
                    "# comment lines are ignored\n"
                            + providerClassName
                            + " # inline comments are ignored\n"));
            return Collections.enumeration(Collections.singletonList(serviceDescriptor));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (providerClassName.equals(name)) {
                requestedClassName = name;
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class VetoedResourceClassLoader extends ClassLoader {
        private final String exclusionsContent;
        private final Collection<String> requestedClassNames = new ArrayList<>();
        private String requestedResourceName;

        private VetoedResourceClassLoader(ClassLoader parent, String exclusionsContent) {
            super(parent);
            this.exclusionsContent = exclusionsContent;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceName = name;
            if (!"META-INF/exclusions".equals(name)) {
                return Collections.emptyEnumeration();
            }
            URL serviceDescriptor = new URL(null, "memory:remote-extension-exclusions", new StringUrlStreamHandler(
                    exclusionsContent));
            return Collections.enumeration(Collections.singletonList(serviceDescriptor));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(VetoedRemoteExtensionService.class.getName())
                    || name.equals(VetoedRemoteExtensionProviderOne.class.getName())
                    || name.equals(VetoedRemoteExtensionProviderTwo.class.getName())
                    || name.equals("org.example.DoesNotExist")) {
                requestedClassNames.add(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class StringUrlStreamHandler extends URLStreamHandler {
        private final String content;

        private StringUrlStreamHandler(String content) {
            this.content = content;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                }
            };
        }
    }
}

class RemoteExtensionLoaderProvider implements RemoteLoadableExtension {
    @Override
    public void register(LoadableExtension.ExtensionBuilder builder) {
    }
}

interface VetoedRemoteExtensionService {
}

class VetoedRemoteExtensionProviderOne implements VetoedRemoteExtensionService {
}

class VetoedRemoteExtensionProviderTwo implements VetoedRemoteExtensionService {
}
