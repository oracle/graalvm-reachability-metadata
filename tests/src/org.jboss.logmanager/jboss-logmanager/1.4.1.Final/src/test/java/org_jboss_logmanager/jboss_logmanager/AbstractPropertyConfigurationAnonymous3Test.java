/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.PojoConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationAnonymous3Test {

    @Test
    void commitValidatesAndRunsConfiguredPostConfigurationMethods() {
        BatchConfiguredPojo.reset();
        final LogContextConfiguration configuration = LogContextConfiguration.Factory.create(LogContext.create());
        final String pojoName = "batchPostConfiguredPojo";

        try {
            final PojoConfiguration pojo = configuration.addPojoConfiguration(
                    null,
                    BatchConfiguredPojo.class.getName(),
                    pojoName
            );

            pojo.setPostConfigurationMethods("markStarted", "markFinished");

            configuration.commit();

            assertThat(pojo.getPostConfigurationMethods()).containsExactly("markStarted", "markFinished");
            assertThat(BatchConfiguredPojo.getConstructorCalls()).isEqualTo(1);
            assertThat(BatchConfiguredPojo.getStartedCalls()).isEqualTo(1);
            assertThat(BatchConfiguredPojo.getFinishedCalls()).isEqualTo(1);
        } finally {
            configuration.forget();
            BatchConfiguredPojo.reset();
        }
    }

    public static final class BatchConfiguredPojo {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger STARTED_CALLS = new AtomicInteger();
        private static final AtomicInteger FINISHED_CALLS = new AtomicInteger();

        public BatchConfiguredPojo() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        public void markStarted() {
            STARTED_CALLS.incrementAndGet();
        }

        public void markFinished() {
            FINISHED_CALLS.incrementAndGet();
        }

        static int getConstructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        static int getStartedCalls() {
            return STARTED_CALLS.get();
        }

        static int getFinishedCalls() {
            return FINISHED_CALLS.get();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            STARTED_CALLS.set(0);
            FINISHED_CALLS.set(0);
        }
    }
}
