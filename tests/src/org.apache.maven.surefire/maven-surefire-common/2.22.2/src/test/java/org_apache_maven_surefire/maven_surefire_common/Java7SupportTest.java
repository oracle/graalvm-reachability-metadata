/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.maven.shared.utils.io.Java7Support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class Java7SupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsReadsIdentifiesAndDeletesSymbolicLink() throws Exception {
        File target = temporaryDirectory.resolve("target.txt").toFile();
        File symlink = temporaryDirectory.resolve("target-link.txt").toFile();
        Files.writeString(target.toPath(), "linked content");

        assertThat(Java7Support.isAtLeastJava7()).isTrue();
        assertThat(Java7Support.isJava7()).isTrue();
        assertThat(Java7Support.exists(target)).isTrue();
        assertThat(Java7Support.exists(symlink)).isFalse();
        assertThat(Java7Support.isSymLink(target)).isFalse();

        File createdSymlink = Java7Support.createSymbolicLink(symlink, target);

        assertThat(createdSymlink).isEqualTo(symlink);
        assertThat(Java7Support.exists(symlink)).isTrue();
        assertThat(Java7Support.isSymLink(symlink)).isTrue();
        assertThat(Java7Support.readSymbolicLink(symlink)).isEqualTo(target);

        Java7Support.delete(symlink);

        assertThat(Java7Support.exists(symlink)).isFalse();
        assertThat(target).exists();
    }
}
