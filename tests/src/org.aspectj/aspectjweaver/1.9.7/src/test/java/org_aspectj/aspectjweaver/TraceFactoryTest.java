/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.weaver.tools.Trace;
import org.aspectj.weaver.tools.TraceFactory;
import org.junit.jupiter.api.Test;

public class TraceFactoryTest {
    private static final String FACTORY_PROPERTY = "org.aspectj.tracing.factory";
    private static final String FAILING_FACTORY_NAME = TraceFactoryUnavailableFactory.class.getName();

    @Test
    void fallsBackWhenConfiguredTraceFactoryCannotBeCreated() {
        String previousFactory = System.getProperty(FACTORY_PROPERTY);
        System.setProperty(FACTORY_PROPERTY, FAILING_FACTORY_NAME);
        try {
            TraceFactory factory = TraceFactory.getTraceFactory();
            Trace trace = factory.getTrace(TraceFactoryTest.class);

            assertThat(factory).isNotNull();
            assertThat(factory).isNotInstanceOf(TraceFactoryUnavailableFactory.class);
            assertThat(trace).isNotNull();
        } finally {
            restoreFactoryProperty(previousFactory);
        }
    }

    private static void restoreFactoryProperty(String previousFactory) {
        if (previousFactory == null) {
            System.clearProperty(FACTORY_PROPERTY);
        } else {
            System.setProperty(FACTORY_PROPERTY, previousFactory);
        }
    }
}

class TraceFactoryUnavailableFactory extends TraceFactory {
    @Override
    public Trace getTrace(Class clazz) {
        throw new AssertionError("The unavailable trace factory must not create traces");
    }
}
