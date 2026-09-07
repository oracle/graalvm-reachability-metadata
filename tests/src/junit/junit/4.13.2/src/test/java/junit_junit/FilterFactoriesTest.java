/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.FilterFactory;
import org.junit.runner.FilterFactoryParams;
import org.junit.runner.JUnitCore;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;

public class FilterFactoriesTest {

    @Test
    void instantiatesAndAppliesACommandLineFilterFactory() {
        CommandLineFilterFactory.reset();
        String filterSpec = CommandLineFilterFactory.class.getName() + "=included";

        Description filteredDescription = null;
        try {
            JUnitCore.main("--filter", filterSpec, FilteredFixture.class.getName());
        } catch (CommandLineInvocationComplete completion) {
            filteredDescription = completion.getFilteredDescription();
        }

        assertThat(CommandLineFilterFactory.constructorCalls).isEqualTo(1);
        assertThat(CommandLineFilterFactory.receivedArguments).isEqualTo("included");
        assertThat(CommandLineFilterFactory.initialDescription.testCount()).isEqualTo(2);
        assertThat(filteredDescription).isNotNull();
        assertThat(filteredDescription.testCount()).isEqualTo(1);
        assertThat(filteredDescription.getChildren()).hasSize(1);
        assertThat(filteredDescription.getChildren().get(0).getMethodName()).isEqualTo("included");
    }

    public static class CommandLineFilterFactory implements FilterFactory {
        private static int constructorCalls;
        private static String receivedArguments;
        private static Description initialDescription;

        public CommandLineFilterFactory() {
            constructorCalls++;
        }

        static void reset() {
            constructorCalls = 0;
            receivedArguments = null;
            initialDescription = null;
        }

        @Override
        public Filter createFilter(FilterFactoryParams params) {
            receivedArguments = params.getArgs();
            initialDescription = params.getTopLevelDescription();
            return new NamedMethodFilter(params.getArgs());
        }
    }

    public static class NamedMethodFilter extends Filter {
        private final String methodName;

        public NamedMethodFilter(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public boolean shouldRun(Description description) {
            if (description.isTest()) {
                return methodName.equals(description.getMethodName());
            }
            for (Description child : description.getChildren()) {
                if (shouldRun(child)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String describe() {
            return "method named " + methodName;
        }

        @Override
        public void apply(Object child) throws NoTestsRemainException {
            super.apply(child);
            if (child instanceof Runner) {
                Description filteredDescription = ((Runner) child).getDescription();
                throw new CommandLineInvocationComplete(filteredDescription);
            }
        }
    }

    public static class CommandLineInvocationComplete extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Description filteredDescription;

        public CommandLineInvocationComplete(Description filteredDescription) {
            this.filteredDescription = filteredDescription;
        }

        public Description getFilteredDescription() {
            return filteredDescription;
        }
    }

    public static class FilteredFixture {
        public FilteredFixture() {
        }

        @org.junit.Test
        public void included() {
        }

        @org.junit.Test
        public void excluded() {
        }
    }
}
