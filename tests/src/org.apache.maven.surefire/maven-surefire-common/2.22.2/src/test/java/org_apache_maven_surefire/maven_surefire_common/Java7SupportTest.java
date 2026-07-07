/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.surefire.shade.org.apache.maven.shared.utils.io.Java7Support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Java7SupportTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createsReadsDetectsAndDeletesSymbolicLink() throws Exception {
        assertThat(Java7Support.isAtLeastJava7()).isTrue();

        Path target = tempDirectory.resolve("target.txt");
        Files.createFile(target);
        Path link = tempDirectory.resolve("link.txt");

        File createdLink = Java7Support.createSymbolicLink(link.toFile(), target.toFile());

        assertThat(createdLink.toPath()).isEqualTo(link);
        assertThat(Java7Support.exists(createdLink)).isTrue();
        assertThat(Java7Support.isSymLink(createdLink)).isTrue();
        assertThat(Java7Support.readSymbolicLink(createdLink).toPath()).isEqualTo(target);

        Java7Support.delete(createdLink);

        assertThat(Java7Support.exists(createdLink)).isFalse();
        assertThat(Files.exists(target)).isTrue();
    }
}
