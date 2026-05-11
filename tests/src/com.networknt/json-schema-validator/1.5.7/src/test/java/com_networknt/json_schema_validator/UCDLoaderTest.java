/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt.json_schema_validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.format.IdnHostnameFormat;
import org.junit.jupiter.api.Test;

public class UCDLoaderTest {
    @Test
    void loadsUnicodeCharacterDataWhenValidatingIdnHostnameFormat() {
        IdnHostnameFormat format = new IdnHostnameFormat();

        assertThat(format.matches(null, "ma\u00f1ana.example")).isTrue();
        assertThat(format.matches(null, "example:com")).isFalse();
    }
}
