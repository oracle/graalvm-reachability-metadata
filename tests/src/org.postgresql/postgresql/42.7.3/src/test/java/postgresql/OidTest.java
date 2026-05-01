/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import org.junit.jupiter.api.Test;
import org.postgresql.core.Oid;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.core.Oid}.
 */
public class OidTest {

    @Test
    void knownOidValuesResolveToNames() {
        assertThat(Oid.toString(23)).isEqualTo("INT4");
        assertThat(Oid.toString(2950)).isEqualTo("UUID");
    }

    @Test
    void knownOidNamesResolveToValues() throws Exception {
        assertThat(Oid.valueOf("int4")).isEqualTo(23);
        assertThat(Oid.valueOf("INT4")).isEqualTo(23);
        assertThat(Oid.valueOf("2950")).isEqualTo(2950);
    }

    @Test
    void unknownOidValueUsesDiagnosticName() {
        assertThat(Oid.toString(-1)).isEqualTo("<unknown:-1>");
    }

    @Test
    void unknownOidNameIsRejected() {
        PSQLException exception = catchThrowableOfType(() -> Oid.valueOf("not_a_postgresql_oid"), PSQLException.class);

        assertThat((Throwable) exception).isNotNull();
        assertThat(exception.getSQLState()).isEqualTo(PSQLState.INVALID_PARAMETER_VALUE.getState());
    }
}
