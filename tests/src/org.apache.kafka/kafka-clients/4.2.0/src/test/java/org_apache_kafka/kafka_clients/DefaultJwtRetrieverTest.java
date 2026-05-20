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
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs;
import org.apache.kafka.common.security.oauthbearer.JwtRetriever;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.security.auth.login.AppConfigurationEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultJwtRetrieverTest {

    @TempDir
    Path tempDir;

    @Test
    void oauthBearerLoginHandlerUsesDefaultJwtRetrieverFromProducerConfiguration() throws IOException {
        Path jwtFile = tempDir.resolve("token.jwt");
        Files.writeString(jwtFile, "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjo0MTAyNDQ0ODAwLCJpYXQiOjB9.c2ln");
        String tokenEndpointUrl = jwtFile.toUri().toString();
        String previousAllowedUrls = System.getProperty(BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG);
        System.setProperty(BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG, tokenEndpointUrl);

        OAuthBearerLoginCallbackHandler handler = new OAuthBearerLoginCallbackHandler();
        try {
            ProducerConfig producerConfig = new ProducerConfig(oauthBearerProducerProperties(tokenEndpointUrl));
            Class<?> retrieverClass = producerConfig.getClass(SaslConfigs.SASL_OAUTHBEARER_JWT_RETRIEVER_CLASS);
            assertThat(JwtRetriever.class.isAssignableFrom(retrieverClass)).isTrue();
            assertThat(retrieverClass.getName()).isEqualTo(SaslConfigs.DEFAULT_SASL_OAUTHBEARER_JWT_RETRIEVER_CLASS);

            handler.configure(
                    producerConfig.values(),
                    OAuthBearerLoginModule.OAUTHBEARER_MECHANISM,
                    jaasConfigurationEntries());
        } finally {
            handler.close();
            restoreSystemProperty(BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG, previousAllowedUrls);
        }
    }

    private static Properties oauthBearerProducerProperties(String tokenEndpointUrl) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        properties.put(SaslConfigs.SASL_MECHANISM, OAuthBearerLoginModule.OAUTHBEARER_MECHANISM);
        properties.put(SaslConfigs.SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL, tokenEndpointUrl);
        return properties;
    }

    private static List<AppConfigurationEntry> jaasConfigurationEntries() {
        AppConfigurationEntry entry = new AppConfigurationEntry(
                OAuthBearerLoginModule.class.getName(),
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                Collections.emptyMap());
        return List.of(entry);
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
