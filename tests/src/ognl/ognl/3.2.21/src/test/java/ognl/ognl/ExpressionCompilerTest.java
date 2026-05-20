/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import javassist.NotFoundException;
import ognl.AbstractMemberAccess;
import ognl.Node;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.enhance.ExpressionCompiler;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionCompilerTest {
    @Test
    void discoversMatchingMethodOnPublicInterface() throws Exception {
        final ExpressionCompiler compiler = new ExpressionCompiler();
        final Method implementationMethod = MethodRoot.class.getMethod("describe", String.class);

        assertThat(compiler.containsMethod(implementationMethod, Described.class)).isTrue();
    }

    @Test
    void compilesExpressionIntoGeneratedAccessor() throws Exception {
        final OgnlContext context = newContext();
        final PropertyRoot root = new PropertyRoot("compiled-value");

        try {
            final Node expression = Ognl.compileExpression(context, root, "value");

            assertThat(expression.getAccessor()).isNotNull();
            assertThat(Ognl.getValue(expression, context, root)).isEqualTo("compiled-value");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            final Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null
                    && !isNativeImageRuntimeJavassistNotFound(exception)) {
                throw exception;
            }
        }
    }

    private static OgnlContext newContext() {
        return new OgnlContext(null, null, new AllowAllMemberAccess());
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return (Error) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean isNativeImageRuntimeJavassistNotFound(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NotFoundException
                    && "java.lang.Object".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class AllowAllMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return true;
        }
    }

    public interface Described {
        String describe(String prefix);
    }

    public static final class MethodRoot implements Described {
        @Override
        public String describe(String prefix) {
            return prefix + " method";
        }
    }

    public static final class PropertyRoot {
        private String value;

        public PropertyRoot(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
