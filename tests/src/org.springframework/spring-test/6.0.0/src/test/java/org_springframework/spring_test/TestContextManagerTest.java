/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.lang.reflect.Method;
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
    void getTestContextUsesCopyConstructorForThreadLocalContext() {
        CopyableTestContext.copyConstructorInvocations = 0;

        TestContextManager manager = new TestContextManager(new CopyableTestContextBootstrapper());

        TestContext testContext = manager.getTestContext();
        assertThat(testContext).isInstanceOf(CopyableTestContext.class);
        assertThat(((CopyableTestContext) testContext).copy).isTrue();
        assertThat(testContext.getTestClass()).isEqualTo(ManagedTestCase.class);
        assertThat(CopyableTestContext.copyConstructorInvocations).isOne();
    }

    public static class ManagedTestCase {
    }

    public static class CopyableTestContextBootstrapper implements TestContextBootstrapper {
        @Override
        public void setBootstrapContext(BootstrapContext bootstrapContext) {
        }

        @Override
        public BootstrapContext getBootstrapContext() {
            throw new UnsupportedOperationException("This test bootstrapper does not use a BootstrapContext");
        }

        @Override
        public TestContext buildTestContext() {
            return new CopyableTestContext(ManagedTestCase.class);
        }

        @Override
        public MergedContextConfiguration buildMergedContextConfiguration() {
            throw new UnsupportedOperationException("This test bootstrapper builds the TestContext directly");
        }

        @Override
        public List<TestExecutionListener> getTestExecutionListeners() {
            return List.of();
        }
    }

    public static class CopyableTestContext extends AttributeAccessorSupport implements TestContext {
        static int copyConstructorInvocations;

        private final Class<?> testClass;
        private final boolean copy;
        private Object testInstance;
        private Method testMethod;
        private Throwable testException;

        CopyableTestContext(Class<?> testClass) {
            this.testClass = testClass;
            this.copy = false;
        }

        public CopyableTestContext(CopyableTestContext source) {
            copyConstructorInvocations++;
            this.testClass = source.testClass;
            this.copy = true;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            throw new UnsupportedOperationException("This test context does not load an ApplicationContext");
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
