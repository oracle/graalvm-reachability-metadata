/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.Version;
import org.jgroups.util.XMLSchemaGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class XMLSchemaGeneratorTest {
    private static final String PROTOCOLS_PACKAGE_PATH = "org/jgroups/protocols";
    private static final String PROTOCOL_CLASS_FILE = "SHUFFLE.class";

    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void mainGeneratesSchemaForExplodedProtocolClasses(@TempDir Path tempDir) throws Exception {
        Path protocolsDirectory = tempDir.resolve(PROTOCOLS_PACKAGE_PATH);
        Files.createDirectories(protocolsDirectory);
        Files.createFile(protocolsDirectory.resolve(PROTOCOL_CLASS_FILE));

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(
                    new ExplodedProtocolResourceClassLoader(originalClassLoader, protocolsDirectory));

            XMLSchemaGenerator.main(new String[] {"-o", tempDir.toString()});
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        Path schema = tempDir.resolve("jgroups-" + Version.major + "." + Version.minor + ".xsd");
        assertThat(schema).exists();
        assertThat(Files.readString(schema, StandardCharsets.UTF_8))
                .contains("<xs:element name=\"SHUFFLE\">")
                .contains("name=\"max_size\"")
                .contains("type=\"xs:string\"")
                .contains("Reorder up messages and message batches");
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    private static final class ExplodedProtocolResourceClassLoader extends ClassLoader {
        private final Path protocolsDirectory;

        private ExplodedProtocolResourceClassLoader(ClassLoader parent, Path protocolsDirectory) {
            super(parent);
            this.protocolsDirectory = protocolsDirectory;
        }

        @Override
        public URL getResource(String name) {
            if (PROTOCOLS_PACKAGE_PATH.equals(name)) {
                try {
                    return protocolsDirectory.toUri().toURL();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot expose protocol resource directory", e);
                }
            }
            return super.getResource(name);
        }
    }
}
