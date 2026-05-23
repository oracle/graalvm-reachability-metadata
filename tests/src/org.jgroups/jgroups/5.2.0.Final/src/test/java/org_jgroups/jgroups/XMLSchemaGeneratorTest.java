/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import org.jgroups.Version;
import org.jgroups.protocols.HDRS;
import org.jgroups.stack.Protocol;
import org.jgroups.util.XMLSchemaGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLSchemaGeneratorTest {
    @Test
    void discoversProtocolClassesFromContextClassLoaderResourceDirectory() throws Exception {
        Path resourceRoot = createProtocolClassResourceTree();
        try {
            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader resourceLoader = new DirectoryResourceClassLoader(resourceRoot, previousLoader);
            Thread.currentThread().setContextClassLoader(resourceLoader);
            try {
                Set<Class<?>> protocols = XMLSchemaGenerator.getClasses(Protocol.class, "org.jgroups.protocols");

                assertThat(protocols).contains(HDRS.class);
            } finally {
                Thread.currentThread().setContextClassLoader(previousLoader);
            }
        } finally {
            deleteRecursively(resourceRoot);
        }
    }

    @Test
    void mainGeneratesSchemaForDiscoveredProtocolClasses() throws Exception {
        Path resourceRoot = createProtocolClassResourceTree();
        Path outputDirectory = Files.createTempDirectory("jgroups-schema-output");
        try {
            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader resourceLoader = new DirectoryResourceClassLoader(resourceRoot, previousLoader);
            Thread.currentThread().setContextClassLoader(resourceLoader);
            try {
                XMLSchemaGenerator.main(new String[] {"-o", outputDirectory.toString()});
            } finally {
                Thread.currentThread().setContextClassLoader(previousLoader);
            }

            Path schemaFile = outputDirectory.resolve("jgroups-" + Version.major + "." + Version.minor + ".xsd");
            assertThat(schemaFile).exists();
            String schema = Files.readString(schemaFile, StandardCharsets.UTF_8);
            assertThat(schema).contains("name=\"HDRS\"")
                    .contains("name=\"print_down\"")
                    .contains("name=\"print_up\"");
        } finally {
            deleteRecursively(resourceRoot);
            deleteRecursively(outputDirectory);
        }
    }

    private static Path createProtocolClassResourceTree() throws IOException {
        Path root = Files.createTempDirectory("jgroups-schema-resources");
        Path protocolDirectory = Files.createDirectories(root.resolve("org/jgroups/protocols"));
        Files.createFile(protocolDirectory.resolve("HDRS.class"));

        for (String suffix : new String[] {"pbcast", "relay", "rules", "dns", "kubernetes"}) {
            Files.createDirectories(protocolDirectory.resolve(suffix));
        }
        return root;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private static final class DirectoryResourceClassLoader extends ClassLoader {
        private final Path root;

        private DirectoryResourceClassLoader(Path newRoot, ClassLoader parent) {
            super(parent);
            root = newRoot;
        }

        @Override
        public URL getResource(String name) {
            Path resource = root.resolve(name);
            if (Files.exists(resource)) {
                try {
                    return resource.toUri().toURL();
                } catch (IOException e) {
                    throw new IllegalStateException("Could not create URL for " + resource, e);
                }
            }
            return super.getResource(name);
        }
    }
}
