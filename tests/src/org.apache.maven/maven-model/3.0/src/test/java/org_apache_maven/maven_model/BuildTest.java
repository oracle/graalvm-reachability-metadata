/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.Build;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildTest {
    @Test
    void addsNullBuildExtensionToExtensions() {
        Build build = new Build();

        build.addExtension(null);

        assertThat(build.getExtensions()).hasSize(1);
        assertThat(build.getExtensions().get(0)).isNull();
    }
}
