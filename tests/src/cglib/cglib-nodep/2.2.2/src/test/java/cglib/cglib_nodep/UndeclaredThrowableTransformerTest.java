/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import net.sf.cglib.transform.impl.UndeclaredThrowableTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UndeclaredThrowableTransformerTest {
    @Test
    void acceptsWrapperWithThrowableConstructor() {
        UndeclaredThrowableTransformer transformer = new UndeclaredThrowableTransformer(
                ThrowableWrapper.class
        );

        assertThat(transformer).isNotNull();
    }

    public static final class ThrowableWrapper extends RuntimeException {
        public ThrowableWrapper(Throwable cause) {
            super(cause);
        }
    }
}
