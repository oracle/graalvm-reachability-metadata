/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.HttpMethodConstraintElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpMethodConstraintElementTest {

    @Test
    void createsMethodConstraintElement() {
        HttpMethodConstraintElement constraint = new HttpMethodConstraintElement("GET");

        assertThat(constraint.getMethodName()).isEqualTo("GET");
    }
}
