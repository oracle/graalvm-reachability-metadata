/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.BeanBag;

public class FieldInjectorTest {
    @Test
    void injectsDependencyIntoConfiguredField() throws Exception {
        BeanBag beanBag = beanBagUsingFieldInjection();

        GreetingService greetingService = beanBag.requireBean(GreetingService.class);

        assertThat(greetingService.greeting()).isEqualTo("Hello, field injection");
    }

    private static BeanBag beanBagUsingFieldInjection() throws NoSuchMethodException, NoSuchFieldException {
        Constructor<GreetingService> constructor = GreetingService.class.getConstructor();
        Field messageSourceField = GreetingService.class.getField("messageSource");
        BeanBag.Builder builder = BeanBag.builder();
        builder.addBeanInstance(new MessageSource("field injection"));
        builder.addBean(GreetingService.class)
                .buildSupplier()
                .setConstructor(constructor)
                .injectField(messageSourceField)
                .build()
                .build();
        return builder.build();
    }

    public static final class GreetingService {
        public MessageSource messageSource;

        public GreetingService() {
        }

        String greeting() {
            return "Hello, " + messageSource.text();
        }
    }

    public static final class MessageSource {
        private final String text;

        MessageSource(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }
}
