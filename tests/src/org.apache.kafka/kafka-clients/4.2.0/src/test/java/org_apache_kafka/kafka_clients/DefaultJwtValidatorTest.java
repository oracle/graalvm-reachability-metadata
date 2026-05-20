/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.oauthbearer.JwtValidator;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallbackHandler;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.security.auth.login.AppConfigurationEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultJwtValidatorTest {

    @Test
    void oauthBearerValidatorHandlerUsesDefaultJwtValidatorFromProducerConfiguration() {
        ProducerConfig producerConfig = new ProducerConfig(oauthBearerProducerProperties());
        Class<?> validatorClass = producerConfig.getClass(SaslConfigs.SASL_OAUTHBEARER_JWT_VALIDATOR_CLASS);
        assertThat(JwtValidator.class.isAssignableFrom(validatorClass)).isTrue();
        assertThat(validatorClass.getName()).isEqualTo(SaslConfigs.DEFAULT_SASL_OAUTHBEARER_JWT_VALIDATOR_CLASS);

        OAuthBearerValidatorCallbackHandler handler = new OAuthBearerValidatorCallbackHandler();
        try {
            handler.configure(
                    producerConfig.values(),
                    OAuthBearerLoginModule.OAUTHBEARER_MECHANISM,
                    jaasConfigurationEntries());
        } finally {
            handler.close();
        }
    }

    private static Properties oauthBearerProducerProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        properties.put(SaslConfigs.SASL_MECHANISM, OAuthBearerLoginModule.OAUTHBEARER_MECHANISM);
        return properties;
    }

    private static List<AppConfigurationEntry> jaasConfigurationEntries() {
        AppConfigurationEntry entry = new AppConfigurationEntry(
                OAuthBearerLoginModule.class.getName(),
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                Collections.emptyMap());
        return List.of(entry);
    }
}
