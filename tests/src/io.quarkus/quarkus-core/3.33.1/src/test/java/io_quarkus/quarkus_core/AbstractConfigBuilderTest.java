/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.configuration.AbstractConfigBuilder;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

public class AbstractConfigBuilderTest {
    @Test
    void appliesCustomizerSelectedByClassName() {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder().addDefaultInterceptors();
        AbstractConfigBuilder.withCustomizer(builder, GreetingCustomizer.class.getName());
        SmallRyeConfig config = builder.build();

        assertThat(config.getValue("app.greeting", String.class)).isEqualTo("Welcome, reader");
        assertThat(config.getValue("app.limit", Integer.class)).isEqualTo(12);
    }

    @Test
    void appliesCustomizerInstanceWithUserOverrides() {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(Map.of("app.name", "operator"), "user-settings"));
        AbstractConfigBuilder.withCustomizer(builder, new GreetingCustomizer());
        SmallRyeConfig config = builder.build();

        assertThat(config.getValue("app.greeting", String.class)).isEqualTo("Welcome, operator");
    }

    public static class GreetingCustomizer implements SmallRyeConfigBuilderCustomizer {
        @Override
        public void configBuilder(SmallRyeConfigBuilder builder) {
            builder.withDefaultValue("app.name", "reader")
                    .withDefaultValue("app.greeting", "Welcome, ${app.name}")
                    .withDefaultValue("app.limit", "12");
        }
    }
}
