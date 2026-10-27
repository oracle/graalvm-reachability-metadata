/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.transform.impl.UndeclaredThrowableTransformer;

/** Verifies validation of throwable wrapper constructors. */
public class UndeclaredThrowableTransformerTest {
    @Test
    void acceptsWrapperWithThrowableConstructor() {
        UndeclaredThrowableTransformer transformer =
                new UndeclaredThrowableTransformer(ThrowableWrapper.class);

        assertThat(transformer).isNotNull();
    }

    @Test
    void rejectsWrapperWithoutThrowableConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UndeclaredThrowableTransformer(MessageOnlyWrapper.class))
                .withMessageContaining("single-arg constructor that takes a Throwable");
    }

    public static final class ThrowableWrapper extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ThrowableWrapper(Throwable cause) {
            super(cause);
        }
    }

    public static final class MessageOnlyWrapper extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MessageOnlyWrapper(String message) {
            super(message);
        }
    }
}
