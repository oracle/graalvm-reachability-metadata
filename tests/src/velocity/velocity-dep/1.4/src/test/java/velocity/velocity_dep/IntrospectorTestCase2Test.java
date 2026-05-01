/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.test.IntrospectorTestCase2;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase2Test {
    @Test
    void resolvesMostSpecificMethodAndRejectsAmbiguousOverload() {
        IntrospectorTestCase2 testCase = new IntrospectorTestCase2("IntrospectorTestCase2");

        testCase.runTest();
    }
}
