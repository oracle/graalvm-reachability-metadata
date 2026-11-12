/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import oracle.sql.CharacterSet;
import oracle.sql.converter.CharacterConverterJDBC;
import oracle.sql.converter.JdbcCharacterConverters;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CharacterConverterJDBCTest {
    @ParameterizedTest
    @ValueSource(ints = {
        CharacterSet.US7ASCII_CHARSET,
        CharacterSet.WE8DEC_CHARSET,
        CharacterSet.WE8ISO8859P1_CHARSET,
        CharacterSet.WE8ISO8859P15_CHARSET,
        CharacterSet.WE8MSWIN1252_CHARSET
    })
    void convertsTextUsingBundledCharacterMappings(int characterSetId) throws Exception {
        JdbcCharacterConverters converter = CharacterConverterJDBC.getInstance(characterSetId);
        String text = "Oracle JDBC 123";
        byte[] expectedBytes = text.getBytes(StandardCharsets.US_ASCII);

        assertThat(converter).isNotNull();
        assertThat(converter.getOracleId()).isEqualTo(characterSetId);
        assertThat(converter.toOracleString(text)).containsExactly(expectedBytes);
        assertThat(converter.toUnicodeString(expectedBytes, 0, expectedBytes.length)).isEqualTo(text);
    }
}
