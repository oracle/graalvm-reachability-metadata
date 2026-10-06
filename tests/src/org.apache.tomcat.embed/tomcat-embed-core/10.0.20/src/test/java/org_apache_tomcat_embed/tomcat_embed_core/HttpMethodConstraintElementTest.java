/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.HttpConstraintElement;
import jakarta.servlet.HttpMethodConstraintElement;
import jakarta.servlet.annotation.ServletSecurity.TransportGuarantee;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpMethodConstraintElementTest {

    @Test
    void associatesConstraintWithHttpMethod() {
        HttpConstraintElement constraint = new HttpConstraintElement(TransportGuarantee.CONFIDENTIAL, "admin");

        HttpMethodConstraintElement methodConstraint = new HttpMethodConstraintElement("POST", constraint);

        assertThat(methodConstraint.getMethodName()).isEqualTo("POST");
        assertThat(methodConstraint.getTransportGuarantee()).isEqualTo(TransportGuarantee.CONFIDENTIAL);
        assertThat(methodConstraint.getRolesAllowed()).containsExactly("admin");
    }
}
