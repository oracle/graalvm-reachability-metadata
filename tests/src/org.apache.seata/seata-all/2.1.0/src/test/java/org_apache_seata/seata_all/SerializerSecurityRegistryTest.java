/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.seata.core.protocol.HeartbeatMessage;
import org.apache.seata.core.protocol.RegisterRMRequest;
import org.apache.seata.core.protocol.transaction.GlobalBeginRequest;
import org.apache.seata.core.serializer.SerializerSecurityRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializerSecurityRegistryTest {
    private static final String PROTOCOL_RESOURCE_PATH = "org/apache/seata/core/protocol";

    @Test
    void allowClassTypeInitializationScansProtocolResourcesUsingContextClassLoader(@TempDir Path tempDir) throws Exception {
        Path protocolDirectory = Files.createDirectories(tempDir.resolve(PROTOCOL_RESOURCE_PATH));
        Path transactionDirectory = Files.createDirectories(protocolDirectory.resolve("transaction"));
        Files.write(protocolDirectory.resolve("RegisterRMRequest.class"), new byte[0]);
        Files.write(protocolDirectory.resolve("HeartbeatMessage.class"), new byte[0]);
        Files.write(protocolDirectory.resolve("AbstractIgnored.class"), new byte[0]);
        Files.write(protocolDirectory.resolve("README.txt"), new byte[0]);
        Files.write(transactionDirectory.resolve("GlobalBeginRequest.class"), new byte[0]);

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        TrackingProtocolClassLoader trackingClassLoader =
                new TrackingProtocolClassLoader(originalContextClassLoader, protocolDirectory);
        Thread.currentThread().setContextClassLoader(trackingClassLoader);

        try {
            Set<Class<?>> allowedClassTypes = SerializerSecurityRegistry.getAllowClassType();

            assertThat(trackingClassLoader.getRequestedResourceNames())
                    .contains(PROTOCOL_RESOURCE_PATH);
            assertThat(trackingClassLoader.getLoadedProtocolClassNames())
                    .contains(
                            RegisterRMRequest.class.getName(),
                            HeartbeatMessage.class.getName(),
                            GlobalBeginRequest.class.getName())
                    .doesNotContain("org.apache.seata.core.protocol.AbstractIgnored");
            assertThat(allowedClassTypes)
                    .contains(RegisterRMRequest.class, HeartbeatMessage.class, GlobalBeginRequest.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class TrackingProtocolClassLoader extends ClassLoader {
        private final Path protocolDirectory;
        private final Set<String> requestedResourceNames = new LinkedHashSet<>();
        private final Set<String> loadedProtocolClassNames = new LinkedHashSet<>();

        private TrackingProtocolClassLoader(ClassLoader parent, Path protocolDirectory) {
            super(parent);
            this.protocolDirectory = protocolDirectory;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResourceNames.add(name);
            if (PROTOCOL_RESOURCE_PATH.equals(name)) {
                return Collections.enumeration(List.of(protocolDirectory.toUri().toURL()));
            }
            ClassLoader parent = getParent();
            if (parent != null) {
                return parent.getResources(name);
            }
            return ClassLoader.getSystemResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.apache.seata.core.protocol.")) {
                loadedProtocolClassNames.add(name);
            }
            return super.loadClass(name, resolve);
        }

        private Set<String> getRequestedResourceNames() {
            return requestedResourceNames;
        }

        private Set<String> getLoadedProtocolClassNames() {
            return loadedProtocolClassNames;
        }
    }
}
