/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.oauthbearer.DefaultJwtRetriever;
import org.apache.kafka.common.security.oauthbearer.DefaultJwtValidator;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SaslOauthbearerDefaultsTest {

    @Test
    void producerConfigParsesDefaultOauthbearerJwtClasses() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        ProducerConfig config = new ProducerConfig(properties);

        assertThat(config.getClass(SaslConfigs.SASL_OAUTHBEARER_JWT_RETRIEVER_CLASS))
                .isEqualTo(DefaultJwtRetriever.class);
        assertThat(config.getClass(SaslConfigs.SASL_OAUTHBEARER_JWT_VALIDATOR_CLASS))
                .isEqualTo(DefaultJwtValidator.class);
    }
}
