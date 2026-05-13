/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.shared.utils.io.Java7Support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Java7SupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsRegularFileIsNotSymbolicLink() throws IOException {
        Path regularFile = Files.createFile(temporaryDirectory.resolve("regular-file.txt"));
        File file = regularFile.toFile();

        boolean symbolicLink = Java7Support.isSymLink(file);

        assertThat(symbolicLink).isFalse();
    }
}
