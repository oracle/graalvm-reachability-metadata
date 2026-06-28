/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.Secret;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

public class ConfigMappingLoaderInnerConfigMappingImplementationTest {
    @Test
    void registeredMappingLoadsGeneratedImplementationMetadataAndObject() {
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
}
