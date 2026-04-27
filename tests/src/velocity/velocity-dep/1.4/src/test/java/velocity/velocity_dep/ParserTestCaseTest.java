/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import junit.framework.TestResult;
import org.apache.velocity.test.ParserTestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class ParserTestCaseTest {
    @org.junit.jupiter.api.Test
    void validatesParserErrorsThroughUpstreamSuite() {
        junit.framework.Test suite = ParserTestCase.suite();
        TestResult result = new TestResult();

        suite.run(result);

        assertThat(result.errorCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(result.runCount()).isEqualTo(3);
    }
}
