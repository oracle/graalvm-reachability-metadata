/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class UnsafeAccessTest {
    @Test
    void resolvesUnsafeAndDeclaredFieldOffsets() {
        Assertions.assertNotNull(UnsafeAccess.UNSAFE);

        long valueOffset = UnsafeAccess.fieldOffset(OffsetHolder.class, "value");
        OffsetHolder holder = new OffsetHolder();
        Object marker = new Object();

        UnsafeAccess.UNSAFE.putObject(holder, valueOffset, marker);

        Assertions.assertSame(marker, holder.value);
    }

    @Test
    void fallsBackToThePrivateUnsafeConstructorWhenTheSingletonFieldCannotBeCast() throws Exception {
        Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        Unsafe singletonUnsafe = (Unsafe) theUnsafeField.get(null);
        Object staticFieldBase = singletonUnsafe.staticFieldBase(theUnsafeField);
        long staticFieldOffset = singletonUnsafe.staticFieldOffset(theUnsafeField);
        Object originalValue = singletonUnsafe.getObject(staticFieldBase, staticFieldOffset);

        try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(findNettyCommonLocation())) {
            singletonUnsafe.putObject(staticFieldBase, staticFieldOffset, new Object());

            Class<?> unsafeAccessClass = Class.forName(
                    "io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess",
                    true,
                    classLoader
            );
            Object fallbackUnsafe = unsafeAccessClass.getField("UNSAFE").get(null);

            Assertions.assertNotNull(fallbackUnsafe);
            Assertions.assertEquals(Unsafe.class, fallbackUnsafe.getClass());
            Assertions.assertNotSame(originalValue, fallbackUnsafe);
        } finally {
            singletonUnsafe.putObject(staticFieldBase, staticFieldOffset, originalValue);
        }
    }

    private static URL findNettyCommonLocation() throws MalformedURLException, URISyntaxException {
        String resourceName = "io/netty/util/internal/shaded/org/jctools/util/UnsafeAccess.class";
        URL resource = UnsafeAccessTest.class.getClassLoader().getResource(resourceName);
        Assertions.assertNotNull(resource, "Expected netty-common on the test classpath");

        if ("jar".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            int separatorIndex = externalForm.indexOf("!/");
            return URI.create(externalForm.substring("jar:".length(), separatorIndex)).toURL();
        }

        if ("file".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            return URI.create(externalForm.substring(0, externalForm.length() - resourceName.length())).toURL();
        }

        throw new IllegalStateException("Unsupported netty-common location: " + resource);
    }

    private static final class OffsetHolder {
        private Object value;
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, UnsafeAccessTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("io.netty.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
