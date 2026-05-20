/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.HttpConstraintElement;
import jakarta.servlet.annotation.ServletSecurity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpConstraintElementTest {

    @Test
    void createsConstraintElement() {
        HttpConstraintElement constraint = new HttpConstraintElement(ServletSecurity.EmptyRoleSemantic.PERMIT);

        assertThat(constraint.getEmptyRoleSemantic()).isEqualTo(ServletSecurity.EmptyRoleSemantic.PERMIT);
    }
}
