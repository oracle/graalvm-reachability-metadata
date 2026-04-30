/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.BeanBag;

public class MethodInjectorTest {
    @Test
    void injectsDependencyThroughConfiguredMethod() throws Exception {
        BeanBag beanBag = beanBagUsingMethodInjection();

        GreetingService greetingService = beanBag.requireBean(GreetingService.class);

        assertThat(greetingService.greeting()).isEqualTo("Hello, method injection");
        assertThat(greetingService.injectionCount()).isEqualTo(1);
    }

    private static BeanBag beanBagUsingMethodInjection() throws NoSuchMethodException {
        Constructor<GreetingService> constructor = GreetingService.class.getConstructor();
        Method method = GreetingService.class.getMethod("setMessageSource", MessageSource.class);
        BeanBag.Builder builder = BeanBag.builder();
        builder.addBeanInstance(new MessageSource("method injection"));
        builder.addBean(GreetingService.class)
                .buildSupplier()
                .setConstructor(constructor)
                .injectMethod(method)
                .build()
                .build();
        return builder.build();
    }

    public static final class GreetingService {
        private MessageSource messageSource;
        private int injectionCount;

        public GreetingService() {
        }

        public void setMessageSource(MessageSource messageSource) {
            this.messageSource = messageSource;
            injectionCount++;
        }

        String greeting() {
            return "Hello, " + messageSource.text();
        }

        int injectionCount() {
            return injectionCount;
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
