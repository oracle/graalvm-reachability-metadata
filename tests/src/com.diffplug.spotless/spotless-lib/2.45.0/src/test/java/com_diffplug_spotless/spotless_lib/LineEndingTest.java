/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.LineEnding;

public class LineEndingTest {
    @Test
    void gitAttributesPolicyUsesFileSpecificLineEndings() throws Exception {
        final Path projectDir = Files.createTempDirectory("spotless-line-ending-");
        final Path windowsFile = createFile(projectDir, "windows.txt");
        final Path unixFile = createFile(projectDir, "unix.txt");
        writeGitAttributes(projectDir);

        final LineEnding.Policy policy = LineEnding.GIT_ATTRIBUTES.createPolicy(
                projectDir.toFile(),
                () -> List.of(windowsFile.toFile(), unixFile.toFile()));

        assertThat(policy.getEndingFor(windowsFile.toFile())).isEqualTo(LineEnding.WINDOWS.str());
        assertThat(policy.getEndingFor(unixFile.toFile())).isEqualTo(LineEnding.UNIX.str());
        assertThat(policy.isUnix(unixFile.toFile())).isTrue();
        assertThat(policy.isUnix(windowsFile.toFile())).isFalse();
    }

    @Test
    void fastGitAttributesPolicyReusesFirstDiscoveredLineEnding() throws Exception {
        final Path projectDir = Files.createTempDirectory("spotless-line-ending-fast-");
        final Path windowsFile = createFile(projectDir, "windows.txt");
        final Path unixFile = createFile(projectDir, "unix.txt");
        writeGitAttributes(projectDir);

        final LineEnding.Policy policy = LineEnding.GIT_ATTRIBUTES_FAST_ALLSAME.createPolicy(
                projectDir.toFile(),
                () -> List.of(windowsFile.toFile(), unixFile.toFile()));

        assertThat(policy.getEndingFor(windowsFile.toFile())).isEqualTo(LineEnding.WINDOWS.str());
        assertThat(policy.getEndingFor(unixFile.toFile())).isEqualTo(LineEnding.WINDOWS.str());
        assertThat(policy.isUnix(unixFile.toFile())).isFalse();
    }

    private static Path createFile(Path projectDir, String fileName) throws Exception {
        final Path file = projectDir.resolve(fileName);
        Files.writeString(file, "alpha\nbeta\n", StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        return file;
    }

    private static void writeGitAttributes(Path projectDir) throws Exception {
        final Path attributes = projectDir.resolve(".gitattributes");
        Files.writeString(
                attributes,
                "windows.txt text eol=crlf\nunix.txt text eol=lf\n",
                StandardCharsets.UTF_8);
        attributes.toFile().deleteOnExit();
        projectDir.toFile().deleteOnExit();
    }
}
