/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata_tests.ognl.ognl;

import ognl.AbstractMemberAccess;
import ognl.Node;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.enhance.ExpressionCompiler;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
    void resolvesPublicMethodAndCollectionTypes() throws Exception {
        final ExpressionCompiler compiler = new ExpressionCompiler();
        final Method implementationMethod = MethodRoot.class.getMethod("describe", String.class);

        assertThat(compiler.getSuperOrInterfaceClass(implementationMethod, MethodRoot.class))
                .isEqualTo(Described.class);
        assertThat(compiler.getInterfaceClass(ArrayList.class)).isEqualTo(List.class);
        assertThat(compiler.getSuperOrInterfaceClass(implementationMethod, Object.class)).isNull();
    }

    @Test
    void compilesExpressionIntoGeneratedAccessor() throws Exception {
        final OgnlContext context = newContext();
        final PropertyRoot root = new PropertyRoot("compiled-value");

        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            final Node expression = Ognl.compileExpression(context, root, "value");

            assertThat(expression.getAccessor()).isNotNull();
            assertThat(Ognl.getValue(expression, context, root)).isEqualTo("compiled-value");
        });
    }

    private static OgnlContext newContext() {
        return new OgnlContext(null, null, new AllowAllMemberAccess());
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
