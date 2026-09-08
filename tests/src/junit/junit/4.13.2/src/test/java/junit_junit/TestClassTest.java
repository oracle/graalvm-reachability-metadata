/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TestName;
import org.junit.runners.model.TestClass;

public class TestClassTest {

    @Test
    void scansConstructorsFieldsAndMethods() {
        TestClass testClass = new TestClass(Fixture.class);

        assertThat(testClass.getOnlyConstructor().getParameterTypes()).isEmpty();
        assertThat(testClass.getAnnotatedFields(Rule.class)).hasSize(1);
        assertThat(testClass.getAnnotatedMethods(org.junit.Test.class)).hasSize(1);
    }

    public static class Fixture {
        @Rule
        public final TestName testName = new TestName();

        public Fixture() {
        }

        @org.junit.Test
        public void executes() {
        }
    }
}
