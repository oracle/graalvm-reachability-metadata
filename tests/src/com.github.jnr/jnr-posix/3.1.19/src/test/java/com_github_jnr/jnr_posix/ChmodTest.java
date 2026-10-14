/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_posix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ChmodTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void appliesOwnerPermissionsThroughPureJavaPosix() throws IOException {
        Path file = temporaryDirectory.resolve("executable.txt");
        Files.writeString(file, "payload");
        POSIX posix = POSIXFactory.getJavaPOSIX();

        int result = posix.chmod(file.toString(), 0700);

        assertThat(result).isZero();
        assertThat(file.toFile().canRead()).isTrue();
        assertThat(file.toFile().canWrite()).isTrue();
        assertThat(file.toFile().canExecute()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("payload");
    }
}
