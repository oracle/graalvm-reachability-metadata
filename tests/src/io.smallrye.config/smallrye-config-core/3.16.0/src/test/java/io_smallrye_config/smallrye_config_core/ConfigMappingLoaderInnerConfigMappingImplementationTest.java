/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMappingContext;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.Secret;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConfigMappingLoaderInnerConfigMappingImplementationTest {
    @Test
    void registeredMappingLoadsPreGeneratedImplementationMetadataAndObject() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "service.host", "localhost",
                        "service.port", "8443",
                        "service.tags", "blue,green",
                        "service.credentials.username", "alice",
                        "service.credentials.password", "s3cr3t"), "mapping-properties"))
                .withMapping(ServiceMapping.class)
                .withValidateUnknown(false)
                .build();

        ServiceMapping mapping = config.getConfigMapping(ServiceMapping.class);

        assertThat(mapping.getClass().getName()).endsWith("ServiceMapping$$CMImpl");
        assertThat(mapping.host()).isEqualTo("localhost");
        assertThat(mapping.port()).isEqualTo(8443);
        assertThat(mapping.tags()).containsExactly("blue", "green");
        assertThat(mapping.credentials().username()).isEqualTo("alice");
        assertThat(mapping.credentials().password().get()).isEqualTo("s3cr3t");
    }

    @ConfigMapping(prefix = "service")
    public interface ServiceMapping {
        String host();

        int port();

        List<String> tags();

        Credentials credentials();
    }

    public interface Credentials {
        String username();

        Secret<String> password();
    }

    public static final class ServiceMapping$$CMImpl implements ServiceMapping {
        private static final Map<String, String> PROPERTIES;
        private static final Set<String> SECRETS = Set.of("credentials.password");

        static {
            Map<String, String> properties = new HashMap<>();
            properties.put("host", null);
            properties.put("port", null);
            properties.put("tags", null);
            properties.put("credentials.username", null);
            properties.put("credentials.password", null);
            PROPERTIES = Collections.unmodifiableMap(properties);
        }

        private final String host;
        private final int port;
        private final List<String> tags;
        private final Credentials credentials;

        public ServiceMapping$$CMImpl(final ConfigMappingContext context) {
            this.host = ConfigMappingContext.ObjectCreator.stringValue(context, true, "host");
            this.port = ConfigMappingContext.ObjectCreator.intValue(context, true, "port");
            this.tags = List.of(ConfigMappingContext.ObjectCreator.stringValue(context, true, "tags").split(","));
            this.credentials = new ServiceCredentials(
                    ConfigMappingContext.ObjectCreator.stringValue(context, true, "credentials.username"),
                    ConfigMappingContext.ObjectCreator.stringValue(context, true, "credentials.password"));
        }

        public static Map<String, String> getProperties() {
            return PROPERTIES;
        }

        public static Set<String> getSecrets() {
            return SECRETS;
        }

        @Override
        public String host() {
            return host;
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public List<String> tags() {
            return tags;
        }

        @Override
        public Credentials credentials() {
            return credentials;
        }
    }

    private static final class ServiceCredentials implements Credentials {
        private final String username;
        private final Secret<String> password;

        private ServiceCredentials(final String username, final String password) {
            this.username = username;
            this.password = new FixedSecret(password);
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public Secret<String> password() {
            return password;
        }
    }

    private static final class FixedSecret implements Secret<String> {
        private final String value;

        private FixedSecret(final String value) {
            this.value = value;
        }

        @Override
        public String get() {
            return value;
        }
    }
}
