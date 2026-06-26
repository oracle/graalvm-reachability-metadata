/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFile.Kind;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFiles;
import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class RestartClassLoaderTest {

    @Test
    void loadClassFallsBackToParentClassLoader() throws Exception {
        RestartClassLoader classLoader = new ParentOnlyRestartClassLoader(getClass().getClassLoader());

        Class<?> loadedClass = classLoader.loadClass("java.lang.String");

        assertThat(loadedClass).isSameAs(String.class);
        assertThat(classLoader.isClassReloadable(loadedClass)).isFalse();
    }

    @Test
    void getResourceFallsBackToParentClassLoader() {
        URL parentResource = resourceUrl("parent-resource");
        ParentResourceClassLoader parent = new ParentResourceClassLoader(parentResource, List.of());
        RestartClassLoader classLoader = new ParentOnlyRestartClassLoader(parent);

        URL resource = classLoader.getResource("example.txt");

        assertThat(resource).isSameAs(parentResource);
    }

    @Test
    void getResourcesShadowsFirstParentResourceWithUpdatedFile() throws Exception {
        URL firstParentResource = resourceUrl("parent-one");
        URL secondParentResource = resourceUrl("parent-two");
        ClassLoaderFiles updatedFiles = new ClassLoaderFiles();
        updatedFiles.addFile("example.txt", new ClassLoaderFile(Kind.MODIFIED, bytes("updated")));
        RestartClassLoader classLoader = new ParentOnlyRestartClassLoader(
                new ParentResourceClassLoader(firstParentResource, List.of(firstParentResource, secondParentResource)),
                updatedFiles);

        Enumeration<URL> resources = classLoader.getResources("example.txt");

        List<URL> resourceList = Collections.list(resources);
        assertThat(resourceList).satisfiesExactly(
                (resource) -> assertThat(read(resource)).isEqualTo("updated"),
                (resource) -> assertThat(read(resource)).isEqualTo("parent-two"));
    }

    private static URL resourceUrl(String contents) {
        try {
            return new URL(null, "memory:/" + contents, new StringUrlStreamHandler(contents));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] bytes(String contents) {
        return contents.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(URL url) {
        try (InputStream inputStream = url.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class ParentOnlyRestartClassLoader extends RestartClassLoader {

        private ParentOnlyRestartClassLoader(ClassLoader parent) {
            this(parent, new ClassLoaderFiles());
        }

        private ParentOnlyRestartClassLoader(ClassLoader parent, ClassLoaderFiles updatedFiles) {
            super(parent, new URL[0], updatedFiles);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public URL findResource(String name) {
            return null;
        }

    }

    private static final class ParentResourceClassLoader extends ClassLoader {

        private final URL resource;

        private final List<URL> resources;

        private ParentResourceClassLoader(URL resource, List<URL> resources) {
            super(RestartClassLoaderTest.class.getClassLoader());
            this.resource = resource;
            this.resources = resources;
        }

        @Override
        public URL getResource(String name) {
            return this.resource;
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(this.resources);
        }

    }

    private static final class StringUrlStreamHandler extends URLStreamHandler {

        private final String contents;

        private StringUrlStreamHandler(String contents) {
            this.contents = contents;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new StringUrlConnection(url, this.contents);
        }

    }

    private static final class StringUrlConnection extends URLConnection {

        private final String contents;

        private StringUrlConnection(URL url, String contents) {
            super(url);
            this.contents = contents;
        }

        @Override
        public void connect() throws IOException {
            this.connected = true;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes(this.contents));
        }

    }

}
