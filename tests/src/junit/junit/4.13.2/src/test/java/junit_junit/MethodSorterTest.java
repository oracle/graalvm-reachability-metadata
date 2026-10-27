/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.jupiter.api.Test;

public class MethodSorterTest {

    @Test
    void discoversLegacyTestMethodsInDeterministicOrder() {
        TestSuite suite = new TestSuite(LegacyCase.class);

        assertThat(suite.testCount()).isEqualTo(2);
        assertThat(suite.testAt(0).toString()).contains("test");
        assertThat(suite.testAt(1).toString()).contains("test");
    }

    public static class LegacyCase extends TestCase {
        public LegacyCase(String name) {
            super(name);
        }

        public void testAlpha() {
        }

        public void testBeta() {
        }
    }
}
