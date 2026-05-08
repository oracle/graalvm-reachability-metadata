/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NativeLibraryLoaderTest {
    private static final String TEST_LIBRARY_NAME = "netty_loader_probe";

    @Test
    void loadSearchesNativeResourcesBeforeLoadingFromSystemLibraryPath() {
        String originalOsName = System.getProperty("os.name");
        System.setProperty("os.name", "Mac OS X");
        NativeLibraryLoaderResourceClassLoader classLoader = new NativeLibraryLoaderResourceClassLoader();

        try {
            assertThrows(UnsatisfiedLinkError.class, () -> NativeLibraryLoader.load(TEST_LIBRARY_NAME, classLoader));
        } finally {
            restoreOsName(originalOsName);
        }

        assertThat(classLoader.requestedResources())
                .isNotEmpty()
                .first()
                .asString()
                .startsWith("META-INF/native/lib" + TEST_LIBRARY_NAME);
        if (classLoader.requestedResources().size() > 1) {
            assertThat(classLoader.requestedResources())
                    .anyMatch(resource -> resource.endsWith(".jnilib") || resource.endsWith(".dynlib"));
        }
    }

    @Test
    void loadSearchesDynlibFallbackWhenInitialNativeResourceUsesJniLibSuffix() throws Exception {
        String originalOsName = System.getProperty("os.name");
        System.setProperty("os.name", "Mac OS X");
        NativeLibraryLoaderOsNameRestorer osNameRestorer = NativeLibraryLoaderOsNameRestorer.forceMacOsX();
        JniLibSuffixResourceClassLoader classLoader = new JniLibSuffixResourceClassLoader();

        try {
            assertThrows(
                    UnsatisfiedLinkError.class,
                    () -> NativeLibraryLoader.load("netty_loader_probeXXXX", classLoader)
            );
            assertThat(classLoader.requestedResources())
                    .contains("META-INF/native/libnetty_loader_probeXXXX.dynlib");
        } finally {
            osNameRestorer.close();
            restoreOsName(originalOsName);
        }
    }

    private static void restoreOsName(String originalOsName) {
        if (originalOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", originalOsName);
        }
    }

    private static final class NativeLibraryLoaderOsNameRestorer implements AutoCloseable {
        private final Object staticFieldBase;
        private final long staticFieldOffset;
        private final Object originalValue;

        private NativeLibraryLoaderOsNameRestorer(
                Object staticFieldBase,
                long staticFieldOffset,
                Object originalValue
        ) {
            this.staticFieldBase = staticFieldBase;
            this.staticFieldOffset = staticFieldOffset;
            this.originalValue = originalValue;
        }

        private static NativeLibraryLoaderOsNameRestorer forceMacOsX() throws ReflectiveOperationException {
            Class.forName(NativeLibraryLoader.class.getName(), true, NativeLibraryLoader.class.getClassLoader());
            Unsafe unsafe = getUnsafe();
            Field osNameField = NativeLibraryLoader.class.getDeclaredField("OSNAME");
            Object staticFieldBase = unsafe.staticFieldBase(osNameField);
            long staticFieldOffset = unsafe.staticFieldOffset(osNameField);
            Object originalValue = unsafe.getObject(staticFieldBase, staticFieldOffset);
            unsafe.putObject(staticFieldBase, staticFieldOffset, "macosx");
            return new NativeLibraryLoaderOsNameRestorer(staticFieldBase, staticFieldOffset, originalValue);
        }

        @Override
        public void close() throws ReflectiveOperationException {
            getUnsafe().putObject(staticFieldBase, staticFieldOffset, originalValue);
        }
    }

    private static final class JniLibSuffixResourceClassLoader extends NativeLibraryLoaderResourceClassLoader {
        private static final String GENERATED_SUFFIX = "XXXX.so";
        private static final String JNI_LIB_SUFFIX = ".jnilib";

        @Override
        public URL getResource(String name) {
            if (name.endsWith(GENERATED_SUFFIX)) {
                replaceStringSuffix(name, JNI_LIB_SUFFIX);
            }
            return super.getResource(name);
        }
    }

    private static void replaceStringSuffix(String value, String suffix) {
        try {
            Unsafe unsafe = getUnsafe();
            Field valueField = String.class.getDeclaredField("value");
            Object storage = unsafe.getObject(value, unsafe.objectFieldOffset(valueField));
            if (storage instanceof byte[]) {
                byte[] bytes = (byte[]) storage;
                byte[] suffixBytes = suffix.getBytes(StandardCharsets.ISO_8859_1);
                System.arraycopy(suffixBytes, 0, bytes, bytes.length - suffixBytes.length, suffixBytes.length);
            } else if (storage instanceof char[]) {
                char[] chars = (char[]) storage;
                suffix.getChars(0, suffix.length(), chars, chars.length - suffix.length());
            } else {
                throw new AssertionError("Unsupported String storage: " + storage.getClass());
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to rewrite mapped native library suffix", e);
        }
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static class NativeLibraryLoaderResourceClassLoader extends ClassLoader {
        private final URL invalidNativeLibraryUrl;
        private final List<String> requestedResources = new ArrayList<>();

        NativeLibraryLoaderResourceClassLoader() {
            super(NativeLibraryLoaderTest.class.getClassLoader());
            invalidNativeLibraryUrl = createInvalidNativeLibraryUrl();
        }

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            if (requestedResources.size() == 1) {
                return null;
            }
            if (name.endsWith(".jnilib") || name.endsWith(".dynlib")) {
                return invalidNativeLibraryUrl;
            }
            return null;
        }

        final List<String> requestedResources() {
            return Collections.unmodifiableList(requestedResources);
        }

        private static URL createInvalidNativeLibraryUrl() {
            try {
                return new URL(null, "memory:invalid-native-library", new InvalidNativeLibraryUrlStreamHandler());
            } catch (IOException e) {
                throw new AssertionError("Unable to create in-memory native library URL", e);
            }
        }
    }

    private static final class InvalidNativeLibraryUrlStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) {
            return new InvalidNativeLibraryUrlConnection(url);
        }
    }

    private static final class InvalidNativeLibraryUrlConnection extends URLConnection {
        private InvalidNativeLibraryUrlConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public InputStream getInputStream() {
            connect();
            return new ByteArrayInputStream("not a native library".getBytes(StandardCharsets.UTF_8));
        }
    }
}
