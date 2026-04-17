/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.seata.common.exception.FrameworkErrorCode;
import org.apache.seata.saga.engine.exception.EngineExecutionException;
import org.apache.seata.saga.engine.expression.exception.ExceptionMatchExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExceptionMatchExpressionTest {
    @Test
    void setExpressionStringLoadsTheConfiguredExceptionTypeForAssignableMatches() {
        ExceptionMatchExpression expression = new ExceptionMatchExpression();
        expression.setExpressionString(IOException.class.getName());

        assertThat(expression.getExpressionString()).isEqualTo(IOException.class.getName());
        assertThat(expression.getValue(new FileNotFoundException("missing.txt"))).isEqualTo(true);
        assertThat(expression.getValue(new IllegalArgumentException("boom"))).isEqualTo(false);
    }

    @Test
    void setExpressionStringRejectsUnknownExceptionTypes() {
        ExceptionMatchExpression expression = new ExceptionMatchExpression();

        EngineExecutionException thrown = assertThrows(
                EngineExecutionException.class,
                () -> expression.setExpressionString("org_apache_seata.seata_all.missing.UnknownException"));

        assertThat(thrown).hasMessageContaining("org_apache_seata.seata_all.missing.UnknownException is not a Exception Class");
        assertThat(thrown.getErrcode()).isEqualTo(FrameworkErrorCode.NotExceptionClass);
        assertThat(thrown.getCause()).isInstanceOf(ClassNotFoundException.class);
    }
}
