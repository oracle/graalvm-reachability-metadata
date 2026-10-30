/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceDescriptor;
import org.springframework.core.io.support.PropertySourceProcessor;

/** Verifies property source processing instantiates the configured factory. */
public class PropertySourceProcessorTest {
    @Test
    void loadsNamedPropertiesThroughCustomFactory() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        PropertySourceProcessor processor = new PropertySourceProcessor(environment, new DefaultResourceLoader());
        PropertySourceDescriptor descriptor = new PropertySourceDescriptor(
                List.of("classpath:org_springframework/spring_core/dynamic-access.properties"),
                false, "library-settings", NamedFactory.class, "UTF-8");

        processor.processPropertySource(descriptor);

        assertThat(environment.getPropertySources().get("custom-library-settings").getProperty("library"))
                .isEqualTo("spring-core");
        assertThat(environment.getProperty("library")).isEqualTo("spring-core");
    }

    public static class NamedFactory extends DefaultPropertySourceFactory {
        public NamedFactory() { }

        @Override
        public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
            return super.createPropertySource("custom-" + name, resource);
        }
    }
}
