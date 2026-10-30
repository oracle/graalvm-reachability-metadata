/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import org.apache.maven.api.plugin.descriptor.Requirement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequirementTest {
    @Test
    void describesComponentInjectionAndDerivesAlternativeHint() {
        Requirement requirement = Requirement.newBuilder()
                .role("example.components.Compiler")
                .roleHint("default")
                .fieldName("compiler")
                .build();
        Requirement alternate = requirement.withRoleHint("incremental");
        Requirement renamed = requirement.with().fieldName("incrementalCompiler").build();

        assertThat(requirement.getRole()).isEqualTo("example.components.Compiler");
        assertThat(requirement.getRoleHint()).isEqualTo("default");
        assertThat(requirement.getFieldName()).isEqualTo("compiler");
        assertThat(alternate.getRoleHint()).isEqualTo("incremental");
        assertThat(alternate.getRole()).isEqualTo(requirement.getRole());
        assertThat(alternate.getFieldName()).isEqualTo("compiler");
        assertThat(renamed.getFieldName()).isEqualTo("incrementalCompiler");
        assertThat(renamed.getRoleHint()).isEqualTo("default");
        assertThat(renamed.getRole()).isEqualTo(requirement.getRole());
    }
}
