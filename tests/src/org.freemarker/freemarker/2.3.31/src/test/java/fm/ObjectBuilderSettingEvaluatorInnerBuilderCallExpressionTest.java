/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package fm;

import freemarker.cache.CacheStorage;
import freemarker.cache.StrongCacheStorage;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.OutputFormat;
import freemarker.core._ObjectBuilderSettingEvaluator;
import freemarker.core._SettingEvaluationEnvironment;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Version;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ObjectBuilderSettingEvaluatorInnerBuilderCallExpressionTest {

    @Test
    void evaluatesLegacyZeroArgumentConstructorCall() throws Exception {
        Object result = evaluate("freemarker.cache.StrongCacheStorage", CacheStorage.class);

        Assertions.assertThat(result).isInstanceOf(StrongCacheStorage.class);
    }

    @Test
    void evaluatesModernZeroArgumentConstructorCall() throws Exception {
        Object result = evaluate("freemarker.cache.StrongCacheStorage()", CacheStorage.class);

        Assertions.assertThat(result).isInstanceOf(StrongCacheStorage.class);
    }

    @Test
    void evaluatesPublicInstanceFieldAsPseudoConstructor() throws Exception {
        Object result = evaluate("freemarker.core.HTMLOutputFormat()", OutputFormat.class);

        Assertions.assertThat(result).isSameAs(HTMLOutputFormat.INSTANCE);
    }

    @Test
    void evaluatesPublicStaticFieldReference() throws Exception {
        Object result = evaluate(
                "freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS",
                Version.class);

        Assertions.assertThat(result).isSameAs(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
    }

    @Test
    void evaluatesBuilderClassAndInvokesBuildMethod() throws Exception {
        Object result = evaluate(
                "freemarker.template.DefaultObjectWrapper("
                        + "freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)",
                DefaultObjectWrapper.class);

        Assertions.assertThat(result).isInstanceOf(DefaultObjectWrapper.class);
    }

    private Object evaluate(String expression, Class<?> expectedClass) throws Exception {
        return _ObjectBuilderSettingEvaluator.eval(
                expression,
                expectedClass,
                false,
                _SettingEvaluationEnvironment.getCurrent());
    }
}
