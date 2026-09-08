/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_seregamorph.spring_test_smart_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.seregamorph.testsmartcontext.IntegrationTestFilter;
import com.github.seregamorph.testsmartcontext.SmartDirtiesContextTestExecutionListener;
import com.github.seregamorph.testsmartcontext.SmartDirtiesTestsSorter;
import com.github.seregamorph.testsmartcontext.SmartDirtiesTestsSupportState;
import com.github.seregamorph.testsmartcontext.TestClassExtractor;
import com.github.seregamorph.testsmartcontext.TestSortResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.AttributeAccessorSupport;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContext;

public class SmartDirtiesTestsSupportTest {

    @Test
    void reportsUninitializedTestOrdering() {
        Object ordering = SmartDirtiesTestsSupportState.clearOrdering();
        TestContext context = new StubTestContext();
        SmartDirtiesContextTestExecutionListener listener = new SmartDirtiesContextTestExecutionListener();

        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> listener.afterTestClass(context));
            assertEquals("Test ordering is not initialized or failed", error.getMessage());
        } finally {
            SmartDirtiesTestsSupportState.restoreOrdering(ordering);
        }
    }

    @Test
    void sortsNonIntegrationClassesByName() {
        List<Class<?>> testClasses = new ArrayList<>(Arrays.asList(ZetaCase.class, AlphaCase.class));

        TestSortResult result = SmartDirtiesTestsSorter.getInstance().sort(
                testClasses,
                new ClassExtractor(),
                new NonIntegrationTestFilter());

        assertEquals(Arrays.asList(AlphaCase.class, ZetaCase.class), testClasses);
        assertEquals(new LinkedHashSet<>(testClasses), result.getNonItClasses());
        assertTrue(result.getSortedConfigToTests().isEmpty());
    }

    public static class AlphaCase {
    }

    public static class ZetaCase {
    }

    private static class StubTestContext extends AttributeAccessorSupport implements TestContext {

        @Override
        public ApplicationContext getApplicationContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<?> getTestClass() {
            return SmartDirtiesTestsSupportTest.class;
        }

        @Override
        public Object getTestInstance() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Method getTestMethod() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Throwable getTestException() {
            return null;
        }

        @Override
        public void markApplicationContextDirty(HierarchyMode hierarchyMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateState(Object testInstance, Method testMethod, Throwable testException) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ClassExtractor extends TestClassExtractor<Class<?>> {

        ClassExtractor() {
            super(ItemType.TEST_CLASS);
        }

        @Override
        public Class<?> getTestClass(Class<?> testClass) {
            return testClass;
        }
    }

    private static class NonIntegrationTestFilter extends IntegrationTestFilter {

        @Override
        protected boolean isIntegrationTest(Class<?> testClass) {
            return false;
        }
    }
}
