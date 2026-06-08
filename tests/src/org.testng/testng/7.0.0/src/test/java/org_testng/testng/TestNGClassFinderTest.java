/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.testng.IObjectFactory;
import org.testng.ITestContext;
import org.testng.ITestObjectFactory;
import org.testng.TestNG;
import org.testng.annotations.ObjectFactory;

public class TestNGClassFinderTest {
    @Test
    void createsObjectFactoryFromNoArgumentFactoryMethod() {
        NoArgumentObjectFactoryTestCase.reset();

        runTestNg(NoArgumentObjectFactoryTestCase.class);

        assertThat(NoArgumentObjectFactoryTestCase.factoryMethodCalls.get()).isEqualTo(1);
        assertThat(NoArgumentObjectFactoryTestCase.testMethodCalls.get()).isEqualTo(1);
        assertThat(NoArgumentObjectFactoryTestCase.objectFactoryConstructorCalls.get()).isEqualTo(1);
    }

    @Test
    void createsObjectFactoryFromTestContextFactoryMethod() {
        ContextAwareObjectFactoryTestCase.reset();

        runTestNg(ContextAwareObjectFactoryTestCase.class);

        assertThat(ContextAwareObjectFactoryTestCase.factoryMethodCalls.get()).isEqualTo(1);
        assertThat(ContextAwareObjectFactoryTestCase.testMethodCalls.get()).isEqualTo(1);
        assertThat(ContextAwareObjectFactoryTestCase.receivedContext.get()).isNotNull();
    }

    @Test
    void scansConstructorsWhenConfiguredClassHasNoTestNgAnnotations() {
        PlainConfiguredClass.constructorCalls.set(0);
        OrdinaryTestCase.testMethodCalls.set(0);

        runTestNg(PlainConfiguredClass.class, OrdinaryTestCase.class);

        assertThat(PlainConfiguredClass.constructorCalls.get()).isEqualTo(0);
        assertThat(OrdinaryTestCase.testMethodCalls.get()).isEqualTo(1);
    }

    private static void runTestNg(Class<?>... testClasses) {
        TestNG testNg = new TestNG();
        testNg.setUseDefaultListeners(false);
        testNg.setVerbose(0);
        testNg.setTestClasses(testClasses);
        testNg.run();
    }

    public static final class NoArgumentObjectFactoryTestCase {
        private static final AtomicInteger factoryMethodCalls = new AtomicInteger();
        private static final AtomicInteger testMethodCalls = new AtomicInteger();
        private static final AtomicInteger objectFactoryConstructorCalls = new AtomicInteger();

        public NoArgumentObjectFactoryTestCase() {
        }

        private static void reset() {
            factoryMethodCalls.set(0);
            testMethodCalls.set(0);
            objectFactoryConstructorCalls.set(0);
        }

        @ObjectFactory
        public ITestObjectFactory createObjectFactory() {
            factoryMethodCalls.incrementAndGet();
            return new CountingObjectFactory(objectFactoryConstructorCalls);
        }

        @org.testng.annotations.Test
        public void sampleTest() {
            testMethodCalls.incrementAndGet();
        }
    }

    public static final class ContextAwareObjectFactoryTestCase {
        private static final AtomicInteger factoryMethodCalls = new AtomicInteger();
        private static final AtomicInteger testMethodCalls = new AtomicInteger();
        private static final AtomicReference<ITestContext> receivedContext = new AtomicReference<>();

        public ContextAwareObjectFactoryTestCase() {
        }

        private static void reset() {
            factoryMethodCalls.set(0);
            testMethodCalls.set(0);
            receivedContext.set(null);
        }

        @ObjectFactory
        public ITestObjectFactory createObjectFactory(ITestContext context) {
            factoryMethodCalls.incrementAndGet();
            receivedContext.set(context);
            return new CountingObjectFactory(new AtomicInteger());
        }

        @org.testng.annotations.Test
        public void sampleTest() {
            testMethodCalls.incrementAndGet();
        }
    }

    public static final class PlainConfiguredClass {
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        public PlainConfiguredClass() {
            constructorCalls.incrementAndGet();
        }
    }

    public static final class OrdinaryTestCase {
        private static final AtomicInteger testMethodCalls = new AtomicInteger();

        public OrdinaryTestCase() {
        }

        @org.testng.annotations.Test
        public void sampleTest() {
            testMethodCalls.incrementAndGet();
        }
    }

    private static final class CountingObjectFactory implements IObjectFactory {
        private final AtomicInteger constructorCalls;

        private CountingObjectFactory(AtomicInteger constructorCalls) {
            this.constructorCalls = constructorCalls;
        }

        @Override
        public Object newInstance(Constructor constructor, Object... params) {
            constructorCalls.incrementAndGet();
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(params);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Failed to construct TestNG test instance", ex);
            }
        }
    }
}
