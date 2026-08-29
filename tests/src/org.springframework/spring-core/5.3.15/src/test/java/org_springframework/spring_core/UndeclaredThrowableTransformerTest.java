/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.transform.impl.UndeclaredThrowableTransformer;

/** Verifies validation of the configured throwable wrapper constructor. */
public class UndeclaredThrowableTransformerTest {
    @Test
    void acceptsWrapperWithThrowableConstructor() {
        UndeclaredThrowableTransformer transformer =
                new UndeclaredThrowableTransformer(ThrowableWrapper.class);

        assertThat(transformer).isNotNull();
    }

    public static final class ThrowableWrapper extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ThrowableWrapper(Throwable cause) {
            super(cause);
        }
    }
}
