/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_proguard.proguard_gradle;

import org.gradle.api.tasks.TaskInstantiationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import proguard.gradle.ProGuardTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Proguard_gradleTest {

    @Test
    @Timeout(60)
    void proGuardTaskRejectsDirectInstantiationOutsideGradleDsl() {
        assertThatThrownBy(ProGuardTask::new)
                .isInstanceOf(TaskInstantiationException.class)
                .satisfies(exception -> assertThat(exception)
                        .hasMessageContaining(ProGuardTask.class.getName())
                        .hasMessageContaining("instantiated directly")
                        .hasMessageContaining("DSL"));
    }
}
