/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_aether.aether_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.spi.io.FileProcessor;

public class DefaultServiceLocatorTest {
    @TempDir
    Path tempDir;

    @Test
    void locatesAndReusesFileProcessorForRepositoryFiles() throws IOException {
        DefaultServiceLocator locator = new DefaultServiceLocator();

        List<FileProcessor> processors = locator.getServices(FileProcessor.class);

        assertThat(processors).hasSize(1);
        FileProcessor processor = processors.get(0);
        assertThat(locator.getService(FileProcessor.class)).isSameAs(processor);
        assertThat(locator.getServices(FileProcessor.class)).isSameAs(processors);

        Path source = tempDir.resolve("source.txt");
        String content = "repository artifact contents\n";
        processor.write(source.toFile(), content);
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(content);

        Path target = tempDir.resolve("repository").resolve("artifacts").resolve("copied.txt");
        long copiedBytes = processor.copy(source.toFile(), target.toFile(), null);

        assertThat(copiedBytes).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(content);
    }
}
