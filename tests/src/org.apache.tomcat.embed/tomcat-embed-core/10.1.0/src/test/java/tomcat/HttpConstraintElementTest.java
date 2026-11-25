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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HttpConstraintElementTest {
    @Test
    void enforcesRoleAndDenyConstraintConsistency() {
        HttpConstraintElement constraint = new HttpConstraintElement(TransportGuarantee.CONFIDENTIAL, "reader");
        assertThat(constraint.getRolesAllowed()).containsExactly("reader");
        assertThat(constraint.getTransportGuarantee()).isEqualTo(TransportGuarantee.CONFIDENTIAL);
        assertThatThrownBy(() -> new HttpConstraintElement(EmptyRoleSemantic.DENY,
                TransportGuarantee.NONE, "reader")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DENY");
    }
}
