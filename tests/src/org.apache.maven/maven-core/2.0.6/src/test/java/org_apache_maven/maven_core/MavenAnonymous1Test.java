/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.Maven;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenAnonymous1Test {
    @Test
    void exposesMavenComponentRoleName() {
        assertThat(Maven.ROLE).isEqualTo(Maven.class.getName());
    }
}
