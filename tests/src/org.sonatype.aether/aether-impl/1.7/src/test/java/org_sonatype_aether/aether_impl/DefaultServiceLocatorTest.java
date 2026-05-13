/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_aether.aether_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.spi.io.FileProcessor;

public class DefaultServiceLocatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsRegisteredFileProcessorService() {
        DefaultServiceLocator locator = new DefaultServiceLocator();

        List<FileProcessor> fileProcessors = locator.getServices(FileProcessor.class);

        Path nestedDirectory = temporaryDirectory.resolve("parent").resolve("child");
        assertThat(fileProcessors).hasSize(1);
        assertThat(fileProcessors.get(0).mkdirs(nestedDirectory.toFile())).isTrue();
        assertThat(nestedDirectory).isDirectory();
    }
}
