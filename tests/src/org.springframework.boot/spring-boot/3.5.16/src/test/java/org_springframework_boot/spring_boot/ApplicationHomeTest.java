/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.springframework.boot.system.ApplicationHome;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationHomeTest {

    @Test
    void resolvesHomeFromManifestStartClassLookup() {
        ApplicationHome home = new ApplicationHome();

        File workingDirectory = new File(System.getProperty("user.dir")).getAbsoluteFile();
        assertThat(home.getSource()).isNull();
        assertThat(home.getDir()).isEqualTo(workingDirectory);
        assertThat(home.getDir()).isDirectory();
        assertThat(home).hasToString(workingDirectory.toString());
    }

    @Test
    void resolvesHomeForExplicitSourceClassDuringUnitTests() {
        ApplicationHome home = new ApplicationHome(ApplicationHomeTest.class);

        assertThat(home.getSource()).isNull();
        assertThat(home.getDir()).isEqualTo(new File(System.getProperty("user.dir")).getAbsoluteFile());
    }
}
