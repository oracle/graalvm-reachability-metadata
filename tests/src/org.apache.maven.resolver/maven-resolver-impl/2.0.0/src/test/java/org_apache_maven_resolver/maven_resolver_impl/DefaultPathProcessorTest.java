/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.internal.impl.DefaultPathProcessor;
import org.eclipse.aether.spi.io.PathProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultPathProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDataToNestedPathUsingPathProcessorImplementation() throws IOException {
        PathProcessor pathProcessor = new DefaultPathProcessor();

        Path target = tempDir.resolve("nested/output.txt");
        pathProcessor.write(target, "maven resolver path processor");

        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("maven resolver path processor");
    }
}
