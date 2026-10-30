/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.List;

import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

final class TestApplication extends SpringApplication {

    TestApplication(Class<?> source) {
        super(source);
        setMainApplicationClass(source);
        setWebApplicationType(WebApplicationType.NONE);
        setBannerMode(Banner.Mode.OFF);
        setLogStartupInfo(false);
        setRegisterShutdownHook(false);
        setAllowBeanDefinitionOverriding(true);
        setApplicationContextFactory(ApplicationContextFactory.of(GenericApplicationContext::new));
        setInitializers(List.of((AotApplicationContextInitializer<GenericApplicationContext>) context ->
                context.getBeanFactory().registerSingleton("launchMarker", "initialized")));
    }

    @Override
    protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        super.postProcessApplicationContext(context);
        // Load the explicitly supplied sources as part of this application's custom lifecycle.
        load(context, getAllSources().toArray());
    }
}
