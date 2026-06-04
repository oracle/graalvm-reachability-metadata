/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.withSettings;

public class ProxyMockMakerInnerCommonClassLoaderTest {
    private static final String LEFT_INTERFACE =
            "org_mockito.mockito_core.ProxyMockMakerCommonLeftInterface";
    private static final String RIGHT_INTERFACE =
            "org_mockito.mockito_core.ProxyMockMakerCommonRightInterface";

    @Test
    void proxyMockMakerCreatesMockForInterfacesFromUnrelatedClassLoaders() throws Exception {
        try {
            createProxyMockForInterfacesFromUnrelatedClassLoaders();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void createProxyMockForInterfacesFromUnrelatedClassLoaders() throws Exception {
        URL[] classPathUrls = currentClassPathUrls();

        try (SelectiveInterfaceClassLoader leftLoader =
                        new SelectiveInterfaceClassLoader(classPathUrls, LEFT_INTERFACE);
                SelectiveInterfaceClassLoader rightLoader =
                        new SelectiveInterfaceClassLoader(classPathUrls, RIGHT_INTERFACE)) {
            Class<?> primaryInterface = Class.forName(LEFT_INTERFACE, true, leftLoader);
            Class<?> extraInterface = Class.forName(RIGHT_INTERFACE, true, rightLoader);

            Object mock = Mockito.mock(
                    (Class) primaryInterface,
                    withSettings().mockMaker(MockMakers.PROXY).extraInterfaces(extraInterface));

            assertThat(Mockito.mockingDetails(mock).isMock()).isTrue();
            assertThat(primaryInterface.isInstance(mock)).isTrue();
            assertThat(extraInterface.isInstance(mock)).isTrue();
            assertThat(Arrays.asList(mock.getClass().getInterfaces()))
                    .contains(primaryInterface, extraInterface);
        }
    }

    private static URL[] currentClassPathUrls() throws IOException {
        String[] classPathEntries = System.getProperty("java.class.path", "")
                .split(File.pathSeparator);
        List<URL> urls = new ArrayList<>();
        for (String classPathEntry : classPathEntries) {
            if (!classPathEntry.isBlank()) {
                urls.add(Path.of(classPathEntry).toUri().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static final class SelectiveInterfaceClassLoader extends URLClassLoader {
        private final String ownedInterfaceName;

        private SelectiveInterfaceClassLoader(URL[] urls, String ownedInterfaceName) {
            super(urls, ClassLoader.getSystemClassLoader());
            this.ownedInterfaceName = ownedInterfaceName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && ownedInterfaceName.equals(name)) {
                    loadedClass = findClass(name);
                } else if (loadedClass == null && isPeerInterface(name)) {
                    throw new ClassNotFoundException(name);
                } else if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static boolean isPeerInterface(String name) {
            return LEFT_INTERFACE.equals(name) || RIGHT_INTERFACE.equals(name);
        }
    }
}
