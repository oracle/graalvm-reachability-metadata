/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_awspring_cloud.spring_cloud_aws_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.awspring.cloud.core.AWSCoreRuntimeHints;
import io.awspring.cloud.core.SpringCloudClientConfiguration;
import io.awspring.cloud.core.config.AwsPropertySource;
import io.awspring.cloud.core.region.StaticRegionProvider;
import io.awspring.cloud.core.support.JacksonPresent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.core.io.support.SpringFactoriesLoader;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.regions.Region;

public class Spring_cloud_aws_coreTest {
    @Test
    void staticRegionProviderReturnsConfiguredRegion() {
        StaticRegionProvider provider = new StaticRegionProvider(Region.US_EAST_1.id());

        assertThat(provider.getRegion()).isEqualTo(Region.US_EAST_1);
        assertThat(provider.getRegion().id()).isEqualTo("us-east-1");
    }

    @Test
    void staticRegionProviderRejectsMissingOrBlankRegion() {
        assertThatThrownBy(() -> new StaticRegionProvider(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region is required");

        assertThatThrownBy(() -> new StaticRegionProvider(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The region '' is not a valid region!");
    }

    @Test
    void clientConfigurationBuildsSpringCloudAwsUserAgentSuffix() {
        SpringCloudClientConfiguration configuration = new SpringCloudClientConfiguration("test-suite");

        ClientOverrideConfiguration overrideConfiguration = configuration.clientOverrideConfiguration();

        assertThat(overrideConfiguration.advancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX))
                .hasValue("spring-cloud-aws/test-suite");
        assertThat(overrideConfiguration.advancedOption(SdkAdvancedClientOption.USER_AGENT_PREFIX)).isEmpty();
    }

    @Test
    void defaultClientConfigurationLoadsVersionFromPropertiesResource() {
        SpringCloudClientConfiguration configuration = new SpringCloudClientConfiguration();

        ClientOverrideConfiguration overrideConfiguration = configuration.clientOverrideConfiguration();

        assertThat(overrideConfiguration.advancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX))
                .hasValueSatisfying(userAgent -> {
                    String expectedPrefix = "spring-cloud-aws/";
                    assertThat(userAgent).startsWith(expectedPrefix);
                    assertThat(userAgent.substring(expectedPrefix.length())).isNotBlank();
                });
    }

    @Test
    void runtimeHintsRegisterClientConfigurationPropertiesResource() {
        RuntimeHints hints = new RuntimeHints();

        new AWSCoreRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.resource()
                .forResource("io/awspring/cloud/core/SpringCloudClientConfiguration.properties"))
                .accepts(hints);
    }

    @Test
    void runtimeHintsRegistrarIsLoadedFromSpringAotFactories() {
        ClassLoader classLoader = getClass().getClassLoader();

        List<RuntimeHintsRegistrar> registrars = SpringFactoriesLoader
                .forResourceLocation("META-INF/spring/aot.factories", classLoader)
                .load(RuntimeHintsRegistrar.class);

        assertThat(registrars).hasAtLeastOneElementOfType(AWSCoreRuntimeHints.class);
    }

    @Test
    void jacksonPresenceFlagsMatchCoreClasspath() {
        assertThat(JacksonPresent.isJackson2Present()).isFalse();
        assertThat(JacksonPresent.isJackson3Present()).isFalse();
    }

    @Test
    void awsPropertySourceKeepsNameSourceAndCreatesUninitializedCopy() {
        InMemoryAwsPropertySource propertySource = new InMemoryAwsPropertySource("aws-source",
                Map.of("first", "one", "second", "two"));

        assertThat(propertySource.getName()).isEqualTo("aws-source");
        assertThat(propertySource.getProperty("first")).isEqualTo("one");
        assertThat(propertySource.getPropertyNames()).containsExactlyInAnyOrder("first", "second");
        assertThat(propertySource.isInitialized()).isFalse();

        propertySource.init();
        InMemoryAwsPropertySource copy = propertySource.copy();

        assertThat(propertySource.isInitialized()).isTrue();
        assertThat(copy).isNotSameAs(propertySource);
        assertThat(copy.getName()).isEqualTo(propertySource.getName());
        assertThat(copy.getSource()).containsExactlyInAnyOrderEntriesOf(propertySource.getSource());
        assertThat(copy.isInitialized()).isFalse();
    }

    private static final class InMemoryAwsPropertySource
            extends AwsPropertySource<InMemoryAwsPropertySource, Map<String, String>> {
        private boolean initialized;

        private InMemoryAwsPropertySource(String name, Map<String, String> source) {
            super(name, new LinkedHashMap<>(source));
        }

        @Override
        public void init() {
            initialized = true;
        }

        @Override
        public InMemoryAwsPropertySource copy() {
            return new InMemoryAwsPropertySource(getName(), getSource());
        }

        @Override
        public Object getProperty(String name) {
            return getSource().get(name);
        }

        @Override
        public String[] getPropertyNames() {
            return getSource().keySet().toArray(String[]::new);
        }

        private boolean isInitialized() {
            return initialized;
        }
    }
}
