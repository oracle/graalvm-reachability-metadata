/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;

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
