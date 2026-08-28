/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import oracle.jdbc.driver.json.Jsonp;
import org.junit.jupiter.api.Test;

public class JsonpTest {
    @Test
    void recognizesJakartaJsonApiTypes() {
        assertThat(Jsonp.hasJakarta()).isTrue();
        assertThat(Jsonp.JAKARTA_JSON_PARSER).isEqualTo(JsonParser.class);
        assertThat(Jsonp.isJakartaJson(JsonValue.class)).isTrue();
        assertThat(Jsonp.isJakartaJsonStream(JsonParser.class)).isTrue();
    }
}
