/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.driver.json.Jsonp;
import org.junit.jupiter.api.Test;

public class JsonpTest {
    @Test
    void reportsWhetherJakartaJsonIsAvailable() {
        assertThat(Jsonp.hasJakarta()).isEqualTo(Jsonp.JAKARTA_JSON_PARSER != null);
        assertThat(Jsonp.isJakartaJson(String.class)).isFalse();
        assertThat(Jsonp.isJakartaJsonStream(String.class)).isFalse();
    }
}
