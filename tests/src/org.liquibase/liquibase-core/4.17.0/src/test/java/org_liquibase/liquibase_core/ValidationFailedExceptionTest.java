/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.changelog.visitor.ValidatingVisitor;
import liquibase.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationFailedExceptionTest {

    @Test
    void buildsMessageFromLiquibaseResourceBundle() {
        ValidatingVisitor visitor = new ValidatingVisitor();

        ValidationFailedException exception = new ValidationFailedException(visitor);

        assertThat(exception.getInvalidMD5Sums()).isEmpty();
        assertThat(exception.getMessage()).isNotBlank();
    }
}
