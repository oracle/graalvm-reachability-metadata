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
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DependencyTest {
    @Test
    void rejectsNullExclusionWithTypedErrorMessage() {
        Dependency dependency = new Dependency();

        ClassCastException exception = assertThrows(ClassCastException.class,
                () -> dependency.addExclusion(null));

        assertThat(exception).hasMessageContaining("org.apache.maven.model.Exclusion");
        assertThat(dependency.getExclusions()).isEmpty();
    }
}
