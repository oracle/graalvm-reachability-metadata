/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.Nexus;
import net.bytebuddy.dynamic.NexusAccessor;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class NexusAccessorInnerDispatcherInnerCreationActionTest {
    private static final Set<String> ISOLATED_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "net.bytebuddy.dynamic.Nexus",
            "net.bytebuddy.dynamic.NexusAccessor",
            "net.bytebuddy.dynamic.NexusAccessor$Dispatcher",
            "net.bytebuddy.dynamic.NexusAccessor$Dispatcher$Available",
            "net.bytebuddy.dynamic.NexusAccessor$Dispatcher$CreationAction",
            "net.bytebuddy.dynamic.NexusAccessor$Dispatcher$Unavailable")));

    private static final String NEXUS_RESOURCE = "net/bytebuddy/dynamic/Nexus.class";

    @Test
    void createsDispatcherForRegisteringAliveInitializer() {
        String previousDisabledProperty = System.getProperty(Nexus.PROPERTY);
        System.clearProperty(Nexus.PROPERTY);
        try {
            assertThat(NexusAccessor.isAlive()).isTrue();

            NexusAccessor nexusAccessor = new NexusAccessor();
            nexusAccessor.register(
                    SampleType.class.getName(),
                    SampleType.class.getClassLoader(),
                    42,
                    new AliveLoadedTypeInitializer());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            if (previousDisabledProperty == null) {
                System.clearProperty(Nexus.PROPERTY);
            } else {
                System.setProperty(Nexus.PROPERTY, previousDisabledProperty);
            }
        }
    }

    @Test
    void fallsBackToSystemNexusWhenClassFileCannotBeLocatedForInjection() throws Exception {
        try {
            ResourceHidingClassLoader classLoader = new ResourceHidingClassLoader(
                    NexusAccessorInnerDispatcherInnerCreationActionTest.class.getClassLoader());
            try {
                Class<?> nexusAccessorType = Class.forName(
                        "net.bytebuddy.dynamic.NexusAccessor",
                        true,
                        classLoader);

                if (isNativeImageRuntime() && nexusAccessorType.getClassLoader() != classLoader) {
                    throw new TestAbortedException(
                            "Native image runtime does not support reloading application classes via isolated URLClassLoader");
                }

                assertThat(nexusAccessorType.getClassLoader()).isSameAs(classLoader);
            } finally {
                classLoader.close();
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static class ResourceHidingClassLoader extends ClassLoader {
        private final ClassLoader sourceClassLoader;

        ResourceHidingClassLoader(ClassLoader parent) {
            super(parent);
            sourceClassLoader = parent;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (ISOLATED_TYPES.contains(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = defineIsolatedClass(name);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }

        private Class<?> defineIsolatedClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream resource = sourceClassLoader.getResourceAsStream(resourceName)) {
                if (resource == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = resource.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (ClassNotFoundException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        @Override
        public java.net.URL getResource(String name) {
            return NEXUS_RESOURCE.equals(name)
                    ? null
                    : super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return NEXUS_RESOURCE.equals(name)
                    ? null
                    : super.getResourceAsStream(name);
        }

        void close() {
            /* no resources to close */
        }
    }

    public static class AliveLoadedTypeInitializer implements LoadedTypeInitializer {
        @Override
        public void onLoad(Class<?> type) {
            /* no action required */
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }

    public static class SampleType {
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }
}
