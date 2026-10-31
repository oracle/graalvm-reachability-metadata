/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.HttpConstraintElement;
import jakarta.servlet.HttpMethodConstraintElement;
import jakarta.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpMethodConstraintElementTest {
    @Test
    void validatesMethodNameAndRetainsMethodPolicy() {
        HttpMethodConstraintElement constraint = new HttpMethodConstraintElement("DELETE",
                new HttpConstraintElement(EmptyRoleSemantic.DENY));
        assertEquals("DELETE", constraint.getMethodName());
        assertEquals(EmptyRoleSemantic.DENY, constraint.getEmptyRoleSemantic());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HttpMethodConstraintElement(""));
        assertFalse(error.getMessage().isBlank());
    }
}
