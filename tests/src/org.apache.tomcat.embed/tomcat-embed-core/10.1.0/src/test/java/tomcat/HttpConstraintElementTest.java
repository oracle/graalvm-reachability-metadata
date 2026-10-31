/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import jakarta.servlet.HttpConstraintElement;
import jakarta.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import jakarta.servlet.annotation.ServletSecurity.TransportGuarantee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpConstraintElementTest {
    @Test
    void validatesRoleAndTransportConstraints() {
        HttpConstraintElement constraint = new HttpConstraintElement(TransportGuarantee.CONFIDENTIAL, "admin");
        assertEquals(TransportGuarantee.CONFIDENTIAL, constraint.getTransportGuarantee());
        assertArrayEquals(new String[] {"admin"}, constraint.getRolesAllowed());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HttpConstraintElement(EmptyRoleSemantic.DENY, TransportGuarantee.NONE, "admin"));
        assertFalse(error.getMessage().isBlank());
    }
}
