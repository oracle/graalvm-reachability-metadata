/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.FilterFactory;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.manipulation.Filter;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgJunitRunnerFilterFactoriesTest {
    @Test
    void createsFilterFactoryWithPublicNoArgumentConstructor() throws Exception {
        Description description = Description.createSuiteDescription("suite");
        FilterFactoryParams params = new FilterFactoryParams(description, "keep");

        Filter filter = createFilter(TestNameFilterFactory.class, params);

        assertThat(filter.shouldRun(Description.createTestDescription(SampleTestCase.class, "keep"))).isTrue();
        assertThat(filter.shouldRun(Description.createTestDescription(SampleTestCase.class, "skip"))).isFalse();
        assertThat(filter.describe()).isEqualTo("method named keep");
        assertThat(TestNameFilterFactory.createdWith.getTopLevelDescription()).isSameAs(description);
        assertThat(TestNameFilterFactory.createdWith.getArgs()).isEqualTo("keep");
    }

    private static Filter createFilter(Class<? extends FilterFactory> factoryClass, FilterFactoryParams params)
            throws Exception {
        Class<?> filterFactoriesClass = Class.forName("org.junit.runner.FilterFactories");
        Method createFilter = filterFactoriesClass.getDeclaredMethod(
                "createFilter", Class.class, FilterFactoryParams.class);
        createFilter.setAccessible(true);
        try {
            return (Filter) createFilter.invoke(null, factoryClass, params);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }

    public static class TestNameFilterFactory implements FilterFactory {
        private static FilterFactoryParams createdWith;

        public TestNameFilterFactory() {
        }

        @Override
        public Filter createFilter(FilterFactoryParams params) {
            createdWith = params;
            return new Filter() {
                @Override
                public boolean shouldRun(Description description) {
                    return params.getArgs().equals(description.getMethodName());
                }

                @Override
                public String describe() {
                    return "method named " + params.getArgs();
                }
            };
        }
    }

    public static class SampleTestCase {
    }
}
