/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import javassist.LoaderClassPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderClassPathTest {
    private static final String LOADER_CLASS_PATH_RESOURCE = "javassist/LoaderClassPath.class";

    @Test
    void opensClassfileThroughSuppliedClassLoader() throws IOException {
        RecordingClassLoader loader = new RecordingClassLoader();
        LoaderClassPath classPath = new LoaderClassPath(loader);

        try (InputStream stream = classPath.openClassfile(LoaderClassPath.class.getName())) {
            assertThat(loader.openedResourceName()).isEqualTo(LOADER_CLASS_PATH_RESOURCE);
            assertThat(stream).isNotNull();
            assertThat(stream.read()).isEqualTo(0xCA);
            assertThat(stream.read()).isEqualTo(0xFE);
            assertThat(stream.read()).isEqualTo(0xBA);
            assertThat(stream.read()).isEqualTo(0xBE);
        }
    }

    @Test
    void findsClassfileThroughSuppliedClassLoader() {
        RecordingClassLoader loader = new RecordingClassLoader();
        LoaderClassPath classPath = new LoaderClassPath(loader);

        URL resource = classPath.find(LoaderClassPath.class.getName());

        assertThat(loader.foundResourceName()).isEqualTo(LOADER_CLASS_PATH_RESOURCE);
        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).isEqualTo("memory:/" + LOADER_CLASS_PATH_RESOURCE);
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private static final byte[] CLASSFILE_HEADER = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        private final URLStreamHandler handler = new MemoryUrlStreamHandler();
        private String openedResourceName;
        private String foundResourceName;

        @Override
        public InputStream getResourceAsStream(String name) {
            openedResourceName = name;
            return new ByteArrayInputStream(CLASSFILE_HEADER);
        }

        @Override
        public URL getResource(String name) {
            foundResourceName = name;
            try {
                return new URL(null, "memory:/" + name, handler);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Unable to create in-memory resource URL", e);
            }
        }

        String openedResourceName() {
            return openedResourceName;
        }

        String foundResourceName() {
            return foundResourceName;
        }
    }

    private static final class MemoryUrlStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) {
            return new MemoryURLConnection(url);
        }
    }

    private static final class MemoryURLConnection extends URLConnection {
        private MemoryURLConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
        }
    }
}
