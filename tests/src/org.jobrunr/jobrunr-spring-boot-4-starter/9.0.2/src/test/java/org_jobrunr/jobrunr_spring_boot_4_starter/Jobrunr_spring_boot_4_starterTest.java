/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jobrunr.jobrunr_spring_boot_4_starter;

import org.jobrunr.server.JobActivator;
import org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class Jobrunr_spring_boot_4_starterTest {
    @Test
    void activatesTheJobInstanceManagedBySpring() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            StringBuilder job = new StringBuilder("notification");
            context.getBeanFactory().registerSingleton("notificationJob", job);
            context.refresh();

            JobActivator activator = new JobRunrAutoConfiguration().jobActivator(context);

            assertThat(activator.activateJob(StringBuilder.class)).isSameAs(job);
        }
    }
}
