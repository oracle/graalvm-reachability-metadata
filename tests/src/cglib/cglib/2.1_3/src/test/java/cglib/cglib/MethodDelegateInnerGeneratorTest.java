/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.reflect.MethodDelegate;
import net.sf.cglib.transform.NullClassVisitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class MethodDelegateInnerGeneratorTest {
    @Test
    void requiresPublicTargetMethodMatchingInterfaceParameters() {
        MethodDelegate.Generator generator = new MethodDelegate.Generator();
        generator.setTarget(new MessageService());
        generator.setMethodName("missingMessage");
        generator.setInterface(MessageFunction.class);

        assertThatExceptionOfType(NoSuchMethodException.class)
                .isThrownBy(() -> generator.generateClass(NullClassVisitor.INSTANCE))
                .withMessageContaining("missingMessage");
    }

    @Test
    void requiresReturnTypeAssignableToInterfaceReturnType() {
        MethodDelegate.Generator generator = new MethodDelegate.Generator();
        generator.setTarget(new MessageService());
        generator.setMethodName("messageLength");
        generator.setInterface(MessageFunction.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateClass(NullClassVisitor.INSTANCE))
                .withMessage("incompatible return types");
    }

    @Test
    void requiresInstanceTargetForInstanceMethod() {
        MethodDelegate.Generator generator = new MethodDelegate.Generator();
        generator.setTargetClass(MessageService.class);
        generator.setMethodName("message");
        generator.setInterface(MessageFunction.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateClass(NullClassVisitor.INSTANCE))
                .withMessage("Static method expected");
    }

    @Test
    void requiresClassTargetForStaticMethod() {
        MethodDelegate.Generator generator = new MethodDelegate.Generator();
        generator.setTarget(new MessageService());
        generator.setMethodName("staticMessage");
        generator.setInterface(MessageFunction.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateClass(NullClassVisitor.INSTANCE))
                .withMessage("Static method not expected");
    }

    public interface MessageFunction {
        String apply(String name);
    }

    public static class MessageService {
        public String message(String name) {
            return "hello " + name;
        }

        public int messageLength(String name) {
            return name.length();
        }

        public static String staticMessage(String name) {
            return "welcome " + name;
        }
    }
}
