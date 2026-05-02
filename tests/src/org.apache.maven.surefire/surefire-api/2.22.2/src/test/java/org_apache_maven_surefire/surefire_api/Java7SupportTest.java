/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import org.apache.maven.surefire.shade.org.apache.maven.shared.utils.io.Java7Support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class Java7SupportTest {
    @TempDir
    Path tempDir;

    @Test
    void managesSymbolicLinksThroughJava7Support() throws Exception {
        assertThat(Java7Support.isAtLeastJava7()).isTrue();
        assertThat(Java7Support.isJava7()).isTrue();

        Path target = tempDir.resolve("target.txt");
        Files.writeString(target, "content used by symbolic link test");

        File targetFile = target.toFile();
        File linkFile = tempDir.resolve("target-link.txt").toFile();
        File missingFile = tempDir.resolve("missing.txt").toFile();

        assertThat(Java7Support.exists(missingFile)).isFalse();
        assertThat(Java7Support.exists(targetFile)).isTrue();
        assertThat(Java7Support.isSymLink(targetFile)).isFalse();

        File createdLink = Java7Support.createSymbolicLink(linkFile, targetFile);
        assertThat(createdLink).isEqualTo(linkFile);
        assertThat(Java7Support.exists(linkFile)).isTrue();
        assertThat(Java7Support.isSymLink(linkFile)).isTrue();
        assertThat(Java7Support.readSymbolicLink(linkFile)).isEqualTo(targetFile);

        Java7Support.delete(linkFile);
        assertThat(Java7Support.exists(linkFile)).isFalse();

        Java7Support.delete(targetFile);
        assertThat(Java7Support.exists(targetFile)).isFalse();
    }
}
