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
import java.util.stream.Stream;

import org.jgroups.protocols.HDRS;
import org.jgroups.util.PropertiesToAsciidoc;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesToAsciidocTest {
    @Test
    void mainGeneratesTableForDiscoveredProtocolProperties() throws Exception {
        Path resourceRoot = createJGroupsClassResourceTree();
        Path documentDirectory = Files.createTempDirectory("jgroups-asciidoc-output");
        try {
            Path protocolsDocument = documentDirectory.resolve("protocols.adoc");
            Path installationDocument = documentDirectory.resolve("installation.adoc");
            Files.writeString(protocolsDocument, "== Protocols\n${HDRS}\n", StandardCharsets.UTF_8);
            Files.writeString(installationDocument, "== Installation\n${Unsupported}\n${Experimental}\n",
                    StandardCharsets.UTF_8);

            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader resourceLoader = new DirectoryResourceClassLoader(resourceRoot, previousLoader);
            Thread.currentThread().setContextClassLoader(resourceLoader);
            try {
                PropertiesToAsciidoc.main(new String[] {protocolsDocument.toString(), installationDocument.toString()});
            }
            finally {
                Thread.currentThread().setContextClassLoader(previousLoader);
            }

            String generatedProtocols = Files.readString(Path.of(protocolsDocument + ".tmp"), StandardCharsets.UTF_8);
            assertThat(generatedProtocols)
                    .contains(".HDRS")
                    .contains("|print_down|Enables printing of down messages")
                    .contains("|print_up|Enables printing of up (received) messages")
                    .doesNotContain("${HDRS}");
        }
        finally {
            deleteRecursively(resourceRoot);
            deleteRecursively(documentDirectory);
        }
    }

    private static Path createJGroupsClassResourceTree() throws IOException {
        Path root = Files.createTempDirectory("jgroups-asciidoc-resources");
        Path jgroupsDirectory = Files.createDirectories(root.resolve("org/jgroups"));
        Path protocolsDirectory = Files.createDirectories(jgroupsDirectory.resolve("protocols"));
        Files.createFile(protocolsDirectory.resolve(HDRS.class.getSimpleName() + ".class"));

        for (String suffix : new String[] {"pbcast", "relay", "rules", "dns"}) {
            Files.createDirectories(protocolsDirectory.resolve(suffix));
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
                }
                catch (IOException e) {
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
                }
                catch (IOException e) {
                    throw new IllegalStateException("Could not create URL for " + resource, e);
                }
            }
            return super.getResource(name);
        }
    }
}
