/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.BeanInstantiationException;
import io.smallrye.beanbag.NoSuchBeanException;

public class ConstructorSupplierTest {
    @Test
    void buildsBeanWithConstructorDependency() throws Exception {
        BeanBag beanBag = beanBagUsingConstructor(ConstructedGreeting.class);

        ConstructedGreeting greeting = beanBag.requireBean(ConstructedGreeting.class);

        assertThat(greeting.message()).isEqualTo("Hello, constructor supplier");
    }

    @Test
    void wrapsExceptionThrownByConstructor() throws Exception {
        BeanBag beanBag = beanBagUsingConstructor(ThrowingGreeting.class);

        assertThatThrownBy(() -> beanBag.requireBean(ThrowingGreeting.class))
                .isInstanceOf(NoSuchBeanException.class)
                .hasMessageStartingWith("No matching bean available")
                .satisfies(throwable -> {
                    assertThat(throwable.getSuppressed()).hasSize(1);
                    Throwable suppressed = throwable.getSuppressed()[0];
                    assertThat(suppressed)
                            .isInstanceOf(BeanInstantiationException.class)
                            .hasMessage("Constructor invocation failed")
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("constructor rejected: constructor supplier");
                });
    }

    private static <T> BeanBag beanBagUsingConstructor(Class<T> beanType) throws NoSuchMethodException {
        Constructor<T> constructor = beanType.getConstructor(MessageSource.class);
        BeanBag.Builder builder = BeanBag.builder();
        builder.addBeanInstance(new MessageSource("constructor supplier"));
        builder.addBean(beanType)
                .buildSupplier()
                .setConstructor(constructor)
                .addConstructorArgument(MessageSource.class)
                .build()
                .build();
        return builder.build();
    }

    public static final class ConstructedGreeting {
        private final MessageSource source;

        public ConstructedGreeting(MessageSource source) {
            this.source = source;
        }

        String message() {
            return "Hello, " + source.text();
        }
    }

    public static final class ThrowingGreeting {
        public ThrowingGreeting(MessageSource source) {
            throw new IllegalStateException("constructor rejected: " + source.text());
        }
    }

    public static final class MessageSource {
        private final String text;

        public MessageSource(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }
}
