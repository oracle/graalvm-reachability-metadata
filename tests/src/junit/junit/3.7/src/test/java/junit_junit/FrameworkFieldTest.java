/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TestName;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.TestClass;

public class FrameworkFieldTest {

    @Test
    void readsAnnotatedFieldValueFromTestInstance() throws IllegalAccessException {
        TestName testName = new TestName();
        TestFixture fixture = new TestFixture(testName);
        TestClass testClass = new TestClass(TestFixture.class);

        List<FrameworkField> fields = testClass.getAnnotatedFields(Rule.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).get(fixture)).isSameAs(testName);
    }

    public static class TestFixture {
        @Rule
        public final TestName testName;

        TestFixture(TestName testName) {
            this.testName = testName;
        }
    }
}
