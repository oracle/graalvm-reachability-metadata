/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private static void restoreOsName(String originalOsName) {
        if (originalOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", originalOsName);
        }
    }

    private static final class NativeLibraryLoaderResourceClassLoader extends ClassLoader {
        private final URL invalidNativeLibraryUrl;
        private final List<String> requestedResources = new ArrayList<>();

        private NativeLibraryLoaderResourceClassLoader() {
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

        private List<String> requestedResources() {
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
