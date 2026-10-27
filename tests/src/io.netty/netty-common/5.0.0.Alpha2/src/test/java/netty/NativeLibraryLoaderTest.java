/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.List;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NativeLibraryLoaderTest {
    private static final String LIBRARY_NAME = "netty_loader_resource_probe";
    private static final String NATIVE_RESOURCE_HOME = "META-INF/native/";

    @Test
    public void searchesMappedAndMacFallbackResources() {
        String originalOsName = System.getProperty("os.name");
        System.setProperty("os.name", "Mac OS X");
        RecordingClassLoader classLoader = new RecordingClassLoader();

        try {
            UnsatisfiedLinkError error = assertThrows(
                    UnsatisfiedLinkError.class,
                    () -> NativeLibraryLoader.load(LIBRARY_NAME, classLoader)
            );
            assertThat(error).hasCauseInstanceOf(IOException.class);
        } finally {
            restoreOsName(originalOsName);
        }

        String mappedResource = NATIVE_RESOURCE_HOME + System.mapLibraryName(LIBRARY_NAME);
        String fallbackSuffix = mappedResource.endsWith(".jnilib") ? ".dynlib" : ".jnilib";
        assertThat(classLoader.requestedResources()).containsExactly(
                mappedResource,
                NATIVE_RESOURCE_HOME + "lib" + LIBRARY_NAME + fallbackSuffix
        );
    }

    private static void restoreOsName(String originalOsName) {
        if (originalOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", originalOsName);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();
        private final URL unreadableNativeLibrary = createUnreadableNativeLibraryUrl();

        private RecordingClassLoader() {
            super(NativeLibraryLoaderTest.class.getClassLoader());
        }

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            return requestedResources.size() == 1 ? null : unreadableNativeLibrary;
        }

        private List<String> requestedResources() {
            return List.copyOf(requestedResources);
        }

        private static URL createUnreadableNativeLibraryUrl() {
            try {
                return new URL(null, "memory:unreadable-native-library", new UnreadableUrlStreamHandler());
            } catch (IOException e) {
                throw new AssertionError("Unable to create native library test URL", e);
            }
        }
    }

    private static final class UnreadableUrlStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) {
            return new UnreadableUrlConnection(url);
        }
    }

    private static final class UnreadableUrlConnection extends URLConnection {
        private UnreadableUrlConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            throw new IOException("Native library resource is unreadable");
        }
    }
}
