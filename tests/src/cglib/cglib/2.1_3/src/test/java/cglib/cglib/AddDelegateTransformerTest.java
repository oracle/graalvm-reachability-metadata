/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.transform.impl.AddDelegateTransformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class AddDelegateTransformerTest {
    @Test
    void requiresDelegateConstructorAcceptingTheTargetObject() {
        assertThatExceptionOfType(CodeGenerationException.class)
                .isThrownBy(() -> new AddDelegateTransformer(new Class<?>[]{Runnable.class}, Task.class))
                .withCauseInstanceOf(NoSuchMethodException.class);
    }

    public static class Task implements Runnable {
        public Task() {
        }

        @Override
        public void run() {
        }
    }
}
