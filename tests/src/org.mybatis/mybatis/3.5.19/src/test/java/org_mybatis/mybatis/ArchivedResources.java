/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class ArchivedResources implements AutoCloseable {
    private final ClassLoader originalLoader;

    public ArchivedResources(Path archive, String packagePath, String... resources) throws IOException {
        originalLoader = Thread.currentThread().getContextClassLoader();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new JarEntry(packagePath + "/"));
            output.closeEntry();
            for (String resource : resources) {
                String name = packagePath + "/" + resource;
                try (InputStream input = Objects.requireNonNull(originalLoader.getResourceAsStream(name), name)) {
                    output.putNextEntry(new JarEntry(name));
                    input.transferTo(output);
                    output.closeEntry();
                }
            }
        }
        URL archiveUrl = archive.toUri().toURL();
        // Redirect only resource enumeration; classes still come from the parent loader.
        ClassLoader resourceLoader = new ClassLoader(originalLoader) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return packagePath.equals(name) ? Collections.enumeration(Collections.singletonList(archiveUrl))
                        : super.getResources(name);
            }
        };
        Thread.currentThread().setContextClassLoader(resourceLoader);
    }

    @Override
    public void close() {
        Thread.currentThread().setContextClassLoader(originalLoader);
    }
}
