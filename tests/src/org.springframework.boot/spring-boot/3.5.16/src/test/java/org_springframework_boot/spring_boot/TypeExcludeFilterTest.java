/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeExcludeFilterTest {

    @Test
    void componentScanningConstructsTheFilterAndAppliesBeanFactoryExclusions() {
        SpringApplication application = new SpringApplication(ApplicationSource.class);
        application.setMainApplicationClass(EndpointConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setApplicationContextFactory(ApplicationContextFactory.of(AnnotationConfigApplicationContext::new));
        application.addInitializers((AotApplicationContextInitializer<AnnotationConfigApplicationContext>) context -> {
            context.getBeanFactory().registerSingleton("internalEndpointExclusion", new InternalEndpointExclusion());
            context.register(EndpointConfiguration.class);
        });

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.containsBean("publicEndpoint")).isTrue();
            assertThat(context.getBean("publicEndpoint").toString()).isEqualTo("public endpoint");
            assertThat(context.containsBean("internalEndpoint")).isFalse();
        }
    }

    public static class ApplicationSource {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "org_springframework_boot.spring_boot.scanfixture",
            excludeFilters = @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
    public static class EndpointConfiguration {
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
