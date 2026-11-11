/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.exceptions.CJException;
import com.mysql.cj.exceptions.ExceptionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionFactoryTest {
    @Test
    void wrappingConnectorExceptionInheritsSqlDiagnostics() {
        CJException cause = ExceptionFactory.createException("transaction interrupted", "40001", 1213, true, null, null);
        CJException exception = ExceptionFactory.createException("retry transaction", cause);

        assertThat(exception).isNotSameAs(cause).hasMessage("retry transaction").hasCause(cause);
        assertThat(exception.getSQLState()).isEqualTo("40001");
        assertThat(exception.getVendorCode()).isEqualTo(1213);
        assertThat(exception.isTransient()).isTrue();
        exception.appendMessage(" after reconnecting");
        assertThat(exception).hasMessage("retry transaction after reconnecting");
        assertThat(cause).hasMessage("transaction interrupted");
    }

    @Test
    void exceptionFactoryPreservesDiagnosticDetailsAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid connection option");
        CJException exception = ExceptionFactory.createException("connection rejected", "08001", 123, true, cause, null);
        assertThat(exception).hasMessage("connection rejected").hasCause(cause);
        assertThat(exception.getSQLState()).isEqualTo("08001");
        assertThat(exception.getVendorCode()).isEqualTo(123);
        assertThat(exception.isTransient()).isTrue();
    }
}
