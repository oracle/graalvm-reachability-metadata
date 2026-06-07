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
import java.net.URL;
import java.net.URLClassLoader;
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
            URL byteBuddyLocation = Nexus.class.getProtectionDomain().getCodeSource().getLocation();
            ResourceHidingClassLoader classLoader = new ResourceHidingClassLoader(
                    new URL[] {byteBuddyLocation},
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

    private static class ResourceHidingClassLoader extends URLClassLoader {
        ResourceHidingClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (ISOLATED_TYPES.contains(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }

        @Override
        public URL getResource(String name) {
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
