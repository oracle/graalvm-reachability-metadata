/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.withSettings;

public class ProxyMockMakerInnerCommonClassLoaderTest {
    private static final String LEFT_INTERFACE_NAME = MyService.class.getName();
    private static final String RIGHT_INTERFACE_NAME = Callable.class.getName();

    @Test
    void createsInterfaceMockWithExtraInterfaceFromUnrelatedClassLoader() throws Exception {
        try (InterfaceOnlyClassLoader leftLoader =
                new InterfaceOnlyClassLoader(LEFT_INTERFACE_NAME, RIGHT_INTERFACE_NAME)) {
            Class<?> leftInterface = leftLoader.loadClass(LEFT_INTERFACE_NAME);
            Class<?> rightInterface = Callable.class;

            Object mock =
                    Mockito.mock(leftInterface, withSettings().extraInterfaces(rightInterface));

            assertThat(Mockito.mockingDetails(mock).isMock()).isTrue();
            assertThat(leftInterface.isInstance(mock)).isTrue();
            assertThat(rightInterface.isInstance(mock)).isTrue();
            assertThat(Mockito.mockingDetails(mock).getMockCreationSettings().getExtraInterfaces())
                    .containsExactly(rightInterface);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class InterfaceOnlyClassLoader extends URLClassLoader {
        private final String interfaceName;
        private final String rejectedInterfaceName;

        private InterfaceOnlyClassLoader(String interfaceName, String rejectedInterfaceName) {
            super(testClasspathUrls(), ClassLoader.getPlatformClassLoader());
            this.interfaceName = interfaceName;
            this.rejectedInterfaceName = rejectedInterfaceName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (interfaceName.equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            if (rejectedInterfaceName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return Class.forName(name, resolve, ClassLoader.getSystemClassLoader());
        }
    }

    private static URL[] testClasspathUrls() {
        NativeRuntimePropertiesSupport.restoreMissingRuntimeProperties();
        return Arrays.stream(System.getProperty("java.class.path", "").split(File.pathSeparator))
                .filter(entry -> !entry.isEmpty())
                .map(File::new)
                .map(File::toURI)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toArray(URL[]::new);
    }
}
