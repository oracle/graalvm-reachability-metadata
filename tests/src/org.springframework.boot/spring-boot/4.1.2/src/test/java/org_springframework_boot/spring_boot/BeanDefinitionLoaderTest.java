/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org_springframework_boot.spring_boot.scanned.ScannedInventory;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDefinitionLoaderTest {
    @Test
    void loadsClassSourcesAsApplicationBeans() {
        SourceApplication application = new SourceApplication();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            application.registerSources(context, Inventory.class);
            context.refresh();

            assertThat(context.getBean(Inventory.class).label()).isEqualTo("inventory");
            assertThat(context.getBeansOfType(Inventory.class)).hasSize(1);
        }
    }

    @Test
    void discoversBeansFromAPackageNameSource() {
        SourceApplication application = new SourceApplication();
        application.setResourceLoader(new InventoryResourceResolver());
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setApplicationContextFactory(ApplicationContextFactory.of(GenericApplicationContext::new));
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setListeners(List.of());
        application.setInitializers(List.of((AotApplicationContextInitializer<GenericApplicationContext>) context ->
                application.registerSources(context, "org_springframework_boot.spring_boot.scanned")));
        try (GenericApplicationContext context = (GenericApplicationContext) application.run()) {

            assertThat(context.containsBean("scannedInventory")).isTrue();
            assertThat(context.getBean(ScannedInventory.class).toString()).isEqualTo("scanned inventory");
        }
    }

    public static class InventoryResourceResolver extends PathMatchingResourcePatternResolver {
        @Override
        public Resource[] getResources(String locationPattern) throws IOException {
            if (locationPattern.equals("classpath*:org_springframework_boot/spring_boot/scanned/**/*.class")) {
                return new Resource[] {new ClassPathResource(
                        "org_springframework_boot/spring_boot/scanned/ScannedInventory.class")};
            }
            return super.getResources(locationPattern);
        }
    }

    public static class SourceApplication extends SpringApplication {
        public SourceApplication() {
            super(Inventory.class);
        }

        public void registerSources(GenericApplicationContext context, Object... sources) {
            super.load(context, sources);
        }
    }

    public static class Inventory {
        public String label() {
            return "inventory";
        }
    }
}
