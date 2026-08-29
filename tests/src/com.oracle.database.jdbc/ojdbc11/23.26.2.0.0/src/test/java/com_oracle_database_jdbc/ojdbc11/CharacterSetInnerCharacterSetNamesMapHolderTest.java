/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.sql.CharacterSet;
import org.junit.jupiter.api.Test;

public class CharacterSetInnerCharacterSetNamesMapHolderTest {
    @Test
    void resolvesTheNameOfACharacterSet() {
        CharacterSet characterSet = CharacterSet.make(CharacterSet.AL32UTF8_CHARSET);

        assertThat(characterSet.getOracleId()).isEqualTo(CharacterSet.AL32UTF8_CHARSET);
        assertThat(characterSet.toString()).isEqualTo("AL32UTF8");
    }
}
