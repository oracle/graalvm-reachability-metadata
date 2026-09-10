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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.ChainFileListFilter;
import org.springframework.integration.file.filters.FileListFilter;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;

public class ChainFileListFilterTest {

    @Test
    void filtersFilesInOrderAndPreventsDuplicates(@TempDir Path directory) throws IOException {
        File firstReport = Files.writeString(directory.resolve("first.txt"), "first").toFile();
        File ignoredFile = Files.writeString(directory.resolve("ignored.log"), "ignored").toFile();
        File secondReport = Files.writeString(directory.resolve("second.txt"), "second").toFile();
        FileListFilter<File> patternFilter = new SimplePatternFileListFilter("*.txt");
        FileListFilter<File> acceptOnceFilter = new AcceptOnceFileListFilter<>();
        ChainFileListFilter<File> filter = new ChainFileListFilter<>(List.of(patternFilter, acceptOnceFilter));
        File[] files = {firstReport, ignoredFile, secondReport};

        assertThat(filter.filterFiles(files)).containsExactly(firstReport, secondReport);
        assertThat(filter.filterFiles(files)).isEmpty();
    }
}
