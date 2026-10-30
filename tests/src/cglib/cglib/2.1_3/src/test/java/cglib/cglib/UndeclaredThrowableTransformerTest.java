/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.transform.impl.UndeclaredThrowableTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class UndeclaredThrowableTransformerTest {
    @Test
    void rejectsWrapperWithoutThrowableConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UndeclaredThrowableTransformer(MessageOnlyException.class))
                .withMessageContaining("does not have a single-arg constructor that takes a Throwable");
    }

    public static class MessageOnlyException extends RuntimeException {
        public MessageOnlyException(String message) {
            super(message);
        }
    }
}
