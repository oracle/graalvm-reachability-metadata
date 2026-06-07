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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ResourcePatternHint;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.util.ClassUtils;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.regions.Region;

public class Spring_cloud_aws_coreTest {
    @Test
    void springCloudClientConfigurationAddsSpringCloudAwsUserAgentSuffix() {
        SpringCloudClientConfiguration configuration = new SpringCloudClientConfiguration("test-version");

        String userAgentSuffix = configuration.clientOverrideConfiguration()
                .advancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX)
                .orElseThrow();

        assertThat(userAgentSuffix).isEqualTo("spring-cloud-aws/test-version");
    }

    @Test
    void defaultSpringCloudClientConfigurationLoadsPackagedVersionResource() {
        SpringCloudClientConfiguration configuration = new SpringCloudClientConfiguration();

        String userAgentSuffix = configuration.clientOverrideConfiguration()
                .advancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX)
                .orElseThrow();

        assertThat(userAgentSuffix).startsWith("spring-cloud-aws/");
        assertThat(userAgentSuffix).doesNotEndWith("/");
    }

    @Test
    void staticRegionProviderAlwaysReturnsConfiguredRegion() {
        StaticRegionProvider provider = new StaticRegionProvider("eu-central-1");

        assertThat(provider.getRegion()).isEqualTo(Region.EU_CENTRAL_1);
        assertThat(provider.getRegion()).isSameAs(provider.getRegion());
    }

    @Test
    void staticRegionProviderRequiresRegionIdentifier() {
        assertThatThrownBy(() -> new StaticRegionProvider(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region is required");
    }

    @Test
    void awsPropertySourceCanBeImplementedAsLazyCopyablePropertySource() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("cloud.aws.region.static", "us-east-1");
        source.put("cloud.aws.stack.auto", false);
        InMemoryAwsPropertySource propertySource = new InMemoryAwsPropertySource("aws", source);

        assertThat(propertySource.getName()).isEqualTo("aws");
        assertThat(propertySource.getSource()).isSameAs(source);
        assertThat(propertySource.getPropertyNames()).isEmpty();

        propertySource.init();

        assertThat(propertySource.getPropertyNames())
                .containsExactly("cloud.aws.region.static", "cloud.aws.stack.auto");
        assertThat(propertySource.getProperty("cloud.aws.region.static")).isEqualTo("us-east-1");
        assertThat(propertySource.getProperty("cloud.aws.stack.auto")).isEqualTo(false);
        assertThat(propertySource.copy().getPropertyNames()).isEmpty();
    }

    @Test
    void runtimeHintsRegisterClientConfigurationVersionResource() {
        RuntimeHints hints = new RuntimeHints();

        new AWSCoreRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(hints.resources().resourcePatternHints()
                .flatMap(resourcePatternHints -> resourcePatternHints.getIncludes().stream())
                .map(ResourcePatternHint::getPattern))
                .contains("io/awspring/cloud/core/SpringCloudClientConfiguration.properties");
    }

    @Test
    void jacksonPresenceReportsClasspathAvailability() {
        boolean jackson2Present = isClassPresent("com.fasterxml.jackson.databind.ObjectMapper")
                && isClassPresent("com.fasterxml.jackson.core.JsonGenerator");
        boolean jackson3Present = isClassPresent("tools.jackson.databind.ObjectMapper")
                && isClassPresent("tools.jackson.core.JsonGenerator");

        assertThat(JacksonPresent.isJackson2Present()).isEqualTo(jackson2Present);
        assertThat(JacksonPresent.isJackson3Present()).isEqualTo(jackson3Present);
    }

    private static boolean isClassPresent(String className) {
        return ClassUtils.isPresent(className, ClassUtils.getDefaultClassLoader());
    }

    private static final class InMemoryAwsPropertySource
            extends AwsPropertySource<InMemoryAwsPropertySource, Map<String, Object>> {
        private final Map<String, Object> initializedProperties = new LinkedHashMap<>();

        private InMemoryAwsPropertySource(String name, Map<String, Object> source) {
            super(name, source);
        }

        @Override
        public void init() {
            initializedProperties.clear();
            initializedProperties.putAll(getSource());
        }

        @Override
        public InMemoryAwsPropertySource copy() {
            return new InMemoryAwsPropertySource(getName(), getSource());
        }

        @Override
        public String[] getPropertyNames() {
            return initializedProperties.keySet().toArray(String[]::new);
        }

        @Override
        public Object getProperty(String name) {
            return initializedProperties.get(name);
        }
    }
}
