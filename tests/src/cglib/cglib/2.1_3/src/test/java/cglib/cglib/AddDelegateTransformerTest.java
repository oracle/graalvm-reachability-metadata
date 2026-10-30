/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.core.Constants;
import net.sf.cglib.transform.NullClassVisitor;
import net.sf.cglib.transform.impl.AddDelegateTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AddDelegateTransformerTest {
    @Test
    void requiresDelegateConstructorAcceptingTheTargetObject() {
        assertThatExceptionOfType(CodeGenerationException.class)
                .isThrownBy(() -> new AddDelegateTransformer(new Class<?>[]{Runnable.class}, Task.class))
                .withCauseInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void requiresImplementationOfEveryDelegatedMethod() {
        AddDelegateTransformer transformer = new AddDelegateTransformer(
                new Class<?>[]{TextSource.class}, MissingTextDelegate.class);
        transformer.setTarget(NullClassVisitor.INSTANCE);

        assertThatExceptionOfType(CodeGenerationException.class)
                .isThrownBy(() -> transformer.begin_class(Constants.V1_2, Constants.ACC_PUBLIC,
                        "cglib.cglib.DelegatingText", Type.getType(Object.class), new Type[0], null))
                .withCauseInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void requiresMatchingReturnTypeForDelegatedMethod() {
        AddDelegateTransformer transformer = new AddDelegateTransformer(
                new Class<?>[]{TextSource.class}, NumericTextDelegate.class);
        transformer.setTarget(NullClassVisitor.INSTANCE);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> transformer.begin_class(Constants.V1_2, Constants.ACC_PUBLIC,
                        "cglib.cglib.DelegatingText", Type.getType(Object.class), new Type[0], null))
                .withMessageContaining("Invalid delegate signature");
    }

    public interface TextSource {
        String text();
    }

    public static class MissingTextDelegate {
        public MissingTextDelegate(Object target) {
        }
    }

    public static class NumericTextDelegate {
        public NumericTextDelegate(Object target) {
        }

        public int text() {
            return 1;
        }
    }

    public static class Task implements Runnable {
        public Task() {
        }

        @Override
        public void run() {
        }
    }
}
