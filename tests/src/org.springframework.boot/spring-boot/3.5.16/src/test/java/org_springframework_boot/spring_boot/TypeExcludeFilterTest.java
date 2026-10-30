/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org_springframework_boot.spring_boot.scanfixture.InternalEndpoint;
import org_springframework_boot.spring_boot.scanfixture.PublicEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeExcludeFilterTest {

    @Test
    void delegatesExclusionsToFiltersFromTheBeanFactory() throws IOException {
        SpringApplication application = new SpringApplication(TypeExcludeFilter.class);
        application.setMainApplicationClass(TypeExcludeFilterTest.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setApplicationContextFactory(webType -> new GenericApplicationContext());
        application.addInitializers((AotApplicationContextInitializer<GenericApplicationContext>) context -> {
            GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClassName("org.springframework.boot.context.TypeExcludeFilter");
            context.registerBeanDefinition("exclusionFilter", definition);
        });

        try (ConfigurableApplicationContext applicationContext = application.run();
                GenericApplicationContext context = new GenericApplicationContext()) {
            InternalEndpointExclusion exclusion = new InternalEndpointExclusion();
            context.getBeanFactory().registerSingleton("internalEndpointExclusion", exclusion);
            TypeExcludeFilter filter = applicationContext.getBean("exclusionFilter", TypeExcludeFilter.class);
            filter.setBeanFactory(context.getBeanFactory());
            MetadataReaderFactory readers = new SimpleMetadataReaderFactory();

            assertThat(filter.match(readers.getMetadataReader(InternalEndpoint.class.getName()), readers)).isTrue();
            assertThat(filter.match(readers.getMetadataReader(PublicEndpoint.class.getName()), readers)).isFalse();
        }
    }

    public static class InternalEndpointExclusion extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            return reader.getClassMetadata().getClassName().endsWith(".InternalEndpoint");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InternalEndpointExclusion;
        }

        @Override
        public int hashCode() {
            return InternalEndpointExclusion.class.hashCode();
        }
    }
}
