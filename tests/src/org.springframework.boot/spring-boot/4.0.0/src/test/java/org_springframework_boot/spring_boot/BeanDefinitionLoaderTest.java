/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDefinitionLoaderTest {
    @Test
    void loadsAnApplicationSourceClassIntoAContext() {
        SourceApplication application = new SourceApplication();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            application.loadSources(context, Greeting.class);
            context.refresh();

            assertThat(context.getBean(Greeting.class).message()).isEqualTo("hello from a source bean");
        }
    }

    @Test
    void resolvesAnApplicationSourceByItsClassName() {
        SourceApplication application = new SourceApplication();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            application.loadSources(context, Greeting.class.getName());
            context.refresh();

            assertThat(context.getBean(Greeting.class).message()).isEqualTo("hello from a source bean");
        }
    }

    public static class SourceApplication extends SpringApplication {
        public SourceApplication() {
            super(Greeting.class);
        }

        public void loadSources(ApplicationContext context, Object... sources) {
            super.load(context, sources);
        }
    }

    public static class Greeting {
        public String message() {
            return "hello from a source bean";
        }
    }
}
