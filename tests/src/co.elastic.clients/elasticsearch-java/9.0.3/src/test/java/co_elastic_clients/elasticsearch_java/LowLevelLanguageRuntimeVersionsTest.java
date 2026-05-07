/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import java.net.URI;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LowLevelLanguageRuntimeVersionsTest {
    @Test
    void rest5ClientBuilderDetectsAvailableJvmLanguageRuntimes() {
        Rest5ClientBuilder builder = Rest5Client.builder(URI.create("http://localhost:9200"));

        assertThat(builder.setMetaHeaderEnabled(true)).isSameAs(builder);
    }
}
