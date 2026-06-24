/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.core.AttributeAccessorSupport;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.BootstrapContext;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextBootstrapper;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;

import static org.assertj.core.api.Assertions.assertThat;

public class TestContextManagerTest {
    @Test
    void usesTestContextCopyConstructorForThreadLocalContext() {
        CopyableTestContext.resetCopyCount();
        CopyableTestContext original = new CopyableTestContext(ManagedTestCase.class);
        TestContextManager manager = new TestContextManager(new CopyableBootstrapper(original));

        TestContext testContext = manager.getTestContext();

        assertThat(testContext).isInstanceOf(CopyableTestContext.class);
        CopyableTestContext copy = (CopyableTestContext) testContext;
        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getSource()).isSameAs(original);
        assertThat(copy.getTestClass()).isEqualTo(ManagedTestCase.class);
        assertThat(CopyableTestContext.getCopyCount()).isEqualTo(1);
        assertThat(manager.getTestContext()).isSameAs(copy);
        assertThat(CopyableTestContext.getCopyCount()).isEqualTo(1);
    }

    static class ManagedTestCase {
    }

    static class CopyableBootstrapper implements TestContextBootstrapper {
        private final TestContext testContext;

        private BootstrapContext bootstrapContext;

        CopyableBootstrapper(TestContext testContext) {
            this.testContext = testContext;
        }

        @Override
        public void setBootstrapContext(BootstrapContext bootstrapContext) {
            this.bootstrapContext = bootstrapContext;
        }

        @Override
        public BootstrapContext getBootstrapContext() {
            return this.bootstrapContext;
        }

        @Override
        public TestContext buildTestContext() {
            return this.testContext;
        }

        @Override
        public MergedContextConfiguration buildMergedContextConfiguration() {
            throw new UnsupportedOperationException("Merged context configuration is not needed");
        }

        @Override
        public List<TestExecutionListener> getTestExecutionListeners() {
            return Collections.emptyList();
        }
    }

    public static class CopyableTestContext extends AttributeAccessorSupport implements TestContext {
        private static int copyCount;

        private final Class<?> testClass;

        private final CopyableTestContext source;

        private Object testInstance;

        private Method testMethod;

        private Throwable testException;

        CopyableTestContext(Class<?> testClass) {
            this.testClass = testClass;
            this.source = null;
        }

        public CopyableTestContext(CopyableTestContext source) {
            copyCount++;
            this.testClass = source.testClass;
            this.source = source;
            copyAttributesFrom(source);
        }

        static void resetCopyCount() {
            copyCount = 0;
        }

        static int getCopyCount() {
            return copyCount;
        }

        CopyableTestContext getSource() {
            return this.source;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            throw new IllegalStateException("No application context is configured");
        }

        @Override
        public Class<?> getTestClass() {
            return this.testClass;
        }

        @Override
        public Object getTestInstance() {
            return this.testInstance;
        }

        @Override
        public Method getTestMethod() {
            return this.testMethod;
        }

        @Override
        public Throwable getTestException() {
            return this.testException;
        }

        @Override
        public void markApplicationContextDirty(HierarchyMode hierarchyMode) {
        }

        @Override
        public void updateState(Object testInstance, Method testMethod, Throwable testException) {
            this.testInstance = testInstance;
            this.testMethod = testMethod;
            this.testException = testException;
        }
    }
}
