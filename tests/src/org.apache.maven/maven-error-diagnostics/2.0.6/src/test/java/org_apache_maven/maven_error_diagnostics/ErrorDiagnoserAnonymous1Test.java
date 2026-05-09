/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_error_diagnostics;

import org.apache.maven.usability.diagnostics.ErrorDiagnoser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorDiagnoserAnonymous1Test {
    @Test
    void resolvesRoleNameFromLegacyClassLiteralHelper() {
        String role = ErrorDiagnoser.ROLE;

        assertThat(role).isEqualTo(ErrorDiagnoser.class.getName());
    }
}
