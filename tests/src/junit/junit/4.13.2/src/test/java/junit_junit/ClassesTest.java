/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.experimental.categories.Category;
import org.junit.experimental.categories.IncludeCategories;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.Request;
import org.junit.runner.manipulation.Filter;

public class ClassesTest {

    @Test
    void loadsACategoryNamedInFilterFactoryParameters() throws Exception {
        Description description = Request.aClass(CategorizedFixture.class).getRunner().getDescription();
        FilterFactoryParams params = new FilterFactoryParams(description, Smoke.class.getName());

        Filter filter = new IncludeCategories().createFilter(params);

        assertThat(filter.shouldRun(description)).isTrue();
    }

    public interface Smoke {
    }

    @Category(Smoke.class)
    public static class CategorizedFixture {
        public CategorizedFixture() {
        }

        @org.junit.Test
        public void execute() {
        }
    }
}
