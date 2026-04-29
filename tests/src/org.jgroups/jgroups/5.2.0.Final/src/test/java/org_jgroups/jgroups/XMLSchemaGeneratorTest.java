/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.stack.Protocol;
import org.jgroups.util.XMLSchemaGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLSchemaGeneratorTest {
    private static final String PROTOCOL_PACKAGE_PATH = "org/jgroups/protocols";
    private static final String PROTOCOL_PACKAGE_NAME = "org.jgroups.protocols";
    private static final String PROTOCOL_CLASS_FILE = "UDP.class";

    @TempDir
    private Path tempDir;

    @Test
    void generatesSchemaFromProtocolDirectoryResource() throws Exception {
        Path protocolDirectory = tempDir.resolve(PROTOCOL_PACKAGE_PATH);
        Files.createDirectories(protocolDirectory);
        Files.write(protocolDirectory.resolve(PROTOCOL_CLASS_FILE), new byte[0]);

        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        ProtocolDirectoryClassLoader protocolLoader = new ProtocolDirectoryClassLoader(
            previousLoader, protocolDirectory);
        Thread.currentThread().setContextClassLoader(protocolLoader);
        try {
            XMLSchemaGenerator.main(new String[] {"-o", tempDir.toString()});
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }

        List<Path> generatedSchemas;
        try (Stream<Path> files = Files.list(tempDir)) {
            generatedSchemas = files
                .filter(path -> path.getFileName().toString().endsWith(".xsd"))
                .toList();
        }
        assertThat(generatedSchemas).hasSize(1);

        String schema = Files.readString(generatedSchemas.get(0), StandardCharsets.UTF_8);
        assertThat(schema).contains("<xs:element name=\"UDP\">");
        assertThat(schema).contains("name=\"bind_addr\"");
        assertThat(schema).contains("name=\"level\"");
    }

    @Test
    void discoversConcreteProtocolClassesFromDirectoryResource() throws Exception {
        Path protocolDirectory = tempDir.resolve(PROTOCOL_PACKAGE_PATH);
        Files.createDirectories(protocolDirectory);
        Files.write(protocolDirectory.resolve(PROTOCOL_CLASS_FILE), new byte[0]);

        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        ProtocolDirectoryClassLoader protocolLoader = new ProtocolDirectoryClassLoader(
            previousLoader, protocolDirectory);
        Thread.currentThread().setContextClassLoader(protocolLoader);
        try {
            assertThat(XMLSchemaGenerator.getClasses(Protocol.class, PROTOCOL_PACKAGE_NAME))
                .extracting(Class::getName)
                .contains("org.jgroups.protocols.UDP");
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    private static final class ProtocolDirectoryClassLoader extends ClassLoader {
        private final Path protocolDirectory;

        private ProtocolDirectoryClassLoader(ClassLoader parent, Path protocolDirectory) {
            super(parent);
            this.protocolDirectory = protocolDirectory;
        }

        @Override
        public URL getResource(String name) {
            if (PROTOCOL_PACKAGE_PATH.equals(name)) {
                try {
                    return protocolDirectory.toUri().toURL();
                } catch (IOException e) {
                    throw new IllegalStateException("Could not expose protocol test directory", e);
                }
            }
            return super.getResource(name);
        }
    }
}
