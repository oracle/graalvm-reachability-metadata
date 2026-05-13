/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import junit.framework.TestSuite;

import org.apache.velocity.test.ParserTestCase;
import org.junit.jupiter.api.Test;

public class ParserTestCaseTest {
    @Test
    public void createsJUnitThreeSuiteForParserRegressionTests() throws Exception {
        clearParserTestCaseClassCache();

        TestSuite suite = (TestSuite) ParserTestCase.suite();

        assertThat(suite).isNotNull();
        assertThat(suite.getName()).isEqualTo(ParserTestCase.class.getName());
    }

    private static void clearParserTestCaseClassCache() throws Exception {
        /*
         * The Velocity 1.4 artifact was compiled with a synthetic `Class` cache for
         * `ParserTestCase.class`. Clear it so `suite()` exercises the original
         * `Class.forName(String)` resolution path even if discovery initialized it.
         */
        Field classCache = ParserTestCase.class.getDeclaredField(
                "class$org$apache$velocity$test$ParserTestCase");
        classCache.setAccessible(true);
        classCache.set(null, null);
    }
}
