/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

public class FileUtilInnerFileAccessResTest {
    @Test
    void resourceAccessOpensClassRelativeResource() throws Exception {
        FileAccess fileAccess = new ResourceBackedFileAccess();
        String resourceName = "FileUtilInnerFileAccessResTest.class";

        assertThat(fileAccess.isStreamElement(resourceName)).isTrue();

        try (InputStream inputStream = fileAccess.openInputStreamElement(resourceName)) {
            assertThat(inputStream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    void resourceAccessOpensContextClassLoaderResourceAfterClassLookupMiss() throws Exception {
        FileAccess fileAccess = new FileUtil.FileAccessRes();
        String resourceName = "file-access-res-context-loader-resource.txt";
        byte[] resourceContent = "context-loader-resource".getBytes(StandardCharsets.UTF_8);
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader resourceClassLoader = new InMemoryResourceClassLoader(resourceName, resourceContent);

        try {
            currentThread.setContextClassLoader(resourceClassLoader);

            assertThat(fileAccess.isStreamElement(resourceName)).isTrue();

            try (InputStream inputStream = fileAccess.openInputStreamElement(resourceName)) {
                assertThat(inputStream.readAllBytes()).isEqualTo(resourceContent);
            }
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ResourceBackedFileAccess extends FileUtil.FileAccessRes {
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;

        private InMemoryResourceClassLoader(String resourceName, byte[] resourceContent) {
            super(FileUtilInnerFileAccessResTest.class.getClassLoader());
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.clone();
        }

        @Override
        public URL getResource(String name) {
            if (!resourceName.equals(name)) {
                return super.getResource(name);
            }

            try {
                return new URL(null, "memory://file-access-res/" + name,
                        new ResourceStreamHandler(resourceContent));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!resourceName.equals(name)) {
                return super.getResourceAsStream(name);
            }

            return new ByteArrayInputStream(resourceContent);
        }
    }

    private static final class ResourceStreamHandler extends URLStreamHandler {
        private final byte[] resourceContent;

        private ResourceStreamHandler(byte[] resourceContent) {
            this.resourceContent = resourceContent.clone();
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(resourceContent);
                }
            };
        }
    }
}
