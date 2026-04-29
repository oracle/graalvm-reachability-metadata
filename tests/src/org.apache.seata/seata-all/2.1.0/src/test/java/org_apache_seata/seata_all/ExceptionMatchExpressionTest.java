/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.seata.saga.engine.exception.EngineExecutionException;
import org.apache.seata.saga.engine.expression.exception.ExceptionMatchExpression;
import org.junit.jupiter.api.Test;

public class ExceptionMatchExpressionTest {
    @Test
    void matchesConfiguredExceptionClassAndSubclasses() {
        ExceptionMatchExpression expression = new ExceptionMatchExpression();

        expression.setExpressionString(IOException.class.getName());

        assertThat(expression.getExpressionString()).isEqualTo(IOException.class.getName());
        assertThat(expression.getValue(new IOException("configured type"))).isEqualTo(true);
        assertThat(expression.getValue(new FileNotFoundException("subtype"))).isEqualTo(true);
        assertThat(expression.getValue(new IllegalStateException("unrelated type"))).isEqualTo(false);
    }

    @Test
    void rejectsUnknownExceptionClassName() {
        ExceptionMatchExpression expression = new ExceptionMatchExpression();

        assertThatThrownBy(() -> expression.setExpressionString("example.missing.SeataException"))
                .isInstanceOf(EngineExecutionException.class)
                .hasMessageContaining("example.missing.SeataException is not a Exception Class");
    }
}
