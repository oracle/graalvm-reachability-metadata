/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import org.jmock.expectation.ReturnObjectMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VerifierTest {
    @Test
    void verifiesMockObjectFieldsWhenCheckingExpectations() {
        ReturnObjectMap returnValues = new ReturnObjectMap("return values");
        returnValues.putReturnValue("name", "jmock");

        assertThat(returnValues.getValue("name")).isEqualTo("jmock");

        returnValues.verify();
    }
}
