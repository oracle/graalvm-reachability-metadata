/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_os;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.os.ExecutableFinder;

public class ExecutableFinderTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void findsExecutableByDirectPath() throws IOException {
        Path executable = temporaryDirectory.resolve("selenium-os-test-executable");
        Files.writeString(executable, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        assertThat(executable.toFile().setExecutable(true)).isTrue();

        assertThat(new ExecutableFinder().find(executable.toString())).isEqualTo(executable.toString());
    }

    @Test
    void doesNotTreatDirectoryAsExecutable() {
        assertThat(new ExecutableFinder().find(temporaryDirectory.toString())).isNull();
    }
}
