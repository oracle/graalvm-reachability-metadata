/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.json.JsonObject;
import io.lettuce.core.json.JsonParser;
import org.junit.jupiter.api.Test;

public class DefaultJsonParserTest {
    @Test
    void loadsDefaultParserAndParsesJson() {
        JsonParser parser = ClientOptions.create().getJsonParser().get();

        JsonObject document = parser.createJsonValue("{\"name\":\"lettuce\",\"release\":67}").asJsonObject();

        assertThat(document.get("name").asString()).isEqualTo("lettuce");
        assertThat(document.get("release").asNumber().intValue()).isEqualTo(67);
    }
}
