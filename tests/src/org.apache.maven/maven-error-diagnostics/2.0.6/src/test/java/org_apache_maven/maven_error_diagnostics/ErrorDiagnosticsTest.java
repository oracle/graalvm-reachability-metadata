/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_error_diagnostics;

import org.apache.maven.usability.diagnostics.ErrorDiagnostics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorDiagnosticsTest {
    @Test
    void resolvesRoleNameFromLegacyClassLiteralHelper() {
        String role = ErrorDiagnostics.ROLE;

        assertThat(role).isEqualTo(ErrorDiagnostics.class.getName());
    }
}
