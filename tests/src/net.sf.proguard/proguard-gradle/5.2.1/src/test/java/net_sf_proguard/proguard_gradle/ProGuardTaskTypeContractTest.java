/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_proguard.proguard_gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import proguard.gradle.ProGuardTask;

import static org.assertj.core.api.Assertions.assertThat;

public class ProGuardTaskTypeContractTest {

    @Test
    @Timeout(60)
    void proGuardTaskIsPublishedAsAGradleDefaultTaskType() {
        final Class<? extends DefaultTask> taskType = ProGuardTask.class.asSubclass(DefaultTask.class);

        assertThat(taskType).isSameAs(ProGuardTask.class);
        assertThat(Task.class).isAssignableFrom(taskType);
        assertThat(taskType.getName()).isEqualTo("proguard.gradle.ProGuardTask");
    }
}
