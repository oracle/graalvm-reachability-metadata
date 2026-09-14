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
import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.integration.file.support.FileUtils;

public class FileUtilsTest {

    @Test
    void removesDirectoriesWithoutSorting(@TempDir Path directory) throws IOException {
        Path firstFile = Files.writeString(directory.resolve("first.txt"), "first");
        Path nestedDirectory = Files.createDirectory(directory.resolve("nested"));
        Path secondFile = Files.writeString(directory.resolve("second.txt"), "second");

        File[] files = FileUtils.purgeUnwantedElements(
                new File[] {firstFile.toFile(), nestedDirectory.toFile(), secondFile.toFile()},
                File::isDirectory, null);

        assertThat(files).extracting(File::getName).containsExactly("first.txt", "second.txt");
    }

    @Test
    void removesDirectoriesAndSortsRemainingFiles(@TempDir Path directory) throws IOException {
        Path firstFile = Files.writeString(directory.resolve("first.txt"), "first");
        Path nestedDirectory = Files.createDirectory(directory.resolve("nested"));
        Path secondFile = Files.writeString(directory.resolve("second.txt"), "second");

        File[] files = FileUtils.purgeUnwantedElements(
                new File[] {secondFile.toFile(), nestedDirectory.toFile(), firstFile.toFile()},
                File::isDirectory, Comparator.comparing(File::getName));

        assertThat(files).extracting(File::getName).containsExactly("first.txt", "second.txt");
    }
}
