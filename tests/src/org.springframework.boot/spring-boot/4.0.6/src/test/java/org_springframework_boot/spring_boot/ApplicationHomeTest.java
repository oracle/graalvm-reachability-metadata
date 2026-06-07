/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;

import org.springframework.boot.system.ApplicationHome;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationHomeTest {

    @Test
    void defaultConstructorResolvesApplicationHomeFromRuntimeClasspath() {
        ApplicationHome applicationHome = new ApplicationHome();

        assertThat(applicationHome.getDir()).isAbsolute().exists();
        assertThat(applicationHome.toString()).isEqualTo(applicationHome.getDir().toString());
    }

}
