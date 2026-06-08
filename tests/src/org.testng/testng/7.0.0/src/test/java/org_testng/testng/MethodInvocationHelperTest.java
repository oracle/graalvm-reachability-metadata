/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.testng.IObjectFactory;
import org.testng.TestNG;

public class MethodInvocationHelperTest {
    @Test
    void invokesPublicMethodOnFactorySuppliedInstanceWithDifferentClass() {
        PublicReplacementTestCase.testMethodCalls.set(0);

        TestNG testNg = testNgWithReplacement(PublicSourceTestCase.class, new PublicReplacementTestCase());
        testNg.run();

        assertThat(testNg.hasFailure()).isFalse();
        assertThat(PublicReplacementTestCase.testMethodCalls.get()).isEqualTo(1);
    }

    @Test
    void invokesDeclaredMethodOnFactorySuppliedInstanceWithDifferentClass() {
        PrivateReplacementTestCase.testMethodCalls.set(0);

        TestNG testNg = testNgWithReplacement(PrivateSourceTestCase.class, new PrivateReplacementTestCase());
        testNg.run();

        assertThat(testNg.hasFailure()).isFalse();
        assertThat(PrivateReplacementTestCase.testMethodCalls.get()).isEqualTo(1);
    }

    private static TestNG testNgWithReplacement(Class<?> sourceTestClass, Object replacementInstance) {
        Map<Class<?>, Object> replacements = new HashMap<>();
        replacements.put(sourceTestClass, replacementInstance);

        TestNG testNg = new TestNG();
        testNg.setUseDefaultListeners(false);
        testNg.setVerbose(0);
        testNg.setObjectFactory(new ReplacingObjectFactory(replacements));
        testNg.setTestClasses(new Class<?>[] {sourceTestClass});
        return testNg;
    }

    public static final class PublicSourceTestCase {
        public PublicSourceTestCase() {
        }

        @org.testng.annotations.Test
        public void sampleTest() {
            throw new AssertionError("The factory replacement should receive this invocation");
        }
    }

    public static final class PublicReplacementTestCase {
        private static final AtomicInteger testMethodCalls = new AtomicInteger();

        public void sampleTest() {
            testMethodCalls.incrementAndGet();
        }
    }

    public static final class PrivateSourceTestCase {
        public PrivateSourceTestCase() {
        }

        @org.testng.annotations.Test
        private void sampleTest() {
            throw new AssertionError("The factory replacement should receive this invocation");
        }
    }

    public static final class PrivateReplacementTestCase {
        private static final AtomicInteger testMethodCalls = new AtomicInteger();

        private void sampleTest() {
            testMethodCalls.incrementAndGet();
        }
    }

    private static final class ReplacingObjectFactory implements IObjectFactory {
        private final Map<Class<?>, Object> replacements;

        private ReplacingObjectFactory(Map<Class<?>, Object> replacements) {
            this.replacements = replacements;
        }

        @Override
        public Object newInstance(Constructor constructor, Object... params) {
            Object replacement = replacements.get(constructor.getDeclaringClass());
            if (replacement != null) {
                return replacement;
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(params);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Failed to construct TestNG test instance", ex);
            }
        }
    }
}
