/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.shared.utils.io.Java7Support;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Java7SupportTest {
    @TempDir
    Path directory;

    @Test
    void recognizesRegularFileAndDirectoryAsNonSymbolicLinks() throws Exception {
        Path file = Files.writeString(directory.resolve("input.txt"), "payload");
        Path childDirectory = Files.createDirectory(directory.resolve("child"));

        assertTrue(Java7Support.isJava7());
        assertFalse(Java7Support.isSymLink(file.toFile()));
        assertFalse(Java7Support.isSymLink(childDirectory.toFile()));
    }

    @Test
    void recognizesMissingPathAsNonSymbolicLink() {
        assertFalse(Java7Support.isSymLink(directory.resolve("missing.txt").toFile()));
    }
}
