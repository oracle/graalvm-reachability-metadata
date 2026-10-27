/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import oracle.sql.json.OracleJsonException;
import oracle.sql.json.OracleJsonFactory;
import org.junit.jupiter.api.Test;

public class OracleJsonExceptionsTest {
    @Test
    void reportsMalformedJsonWithALocalizedMessage() {
        OracleJsonFactory factory = new OracleJsonFactory();

        OracleJsonException exception = assertThrows(
                OracleJsonException.class, () -> factory.createJsonTextValue(new StringReader("{")));

        assertThat(exception.getMessage()).isNotBlank();
    }
}
