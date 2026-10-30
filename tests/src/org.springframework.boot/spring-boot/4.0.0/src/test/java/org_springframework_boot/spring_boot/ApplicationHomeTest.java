/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.system.ApplicationHome;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationHomeTest {
    @TempDir
    Path directory;

    @Test
    void usesTheWorkingDirectoryWhenNoExecutableArchiveIsPresent() {
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", directory.toString());
        try {
            ApplicationHome home = new ApplicationHome();

            assertThat(home.getDir()).isEqualTo(directory.toFile().getAbsoluteFile());
            assertThat(home.toString()).isEqualTo(home.getDir().toString());
        } finally {
            if (previous == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", previous);
            }
        }
    }
}
