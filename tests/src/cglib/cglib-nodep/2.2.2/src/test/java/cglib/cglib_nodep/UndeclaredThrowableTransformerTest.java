/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.sf.cglib.transform.impl.UndeclaredThrowableTransformer;
import org.junit.jupiter.api.Test;

public class UndeclaredThrowableTransformerTest {
    @Test
    void acceptsWrapperWithPublicThrowableConstructor() {
        UndeclaredThrowableTransformer transformer = new UndeclaredThrowableTransformer(WrapperException.class);

        assertThat(transformer).isNotNull();
    }

    @Test
    void rejectsWrapperWithoutPublicThrowableConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UndeclaredThrowableTransformer(NoThrowableConstructorException.class))
                .withMessageContaining("does not have a single-arg constructor that takes a Throwable");
    }

    public static class WrapperException extends RuntimeException {
        public WrapperException(Throwable cause) {
            super(cause);
        }
    }

    public static class NoThrowableConstructorException extends RuntimeException {
        public NoThrowableConstructorException(String message) {
            super(message);
        }
    }
}
