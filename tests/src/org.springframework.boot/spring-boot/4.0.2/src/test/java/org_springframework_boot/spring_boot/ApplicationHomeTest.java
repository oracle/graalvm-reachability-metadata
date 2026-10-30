/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.system.ApplicationHome;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationHomeTest {

    @TempDir
    File applicationDirectory;

    @Test
    void resolvesTheApplicationDirectoryWhenNoStartClassIsDeclared() {
        String previousDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", applicationDirectory.getAbsolutePath());

            ApplicationHome home = new ApplicationHome();

            assertThat(home.getDir()).isEqualTo(applicationDirectory.getAbsoluteFile());
            assertThat(home.toString()).isEqualTo(applicationDirectory.getAbsolutePath());
        } finally {
            if (previousDirectory == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", previousDirectory);
            }
        }
    }
}
