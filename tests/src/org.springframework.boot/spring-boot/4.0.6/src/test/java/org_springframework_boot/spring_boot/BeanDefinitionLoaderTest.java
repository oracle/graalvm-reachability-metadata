/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDefinitionLoaderTest {

    private static final String ADMIN_PACKAGE_SOURCE = "org.springframework.boot.admin";

    private static final String RETRY_PACKAGE_SOURCE = "org.springframework.boot.retry";

    @Test
    void runAcceptsLibraryPackageNameSourcesDiscoveredFromClasspath() {
        SpringApplication application = new SpringApplication();
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setSources(Set.of(ADMIN_PACKAGE_SOURCE, RETRY_PACKAGE_SOURCE));
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.isActive()).isTrue();
        }
    }

}
