/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.testng.TestNG;
import org.testng.annotations.Factory;

public class FactoryMethodTest {
    @Test
    void invokesAnnotatedFactoryMethodAndRunsProducedInstances() {
        FactoryHost.reset();

        TestNG testNg = new TestNG();
        testNg.setUseDefaultListeners(false);
        testNg.setVerbose(0);
        testNg.setTestClasses(new Class<?>[] {FactoryHost.class});
        testNg.run();

        assertThat(testNg.hasFailure()).isFalse();
        assertThat(FactoryHost.factoryMethodCalls.get()).isEqualTo(1);
        assertThat(FactoryProducedTestCase.testMethodCalls.get()).isEqualTo(2);
    }

    public static final class FactoryHost {
        private static final AtomicInteger factoryMethodCalls = new AtomicInteger();

        public FactoryHost() {
        }

        private static void reset() {
            factoryMethodCalls.set(0);
            FactoryProducedTestCase.testMethodCalls.set(0);
        }

        @Factory
        public Object[] createTestInstances() {
            factoryMethodCalls.incrementAndGet();
            return new Object[] {
                    new FactoryProducedTestCase("first"),
                    new FactoryProducedTestCase("second")
            };
        }
    }

    public static final class FactoryProducedTestCase {
        private static final AtomicInteger testMethodCalls = new AtomicInteger();
        private final String name;

        private FactoryProducedTestCase(String name) {
            this.name = name;
        }

        @org.testng.annotations.Test
        public void testFactoryProducedInstance() {
            assertThat(name).isNotEmpty();
            testMethodCalls.incrementAndGet();
        }
    }
}
