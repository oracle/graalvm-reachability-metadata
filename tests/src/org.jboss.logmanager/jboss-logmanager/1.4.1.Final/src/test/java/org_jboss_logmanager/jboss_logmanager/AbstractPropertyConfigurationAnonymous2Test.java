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

public class AbstractPropertyConfigurationAnonymous2Test {

    @Test
    void commitValidatesAndRunsAddedPostConfigurationMethod() {
        LifecyclePojo.reset();
        final LogContextConfiguration configuration = LogContextConfiguration.Factory.create(LogContext.create());
        final String pojoName = "postConfiguredPojo";

        try {
            final PojoConfiguration pojo = configuration.addPojoConfiguration(
                    null,
                    LifecyclePojo.class.getName(),
                    pojoName
            );

            assertThat(pojo.addPostConfigurationMethod("markConfigured")).isTrue();

            configuration.commit();

            assertThat(pojo.getPostConfigurationMethods()).containsExactly("markConfigured");
            assertThat(LifecyclePojo.getConstructorCalls()).isEqualTo(1);
            assertThat(LifecyclePojo.getPostConfigurationCalls()).isEqualTo(1);
        } finally {
            configuration.forget();
            LifecyclePojo.reset();
        }
    }

    public static final class LifecyclePojo {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger POST_CONFIGURATION_CALLS = new AtomicInteger();

        public LifecyclePojo() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        public void markConfigured() {
            POST_CONFIGURATION_CALLS.incrementAndGet();
        }

        static int getConstructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        static int getPostConfigurationCalls() {
            return POST_CONFIGURATION_CALLS.get();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            POST_CONFIGURATION_CALLS.set(0);
        }
    }
}
