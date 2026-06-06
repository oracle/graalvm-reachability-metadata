/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.Contributor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContributorTest {
    @Test
    void addsNullRoleToRoles() {
        Contributor contributor = new Contributor();

        contributor.addRole(null);

        assertThat(contributor.getRoles()).hasSize(1);
        assertThat(contributor.getRoles().get(0)).isNull();
    }
}
