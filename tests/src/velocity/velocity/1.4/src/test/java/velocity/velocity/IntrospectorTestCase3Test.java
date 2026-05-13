/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.velocity.test.IntrospectorTestCase3;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase3Test {
    @Test
    void runsPrimitiveOverloadIntrospectionTestCase() throws Exception {
        junit.framework.Test suite = IntrospectorTestCase3.suite();
        assertNotNull(suite);

        IntrospectorTestCase3 testCase = new IntrospectorTestCase3("testSimple");
        testCase.testSimple();
    }
}
