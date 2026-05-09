/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jasper.compiler.JspUtil;
import org.junit.jupiter.api.Test;

public class JspUtilTest {
    @Test
    void toClassResolvesReferenceTypesAndArrays() throws Exception {
        final ClassLoader classLoader = JspUtilTest.class.getClassLoader();

        assertThat(JspUtil.toClass("java.lang.String", classLoader)).isSameAs(String.class);
        assertThat(JspUtil.toClass("java.lang.String[]", classLoader)).isSameAs(String[].class);
        assertThat(JspUtil.toClass("java.lang.String[][]", classLoader)).isSameAs(String[][].class);
    }

    @Test
    void interpreterCallBuildsPrimitiveBooleanEvaluationExpression() {
        final String call = JspUtil.interpreterCall(
                false,
                "${enabled}",
                Boolean.TYPE,
                "null",
                true);

        assertThat(call)
                .contains("java.lang.Boolean.class")
                .contains("false)")
                .endsWith(").booleanValue()");
    }
}
