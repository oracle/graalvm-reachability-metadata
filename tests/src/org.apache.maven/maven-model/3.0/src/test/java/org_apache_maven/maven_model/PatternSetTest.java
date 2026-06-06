/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.PatternSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PatternSetTest {
    @Test
    void addsNullExcludeToExcludes() {
        PatternSet patternSet = new PatternSet();

        patternSet.addExclude(null);

        assertThat(patternSet.getExcludes()).hasSize(1);
        assertThat(patternSet.getExcludes().get(0)).isNull();
    }
}
