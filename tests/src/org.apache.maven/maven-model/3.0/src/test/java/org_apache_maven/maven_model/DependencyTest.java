/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.Dependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyTest {
    @Test
    void addsNullExclusionToExclusions() {
        Dependency dependency = new Dependency();

        dependency.addExclusion(null);

        assertThat(dependency.getExclusions()).hasSize(1);
        assertThat(dependency.getExclusions().get(0)).isNull();
    }
}
