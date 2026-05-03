/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package fm;

import static org.assertj.core.api.Assertions.assertThat;

import freemarker.core._ObjectBuilderSettingEvaluator;
import freemarker.core._SettingEvaluationEnvironment;
import org.junit.jupiter.api.Test;

public class FreemarkerCoreObjectBuilderSettingEvaluatorInnerBuilderCallExpressionTest {

    @Test
    void evaluatesLegacyConstructorExpressionWithoutParentheses() throws Exception {
        Object result = evaluate(LegacyConstructed.class.getName(), LegacyConstructed.class);

        assertThat(result).isInstanceOf(LegacyConstructed.class);
    }

    @Test
    void resolvesStaticFieldExpression() throws Exception {
        String expression = StaticFieldHolder.class.getName() + ".STATIC_VALUE";

        Object result = evaluate(expression, Object.class);

        assertThat(result).isSameAs(StaticFieldHolder.STATIC_VALUE);
    }

    @Test
    void returnsPublicInstanceFieldForNoArgumentPseudoConstructor() throws Exception {
        Object result = evaluate(InstanceHolder.class.getName() + "()", InstanceHolder.class);

        assertThat(result).isSameAs(InstanceHolder.INSTANCE);
    }

    @Test
    void createsNoArgumentObjectWhenNoInstanceFieldExists() throws Exception {
        Object result = evaluate(DefaultConstructed.class.getName() + "()", DefaultConstructed.class);

        assertThat(result).isInstanceOf(DefaultConstructed.class);
    }

    @Test
    void invokesBuilderBuildMethodWhenBuilderClassExists() throws Exception {
        Object result = evaluate(BuiltObject.class.getName() + "()", BuiltObject.class);

        assertThat(result).isSameAs(BuiltObject.BUILT);
    }

    private static Object evaluate(String expression, Class<?> expectedClass) throws Exception {
        return _ObjectBuilderSettingEvaluator.eval(
                expression,
                expectedClass,
                false,
                _SettingEvaluationEnvironment.getCurrent());
    }

    public static class LegacyConstructed {
    }

    public static class StaticFieldHolder {
        public static final Object STATIC_VALUE = new Object();
    }

    public static class InstanceHolder {
        public static final InstanceHolder INSTANCE = new InstanceHolder();
    }

    public static class DefaultConstructed {
    }

    public static class BuiltObject {
        public static final BuiltObject BUILT = new BuiltObject();
    }

    public static class BuiltObjectBuilder {
        public BuiltObject build() {
            return BuiltObject.BUILT;
        }
    }

}
