/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import co.elastic.clients.json.SimpleJsonpMapper;
import co.elastic.clients.util.DateTime;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonpMapperBaseTest {
    @Test
    void deserializesAnnotatedClientTypeWithBuiltInDeserializer() {
        SimpleJsonpMapper mapper = new SimpleJsonpMapper();

        try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader("1716403200123"))) {
            DateTime dateTime = mapper.deserialize(parser, DateTime.class);

            assertThat(dateTime.toEpochMilli()).isEqualTo(1_716_403_200_123L);
        }
    }
}
