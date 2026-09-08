/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

public class AllMembersSupplierTest {

    @Test
    void readsAStaticDataPointFieldForATheory() {
        Result result = JUnitCore.runClasses(FieldDataPointFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(FieldDataPointFixture.observed).isEqualTo("field-value");
    }

    @RunWith(Theories.class)
    public static class FieldDataPointFixture {
        private static String observed;

        @DataPoint
        public static final String VALUE = "field-value";

        public FieldDataPointFixture() {
        }

        @Theory
        public void recordsFieldValue(String value) {
            observed = value;
        }
    }
}
