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
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PluginExecutionTest {
    @Test
    void rejectsNullGoalWithTypedErrorMessage() {
        PluginExecution execution = new PluginExecution();

        ClassCastException exception = assertThrows(ClassCastException.class,
                () -> execution.addGoal(null));

        assertThat(exception).hasMessageContaining("java.lang.String");
        assertThat(execution.getGoals()).isEmpty();
    }
}
