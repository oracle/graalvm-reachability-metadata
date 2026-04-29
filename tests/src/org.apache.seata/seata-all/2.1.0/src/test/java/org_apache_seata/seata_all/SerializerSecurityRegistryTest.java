/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

import org.apache.seata.core.protocol.HeartbeatMessage;
import org.apache.seata.core.protocol.transaction.BranchCommitRequest;
import org.apache.seata.core.serializer.SerializerSecurityRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SerializerSecurityRegistryTest {
    private static final String PROTOCOL_RESOURCE = "org/apache/seata/core/protocol";

    @TempDir
    Path temporaryDirectory;

    @Test
    void registryScansProtocolPackageResourcesAndAllowsProtocolClasses() throws IOException {
        Path protocolDirectory = createProtocolClassPath();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader protocolResourceClassLoader = new ProtocolResourceClassLoader(
                originalClassLoader,
                protocolDirectory.toUri().toURL());

        Thread.currentThread().setContextClassLoader(protocolResourceClassLoader);
        try {
            Set<Class<?>> allowedTypes = SerializerSecurityRegistry.getAllowClassType();

            assertThat(allowedTypes).contains(HeartbeatMessage.class, BranchCommitRequest.class, String.class);
            assertThat(SerializerSecurityRegistry.getAllowClassPattern())
                    .contains("org.apache.seata.core.protocol.HeartbeatMessage", "org.apache.seata.*");
            assertThat(SerializerSecurityRegistry.getDenyClassPattern()).contains("java.lang.Runtime");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private Path createProtocolClassPath() throws IOException {
        Path protocolDirectory = temporaryDirectory.resolve(PROTOCOL_RESOURCE);
        Files.createDirectories(protocolDirectory.resolve("transaction"));
        Files.createFile(protocolDirectory.resolve("HeartbeatMessage.class"));
        Files.createFile(protocolDirectory.resolve("transaction").resolve("BranchCommitRequest.class"));
        return protocolDirectory;
    }

    private static class ProtocolResourceClassLoader extends ClassLoader {
        private final URL protocolResourceUrl;

        ProtocolResourceClassLoader(ClassLoader parent, URL protocolResourceUrl) {
            super(parent);
            this.protocolResourceUrl = protocolResourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (PROTOCOL_RESOURCE.equals(name)) {
                return Collections.enumeration(Collections.singleton(protocolResourceUrl));
            }
            return super.getResources(name);
        }
    }
}
