/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.TextFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClasspathFileSourceTest {
    @TempDir
    Path rootDirectory;

    @Test
    void fallsBackToFilesystemDirectoryWhenClasspathResourceIsAbsent() throws IOException {
        Path nestedDirectory = Files.createDirectories(rootDirectory.resolve("nested"));
        Files.writeString(rootDirectory.resolve("root.txt"), "root file");
        Files.writeString(nestedDirectory.resolve("child.txt"), "child file");

        ClasspathFileSource fileSource = new ClasspathFileSource(rootDirectory.toString());
        FileSource childSource = fileSource.child("nested");
        List<String> fileContents = fileSource.listFilesRecursively().stream()
                .map(TextFile::readContentsAsString)
                .toList();

        assertThat(fileSource.exists()).isTrue();
        assertThat(fileSource.getPath()).isEqualTo(rootDirectory.toString());
        assertThat(fileSource.getUri()).isEqualTo(rootDirectory.toUri());
        assertThat(fileSource.getTextFileNamed("root.txt").readContentsAsString())
                .isEqualTo("root file");
        assertThat(childSource.exists()).isTrue();
        assertThat(childSource.getTextFileNamed("child.txt").readContentsAsString())
                .isEqualTo("child file");
        assertThat(fileContents).containsExactlyInAnyOrder("root file", "child file");
    }
}
