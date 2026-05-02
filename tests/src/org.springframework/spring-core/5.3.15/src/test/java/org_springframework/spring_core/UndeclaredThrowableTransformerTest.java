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

public class UndeclaredThrowableTransformerTest {

    @Test
    void acceptsWrapperExceptionWithThrowableConstructor() {
        UndeclaredThrowableTransformer transformer = new UndeclaredThrowableTransformer(
                WrapperException.class
        );

        assertThat(transformer).isNotNull();
    }

    @Test
    void rejectsWrapperExceptionWithoutThrowableConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UndeclaredThrowableTransformer(MissingThrowableConstructorException.class))
                .withMessageContaining("single-arg constructor that takes a Throwable");
    }

    public static class WrapperException extends RuntimeException {

        public WrapperException(Throwable cause) {
            super(cause);
        }
    }

    public static class MissingThrowableConstructorException extends RuntimeException {

        public MissingThrowableConstructorException(String message) {
            super(message);
        }
    }
}
