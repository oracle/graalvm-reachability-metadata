/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.surefire.shade.org.apache.maven.shared.utils.io.Java7Support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Java7SupportTest {

    @Test
    public void symbolicLinkLifecycleUsesJava7SupportOperations(@TempDir final Path tempDirectory) throws IOException {
        final Path target = tempDirectory.resolve("target.txt");
        final Path link = tempDirectory.resolve("link.txt");
        Files.write(target, "surefire".getBytes(StandardCharsets.UTF_8));

        final Thread currentThread = Thread.currentThread();
        final ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(Java7SupportTest.class.getClassLoader());
        try {
            assertThat(Java7Support.isAtLeastJava7()).isTrue();
            assertThat(Java7Support.exists(link.toFile())).isFalse();

            final File createdLink = Java7Support.createSymbolicLink(link.toFile(), target.toFile());

            assertThat(createdLink).isEqualTo(link.toFile());
            assertThat(Java7Support.exists(createdLink)).isTrue();
            assertThat(Java7Support.isSymLink(createdLink)).isTrue();
            assertThat(Java7Support.readSymbolicLink(createdLink)).isEqualTo(target.toFile());
            assertThat(new String(Files.readAllBytes(link), StandardCharsets.UTF_8)).isEqualTo("surefire");

            Java7Support.delete(createdLink);

            assertThat(Java7Support.exists(createdLink)).isFalse();
            assertThat(Files.exists(target)).isTrue();
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }
}
