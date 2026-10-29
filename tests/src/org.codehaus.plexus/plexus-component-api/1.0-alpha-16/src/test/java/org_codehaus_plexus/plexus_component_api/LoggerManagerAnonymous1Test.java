/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_component_api;

import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.logging.LoggerManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerManagerAnonymous1Test {
    @Test
    void declaresLoggerManagerDependencyForComponent() {
        ComponentRequirement requirement = new ComponentRequirement();
        requirement.setRole(LoggerManager.ROLE);
        requirement.setFieldName("loggerManager");
        ComponentDescriptor component = new ComponentDescriptor();
        component.setRole("example.worker");
        component.addRequirement(requirement);

        assertThat(component.getRequirements()).containsExactly(requirement);
        assertThat(requirement.getRequirementKey()).isEqualTo("org.codehaus.plexus.logging.LoggerManager");
        assertThat(requirement.getHumanReadableKey())
                .contains("role: 'org.codehaus.plexus.logging.LoggerManager'", "field name: 'loggerManager'");
    }

    @Test
    void identifiesHintedLoggerManagerDependencies() {
        ComponentRequirement console = new ComponentRequirement();
        console.setRole(LoggerManager.ROLE);
        console.setRoleHint("console");
        ComponentRequirement file = new ComponentRequirement();
        file.setRole(LoggerManager.ROLE);
        file.setRoleHint("file");

        assertThat(console.getRequirementKey()).isEqualTo("org.codehaus.plexus.logging.LoggerManagerconsole");
        assertThat(file.getRequirementKey()).isEqualTo("org.codehaus.plexus.logging.LoggerManagerfile");
        assertThat(console).isNotEqualTo(file);

        file.setRoleHint("console");
        assertThat(file).isEqualTo(console);
        assertThat(file.hashCode()).isEqualTo(console.hashCode());
    }
}
