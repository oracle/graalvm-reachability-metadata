/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import java.io.StringReader;

import co.elastic.clients.elasticsearch.license.LicenseStatus;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonpMapperBaseTest {
    @Test
    void deserializesGeneratedJsonpDeserializableType() {
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();

        try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader("\"active\""))) {
            LicenseStatus status = mapper.deserialize(parser, LicenseStatus.class);

            assertThat(status).isSameAs(LicenseStatus.Active);
        }
    }
}
