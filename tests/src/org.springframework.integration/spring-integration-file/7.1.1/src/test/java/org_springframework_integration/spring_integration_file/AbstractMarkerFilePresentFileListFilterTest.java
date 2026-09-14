/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.integration.file.filters.FileSystemMarkerFilePresentFileListFilter;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;

public class AbstractMarkerFilePresentFileListFilterTest {

    @Test
    void acceptsFilesWithMatchingMarker(@TempDir Path directory) throws IOException {
        File payload = Files.writeString(directory.resolve("report.txt"), "payload").toFile();
        File marker = Files.writeString(directory.resolve("report.txt.complete"), "complete").toFile();
        FileSystemMarkerFilePresentFileListFilter filter =
                new FileSystemMarkerFilePresentFileListFilter(new SimplePatternFileListFilter("*.txt"));

        assertThat(filter.filterFiles(new File[] {payload, marker})).containsExactly(payload);
    }
}
