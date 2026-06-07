/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_project;

import org.apache.maven.project.path.PathTranslator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PathTranslatorAnonymous1Test {
    @Test
    void roleInitializesThroughSyntheticClassLiteralHelper() {
        assertThat(PathTranslator.ROLE)
                .isEqualTo("org.apache.maven.project.path.PathTranslator");
    }
}
