/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.PluginExecution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginExecutionTest {
    @Test
    void addsNullGoalToGoals() {
        PluginExecution execution = new PluginExecution();

        execution.addGoal(null);

        assertThat(execution.getGoals()).hasSize(1);
        assertThat(execution.getGoals().get(0)).isNull();
    }
}
