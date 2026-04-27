/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_aether.aether_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.io.FileProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultServiceLocatorInnerEntryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsRegisteredServiceImplementationWithDefaultConstructor() throws IOException {
        DefaultServiceLocator locator = new DefaultServiceLocator();

        FileProcessor fileProcessor = locator.getService(FileProcessor.class);

        assertThat(fileProcessor).isNotNull();

        Path target = tempDir.resolve("nested/output.txt");
        fileProcessor.write(target.toFile(), "aether service locator");

        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("aether service locator");
    }
}
