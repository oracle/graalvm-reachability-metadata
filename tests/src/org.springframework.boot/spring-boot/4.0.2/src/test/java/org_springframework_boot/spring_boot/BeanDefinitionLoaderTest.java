/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDefinitionLoaderTest {

    @Test
    void registersAndInstantiatesAPlainApplicationSource() {
        TestApplication application = new TestApplication(MessageService.class);

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getBean(MessageService.class).greet("reader")).isEqualTo("Hello reader");
        }
    }

    public static class MessageService {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
