/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.components.io.attributes.Java7Reflector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Java7ReflectorTest {
    @Test
    public void readsPosixFileAttributesThroughJava7ReflectionBridge() throws IOException {
        Path file = Files.createTempFile("plexus-io-java7-reflector", ".txt");
        try {
            assertThat(Java7Reflector.isJava7()).isTrue();

            Object attributes = Java7Reflector.getPosixFileAttributes(file.toFile());

            assertThat(attributes).isInstanceOf(Java7Reflector.posixFileAttributes);
            assertThat(Java7Reflector.getOwnerUserName(attributes)).isNotBlank();
            assertThat(Java7Reflector.getOwnerGroupName(attributes)).isNotBlank();
            assertThat(Java7Reflector.getPermissions(attributes)).hasSize(9);
        } catch (RuntimeException e) {
            assertThat(rootCause(e)).isInstanceOf(UnsupportedOperationException.class);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
